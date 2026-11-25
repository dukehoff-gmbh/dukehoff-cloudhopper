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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The JSON mapping always; the operations against a PostgreSQL database if
 * {@code CLOUDHOPPER_TEST_JDBC_URL} (and {@code CLOUDHOPPER_TEST_JDBC_USERNAME},
 * {@code CLOUDHOPPER_TEST_JDBC_PASSWORD}) name one, e.g. {@code docker run -p 5432:5432
 * -e POSTGRES_PASSWORD=pw postgres:17-alpine}.
 */
class JdbcKeyValueStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TABLE = "kvtest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static HikariDataSource dataSource;

    @BeforeAll
    static void connect() {
        String url = System.getenv("CLOUDHOPPER_TEST_JDBC_URL");
        if (url != null && !url.isBlank()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(System.getenv("CLOUDHOPPER_TEST_JDBC_USERNAME"));
            config.setPassword(System.getenv("CLOUDHOPPER_TEST_JDBC_PASSWORD"));
            config.setMaximumPoolSize(2);
            dataSource = new HikariDataSource(config);
        }
    }

    @AfterAll
    static void dropTable() throws Exception {
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS \"" + TABLE + "\"");
            }
            dataSource.close();
        }
    }

    private static JdbcKeyValueStore store() {
        Assumptions.assumeTrue(dataSource != null, "no database: set CLOUDHOPPER_TEST_JDBC_URL");
        return new JdbcKeyValueStore(MAPPER, dataSource, TABLE, false, Duration.ofMillis(10));
    }

    private static Map<String, Object> attributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "Ada");
        attributes.put("count", 1L);
        attributes.put("ratio", 0.5);
        attributes.put("big", 1e300);
        attributes.put("active", true);
        attributes.put("tags", List.of("a", List.of(1L, 2.5)));
        attributes.put("meta", Map.of("k", "v", "n", 2L, "__x__", Map.of()));
        return attributes;
    }

    @Test
    void attributesKeepTheirTypes() {
        JdbcKeyValueStore store = new JdbcKeyValueStore(MAPPER, null, "t", true, Duration.ZERO);
        Map<String, Object> read = store.attributes(store.json(attributes()));
        assertEquals(attributes(), read);
        assertEquals(attributes().get("tags"), read.get("tags"));
        assertEquals(attributes().get("meta"), read.get("meta"));
        assertEquals(1L, read.get("count"));
        assertEquals(0.5, read.get("ratio"));
        assertEquals(1e300, read.get("big"));
    }

    @Test
    void invalidTableNamesAreRefused() {
        assertThrows(KeyValueStoreException.class, () -> new JdbcKeyValueStore(MAPPER, null, "a\"b", true, Duration.ZERO));
        assertThrows(KeyValueStoreException.class, () -> new JdbcKeyValueStore(MAPPER, null, "x".repeat(64), true, Duration.ZERO));
    }

    @Test
    void writesAndReads() {
        JdbcKeyValueStore store = store();
        Key key = new Key("acct:" + UUID.randomUUID(), "a");
        assertTrue(store.get(key).isEmpty());
        store.put(new Item(key, attributes()));
        assertEquals(new Item(key, attributes()), store.get(key).orElseThrow());
        assertFalse(store.putIfAbsent(new Item(key, Map.of("name", "Eve"))));
        Key other = new Key(key.partition(), "b");
        assertTrue(store.putIfAbsent(new Item(other, Map.of("name", "Eve"))));
        store.delete(other);
        store.delete(other);
        assertTrue(store.get(other).isEmpty());
    }

    @Test
    void updatesConditionally() {
        JdbcKeyValueStore store = store();
        Key key = new Key("upd:" + UUID.randomUUID(), "a");
        assertFalse(store.update(key, Map.of("count", 2L), Map.of()));
        store.put(new Item(key, Map.of("count", 1L, "name", "Ada")));
        assertFalse(store.update(key, Map.of("count", 2L), Map.of("count", 5L)));
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("count", 2L);
        set.put("name", null);
        assertTrue(store.update(key, set, Map.of("count", 1L)));
        Map<String, Object> absent = new HashMap<>();
        absent.put("name", null);
        assertTrue(store.update(key, Map.of("flag", true), absent));
        assertEquals(Map.of("count", 2L, "flag", true), store.get(key).orElseThrow().attributes());
    }

    @Test
    void queriesByPrefixInPages() {
        JdbcKeyValueStore store = store();
        String partition = "q:" + UUID.randomUUID();
        List<Item> items = new ArrayList<>();
        for (String sort : List.of("order:1", "order:2", "order:3", "order:Z", "order:a", "order:é", "other", "order")) {
            items.add(new Item(new Key(partition, sort), Map.of("s", sort)));
        }
        store.batchPut(items);
        Page first = store.query(partition, "order:", 2, null);
        assertEquals(List.of("order:1", "order:2"), first.items().stream().map(i -> i.key().sort()).toList());
        assertNotNull(first.nextPageToken());
        List<String> all = store.query(partition, "order:").stream().map(i -> i.key().sort()).toList();
        assertEquals(List.of("order:1", "order:2", "order:3", "order:Z", "order:a", "order:é"), all);
        Page last = store.query(partition, "order:", 6, null);
        assertNull(last.nextPageToken());
        assertEquals(8, store.query(partition, "").size());
        store.batchDelete(items.stream().map(Item::key).toList());
        assertTrue(store.query(partition, "").isEmpty());
    }

    @Test
    void missingExistingTableFails() {
        store();
        JdbcKeyValueStore missing = new JdbcKeyValueStore(MAPPER, dataSource, TABLE + "_missing", true, Duration.ZERO);
        assertThrows(KeyValueStoreException.class, () -> missing.get(new Key("p", "s")));
    }
}
