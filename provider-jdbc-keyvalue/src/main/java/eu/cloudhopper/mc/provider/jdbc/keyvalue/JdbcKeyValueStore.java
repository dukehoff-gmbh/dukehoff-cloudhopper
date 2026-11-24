package eu.cloudhopper.mc.provider.jdbc.keyvalue;


/*-
 * #%L
 * provider-jdbc-keyvalue - a library from the "Cloudhopper" project.
 *
 * Dukehoff GmbH designates this particular file as subject to the "Classpath"
 * exception as provided in the LICENSE-EXCEPTIONS file that accompanies this code.
 * %%
 * Copyright (C) 2024 - 2026 Dukehoff GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * A table of a PostgreSQL database with the columns {@code partition_key}, {@code sort_key}
 * (both {@code text} with the collation {@code "C"}, so that the sort keys are ordered by their
 * code points on every database) and {@code attributes} ({@code jsonb}), whose primary key is
 * the partition and the sort key. Unless the table is {@code existing}, the store creates it
 * before its first operation.
 *
 * <p>Conditional updates lock the row ({@code SELECT ... FOR UPDATE}), compare the expected
 * values and write in the same transaction. Batches are written in chunks of 500 statements.
 * Lost connections, serialization failures and deadlocks are retried with backoff; the
 * SQLSTATE 42501 (insufficient privilege) becomes a {@link KeyValueAccessDeniedException}.</p>
 */
class JdbcKeyValueStore implements KeyValueStore {

    private static final int ATTEMPTS = 4;
    private static final int CHUNK = 500;

    private final ObjectMapper mapper;
    private final DataSource dataSource;
    private final String table;
    /** The table as an SQL identifier, quoted. */
    private final String sqlTable;
    private final Duration backoff;
    private volatile boolean created;

    /**
     * @param table    the table's name in the database
     * @param existing whether the table exists; otherwise the store creates it if it is missing
     */
    JdbcKeyValueStore(ObjectMapper mapper, DataSource dataSource, String table, boolean existing, Duration backoff) {
        if (table.isEmpty() || table.indexOf('"') >= 0 || table.indexOf('\0') >= 0
                || table.getBytes(StandardCharsets.UTF_8).length > 63) {
            throw new KeyValueStoreException("Invalid table name \"" + table
                    + "\": PostgreSQL allows up to 63 bytes without double quotes");
        }
        this.mapper = mapper;
        this.dataSource = dataSource;
        this.table = table;
        this.sqlTable = "\"" + table + "\"";
        this.backoff = backoff;
        this.created = existing;
    }

