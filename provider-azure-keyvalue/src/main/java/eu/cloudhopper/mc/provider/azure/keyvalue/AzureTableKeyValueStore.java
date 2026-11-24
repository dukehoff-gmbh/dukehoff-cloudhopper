package eu.cloudhopper.mc.provider.azure.keyvalue;


/*-
 * #%L
 * provider-azure-keyvalue - a library from the "Cloudhopper" project.
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A table of a storage account, through the Table service REST API signed with the account's
 * SharedKey. The partition key is the entity's {@code PartitionKey}, the sort key its
 * {@code RowKey}. Strings, booleans, {@code Long} ({@code Edm.Int64}) and {@code Double}
 * ({@code Edm.Double}) are properties of the attribute's name; Table Storage has no lists and
 * maps, so a list or map is stored as JSON in the property {@code _<name>}, a name no attribute
 * has.
 *
 * <p>Conditional updates read the entity, compare the expected values and replace it with the
 * entity's ETag as condition, so a concurrent change makes the write fail and the comparison is
 * repeated. Batches are written item by item. Throttling, server errors and I/O errors are
 * retried with backoff; HTTP 403 becomes a {@link KeyValueAccessDeniedException}.</p>
 */
class AzureTableKeyValueStore implements KeyValueStore {

    static final String VERSION = "2019-02-02";
    private static final int ATTEMPTS = 5;
    private static final String JSON = "application/json";
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final StorageAccount account;
    private final String endpoint;
    /** The path of the endpoint, e.g. {@code /devstoreaccount1} for Azurite, else empty. */
    private final String basePath;
    private final String table;
    private final Clock clock;
    private final Duration backoff;