    @Override
    public Optional<Item> get(Key key) {
        return run("get " + key, connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT attributes FROM " + sqlTable + " WHERE partition_key = ? AND sort_key = ?")) {
                select.setString(1, key.partition());
                select.setString(2, key.sort());
                try (ResultSet rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(new Item(key, attributes(rows.getString(1)))) : Optional.<Item>empty();
                }
            }
        });
    }

    @Override
    public void put(Item item) {
        run("put " + item.key(), connection -> {
            try (PreparedStatement upsert = connection.prepareStatement(upsert())) {
                bind(upsert, item);
                upsert.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean putIfAbsent(Item item) {
        return run("putIfAbsent " + item.key(), connection -> {
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + sqlTable
                    + " (partition_key, sort_key, attributes) VALUES (?, ?, ?::jsonb) ON CONFLICT DO NOTHING")) {
                bind(insert, item);
                return insert.executeUpdate() == 1;
            }
        });
    }

    @Override
    public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
        return run("update " + key, connection -> {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> attributes;
                try (PreparedStatement select = connection.prepareStatement("SELECT attributes FROM " + sqlTable
                        + " WHERE partition_key = ? AND sort_key = ? FOR UPDATE")) {
                    select.setString(1, key.partition());
                    select.setString(2, key.sort());
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            connection.rollback();
                            return false;
                        }
                        attributes = attributes(rows.getString(1));
                    }
                }
                for (Map.Entry<String, Object> e : expected.entrySet()) {
                    if (!Objects.equals(attributes.get(e.getKey()), e.getValue())) {
                        connection.rollback();
                        return false;
                    }
                }
                set.forEach((name, value) -> {
                    if (value == null) {
                        attributes.remove(name);
                    } else {
                        attributes.put(name, value);
                    }
                });
                try (PreparedStatement write = connection.prepareStatement("UPDATE " + sqlTable
                        + " SET attributes = ?::jsonb WHERE partition_key = ? AND sort_key = ?")) {
                    write.setString(1, json(attributes));
                    write.setString(2, key.partition());
                    write.setString(3, key.sort());
                    write.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    @Override
    public void delete(Key key) {
        run("delete " + key, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(delete())) {
                delete.setString(1, key.partition());
                delete.setString(2, key.sort());
                delete.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
        String after = null;
        if (pageToken != null) {
            try {
                after = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new KeyValueStoreException("Invalid page token for table " + table, e);
            }
        }
        String start = after;
        return run("query " + partition, connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT sort_key, attributes FROM " + sqlTable
                    + " WHERE partition_key = ? AND sort_key " + (start == null ? ">=" : ">") + " ?"
                    + " AND starts_with(sort_key, ?) ORDER BY sort_key LIMIT ?")) {
                select.setString(1, partition);
                select.setString(2, start == null ? sortKeyPrefix : start);
                select.setString(3, sortKeyPrefix);
                select.setInt(4, limit + 1);
                List<Item> items = new ArrayList<>();
                boolean more = false;
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        if (items.size() == limit) {
                            more = true;
                            break;
                        }
                        items.add(new Item(new Key(partition, rows.getString(1)), attributes(rows.getString(2))));
                    }
                }
                String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                        items.get(items.size() - 1).key().sort().getBytes(StandardCharsets.UTF_8)) : null;
                return new Page(items, next);
            }
        });
    }

    @Override
    public void batchPut(Collection<Item> items) {
        Iterator<Item> remaining = items.iterator();
        while (remaining.hasNext()) {
            List<Item> chunk = new ArrayList<>();
            while (remaining.hasNext() && chunk.size() < CHUNK) {
                chunk.add(remaining.next());
            }
            run("batchPut", connection -> {
                try (PreparedStatement upsert = connection.prepareStatement(upsert())) {
                    for (Item item : chunk) {
                        bind(upsert, item);
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }
                return null;
            });
        }
    }

    @Override
    public void batchDelete(Collection<Key> keys) {
        Iterator<Key> remaining = keys.iterator();
        while (remaining.hasNext()) {
            List<Key> chunk = new ArrayList<>();
            while (remaining.hasNext() && chunk.size() < CHUNK) {
                chunk.add(remaining.next());
            }
            run("batchDelete", connection -> {
                try (PreparedStatement delete = connection.prepareStatement(delete())) {
                    for (Key key : chunk) {
                        delete.setString(1, key.partition());
                        delete.setString(2, key.sort());
                        delete.addBatch();
                    }
                    delete.executeBatch();
                }
                return null;
            });
        }
    }

    private String upsert() {
        return "INSERT INTO " + sqlTable + " (partition_key, sort_key, attributes) VALUES (?, ?, ?::jsonb)"
                + " ON CONFLICT (partition_key, sort_key) DO UPDATE SET attributes = EXCLUDED.attributes";
    }

    private String delete() {
        return "DELETE FROM " + sqlTable + " WHERE partition_key = ? AND sort_key = ?";
    }

    private void bind(PreparedStatement statement, Item item) throws SQLException {
        statement.setString(1, item.key().partition());
        statement.setString(2, item.key().sort());
        statement.setString(3, json(item.attributes()));
    }

    /** Creates the table unless it exists; concurrent creations by other instances are expected. */
    private void create(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + sqlTable + " ("
                    + "partition_key text COLLATE \"C\" NOT NULL, "
                    + "sort_key text COLLATE \"C\" NOT NULL, "
                    + "attributes jsonb NOT NULL, "
                    + "PRIMARY KEY (partition_key, sort_key))");
        } catch (SQLException e) {
            // 23505/42P07: another instance created it at the same time
            if (!"23505".equals(e.getSQLState()) && !"42P07".equals(e.getSQLState())) {
                throw e;
            }
        }
        created = true;
    }

    String json(Map<String, Object> attributes) {
        try {
            return mapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new KeyValueStoreException("Cannot write the attributes as JSON for table " + table, e);
        }
    }

    Map<String, Object> attributes(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) value(node);
            return attributes;
        } catch (JsonProcessingException | ClassCastException e) {
            throw new KeyValueStoreException("Table " + table + " holds attributes that are no JSON object", e);
        }
    }

    /** A JSON value as an attribute value; whole numbers are {@code Long}, others {@code Double}. */
    private static Object value(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            double d = node.asDouble();
            return d == Math.rint(d) && Math.abs(d) < 0x1p63 ? (Object) (long) d : (Object) d;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(element -> list.add(value(element)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> map.put(field.getKey(), value(field.getValue())));
            return map;
        }
        return null;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Runs work on a connection of the pool, creating the table first and retrying transient failures. */
    private <T> T run(String what, Work<T> work) {
        for (int attempt = 1; ; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                if (!created) {
                    create(connection);
                }
                return work.run(connection);
            } catch (SQLException e) {
                String state = e.getSQLState() == null ? "" : e.getSQLState();
                if ("42501".equals(state)) {
                    throw new KeyValueAccessDeniedException(what + " in table " + table + " was refused: " + e.getMessage());
                }
                if ("42P01".equals(state)) {
                    throw new KeyValueStoreException("Table " + table + " does not exist", e);
                }
                boolean transientFailure = state.startsWith("08") || state.equals("40001") || state.equals("40P01")
                        || state.equals("57P01") || state.equals("53300");
                if (!transientFailure || attempt >= ATTEMPTS) {
                    throw new KeyValueStoreException(what + " in table " + table + " failed: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(backoff.toMillis() << (attempt - 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new KeyValueStoreException(what + " in table " + table + " was interrupted", e);
                }
            }
        }
    }
}