    /**
     * @param endpoint the Table service endpoint, e.g. {@code https://acct.table.core.windows.net}
     */
    AzureTableKeyValueStore(ObjectMapper mapper, HttpClient http, StorageAccount account, URI endpoint, String table,
            Clock clock, Duration backoff) {
        this.mapper = mapper;
        this.http = http;
        this.account = account;
        this.endpoint = endpoint.toString().replaceAll("/$", "");
        String path = endpoint.getRawPath();
        this.basePath = path == null ? "" : path.replaceAll("/$", "");
        this.table = table;
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public Optional<Item> get(Key key) {
        JsonNode entity = read(key);
        return entity == null ? Optional.empty() : Optional.of(new Item(key, attributes(entity)));
    }

    @Override
    public void put(Item item) {
        call("PUT", entity(item.key()), "", Map.of(), properties(item.attributes()), "put " + item.key());
    }

    @Override
    public boolean putIfAbsent(Item item) {
        ObjectNode body = properties(item.attributes());
        body.put("PartitionKey", item.key().partition());
        body.put("RowKey", item.key().sort());
        Response response = call("POST", "/" + table, "", Map.of("Prefer", "return-no-content"), body,
                "putIfAbsent " + item.key());
        return !"EntityAlreadyExists".equals(response.code);
    }

    @Override
    public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            JsonNode entity = read(key);
            if (entity == null) {
                return false;
            }
            Map<String, Object> attributes = attributes(entity);
            for (Map.Entry<String, Object> e : expected.entrySet()) {
                if (!Objects.equals(attributes.get(e.getKey()), e.getValue())) {
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
            Response response = call("PUT", entity(key), "", Map.of("If-Match", entity.path("odata.etag").asText("*")),
                    properties(attributes), "update " + key);
            if (response.code == null) {
                return true;
            }
            if ("ResourceNotFound".equals(response.code)) {
                return false;
            }
            // UpdateConditionNotSatisfied: changed since it was read, compare again
        }
        throw new KeyValueStoreException("update " + key + " in table " + table + " failed: the item kept changing");
    }

    @Override
    public void delete(Key key) {
        call("DELETE", entity(key), "", Map.of("If-Match", "*"), null, "delete " + key);
    }

    @Override
    public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
        StringBuilder filter = new StringBuilder("PartitionKey eq ").append(literal(partition));
        if (!sortKeyPrefix.isEmpty()) {
            filter.append(" and RowKey ge ").append(literal(sortKeyPrefix));
            String upper = upperBound(sortKeyPrefix);
            if (upper != null) {
                filter.append(" and RowKey lt ").append(literal(upper));
            }
        }
        StringBuilder query = new StringBuilder("?$filter=").append(encode(filter.toString())).append("&$top=").append(limit);
        if (pageToken != null) {
            String[] next;
            try {
                next = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8).split("\n", -1);
            } catch (IllegalArgumentException e) {
                throw new KeyValueStoreException("Invalid page token for table " + table, e);
            }
            if (next.length != 2) {
                throw new KeyValueStoreException("Invalid page token for table " + table);
            }
            query.append("&NextPartitionKey=").append(encode(next[0])).append("&NextRowKey=").append(encode(next[1]));
        }
        Response response = call("GET", "/" + table + "()", query.toString(), Map.of(), null, "query " + partition);
        List<Item> items = new ArrayList<>();
        for (JsonNode entity : response.body.path("value")) {
            Key key = new Key(entity.path("PartitionKey").asText(), entity.path("RowKey").asText());
            if (key.sort().startsWith(sortKeyPrefix)) {
                items.add(new Item(key, attributes(entity)));
            }
        }
        String nextPartition = response.headers.firstValue("x-ms-continuation-NextPartitionKey").orElse(null);
        String nextRow = response.headers.firstValue("x-ms-continuation-NextRowKey").orElse(null);
        String next = nextPartition == null || nextRow == null ? null : Base64.getUrlEncoder().withoutPadding()
                .encodeToString((nextPartition + "\n" + nextRow).getBytes(StandardCharsets.UTF_8));
        return new Page(items, next);
    }

    @Override
    public void batchPut(Collection<Item> items) {
        items.forEach(this::put);
    }

    @Override
    public void batchDelete(Collection<Key> keys) {
        keys.forEach(this::delete);
    }

    /** The entity of an item, or null if there is none. */
    private JsonNode read(Key key) {
        Response response = call("GET", entity(key), "", Map.of(), null, "get " + key);
        return response.code != null ? null : response.body;
    }

    /** The encoded path of an entity. */
    private String entity(Key key) {
        return "/" + table + "(PartitionKey='" + encode(key.partition().replace("'", "''")) + "',RowKey='"
                + encode(key.sort().replace("'", "''")) + "')";
    }

    /** A string literal of a filter. */
    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** The smallest string greater than all strings with the prefix, or null if there is none. */
    static String upperBound(String prefix) {
        for (int i = prefix.length() - 1; i >= 0; i--) {
            char c = prefix.charAt(i);
            if (c != Character.MAX_VALUE) {
                return prefix.substring(0, i) + (char) (c + 1);
            }
        }
        return null;
    }

    /** The entity's properties of the attributes. */
    private ObjectNode properties(Map<String, Object> attributes) {
        ObjectNode entity = mapper.createObjectNode();
        attributes.forEach((name, value) -> {
            if (value instanceof String s) {
                entity.put(name, s);
            } else if (value instanceof Boolean b) {
                entity.put(name, b);
            } else if (value instanceof Long l) {
                entity.put(name, l.toString());
                entity.put(name + "@odata.type", "Edm.Int64");
            } else if (value instanceof Double d) {
                if (d.isNaN() || d.isInfinite()) {
                    entity.put(name, d.toString());
                } else {
                    entity.put(name, d);
                }
                entity.put(name + "@odata.type", "Edm.Double");
            } else if (value instanceof List<?> || value instanceof Map<?, ?>) {
                try {
                    entity.put("_" + name, mapper.writeValueAsString(value));
                } catch (JsonProcessingException e) {
                    throw new KeyValueStoreException("Cannot store attribute " + name + " in table " + table, e);
                }
            } else {
                throw new KeyValueStoreException("Cannot store " + value + " in table " + table);
            }
        });
        return entity;
    }

    /** The attributes of an entity; numbers as Long when they are whole, else Double. */
    private Map<String, Object> attributes(JsonNode entity) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = entity.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            if (name.contains("@") || name.startsWith("odata.") || "PartitionKey".equals(name) || "RowKey".equals(name)
                    || "Timestamp".equals(name)) {
                continue;
            }
            String type = entity.path(name + "@odata.type").asText("");
            JsonNode value = field.getValue();
            if (name.startsWith("_")) {
                try {
                    attributes.put(name.substring(1), json(mapper.readTree(value.asText())));
                } catch (IOException e) {
                    throw new KeyValueStoreException("Table " + table + " holds a property " + name + " that is not JSON", e);
                }
            } else if ("Edm.Int64".equals(type) || "Edm.Int32".equals(type)) {
                attributes.put(name, Long.parseLong(value.asText()));
            } else if ("Edm.Double".equals(type)) {
                attributes.put(name, number(Double.parseDouble(value.asText())));
            } else if (type.isEmpty() && value.isTextual()) {
                attributes.put(name, value.asText());
            } else if (type.isEmpty() && value.isBoolean()) {
                attributes.put(name, value.asBoolean());
            } else if (type.isEmpty() && value.isIntegralNumber()) {
                attributes.put(name, value.asLong());
            } else if (type.isEmpty() && value.isNumber()) {
                attributes.put(name, number(value.asDouble()));
            } else {
                throw new KeyValueStoreException("Table " + table + " holds a property " + name + " of type " + type
                        + " that KeyValueStore does not read");
            }
        }
        return attributes;
    }

    /** A JSON value as a value. */
    private Object json(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return number(node.asDouble());
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(element -> list.add(json(element)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> map.put(field.getKey(), json(field.getValue())));
            return map;
        }
        return null;
    }

    private static Object number(double d) {
        return d == Math.rint(d) && Math.abs(d) < 0x1p63 ? (Object) (long) d : (Object) d;
    }

    /** A response: the body, or for an expected failure its error code. */
    private record Response(JsonNode body, java.net.http.HttpHeaders headers, String code) {
    }

    /**
     * One request, retried on throttling, server errors and I/O errors. A missing entity, an
     * existing entity on insert and a failed ETag condition are returned with their error code;
     * anything else is an exception.
     */
    private Response call(String method, String path, String query, Map<String, String> headers, ObjectNode body,
            String what) {
        byte[] content;
        try {
            content = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new KeyValueStoreException("Cannot write the request for table " + table, e);
        }
        String contentType = body == null ? "" : JSON;
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String date = RFC_1123.format(clock.instant());
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + path + query))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", account.tableSharedKey(method, contentType, date, basePath + path))
                    .header("x-ms-date", date)
                    .header("x-ms-version", VERSION)
                    .header("Accept", "application/json;odata=minimalmetadata")
                    .header("DataServiceVersion", "3.0;NetFx")
                    .header("MaxDataServiceVersion", "3.0;NetFx")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(content));
            headers.forEach(builder::header);
            if (body != null) {
                builder.header("Content-Type", contentType);
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    String text = response.body();
                    return new Response(text == null || text.isBlank() ? mapper.createObjectNode() : mapper.readTree(text),
                            response.headers(), null);
                }
                String code = response.headers().firstValue("x-ms-error-code").orElse(errorCode(response.body()));
                lastError = status + " " + code;
                if ("TableNotFound".equals(code)) {
                    throw new KeyValueStoreException("Table " + table + " does not exist in storage account "
                            + account.name());
                }
                if ("ResourceNotFound".equals(code) && "DELETE".equals(method)) {
                    return new Response(mapper.createObjectNode(), response.headers(), null);
                }
                if ("ResourceNotFound".equals(code) || "EntityAlreadyExists".equals(code)
                        || "UpdateConditionNotSatisfied".equals(code)) {
                    return new Response(null, response.headers(), code);
                }
                if (status == 403) {
                    throw new KeyValueAccessDeniedException("Table Storage refused " + what + " in table " + table + ": "
                            + lastError);
                }
                if (status < 500 && status != 408 && status != 429) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KeyValueStoreException("Interrupted during " + what + " in table " + table, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, what);
            }
        }
        throw new KeyValueStoreException("Table Storage " + what + " in table " + table + " of storage account "
                + account.name() + " failed: " + lastError);
    }

    /** The code of an error body {@code {"odata.error": {"code": ...}}}. */
    private String errorCode(String body) {
        try {
            return mapper.readTree(body).path("odata.error").path("code").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** RFC 3986 encoding of everything but unreserved characters. */
    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
            }
        }
        return out.toString();
    }

    private void pause(int attempt, String what) {
        try {
            Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyValueStoreException("Interrupted during " + what + " in table " + table, e);
        }
    }
}
