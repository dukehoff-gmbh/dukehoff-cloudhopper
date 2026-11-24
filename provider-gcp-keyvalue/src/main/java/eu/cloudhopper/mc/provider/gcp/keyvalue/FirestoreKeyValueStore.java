package eu.cloudhopper.mc.provider.gcp.keyvalue;


/*-
 * #%L
 * provider-gcp-keyvalue - a library from the "Cloudhopper" project.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A Firestore database (Native mode), through the Firestore REST API with an access token of the
 * function's service account. An item is the document
 * {@code partitions/k<partition>/items/k<sort>}: the key parts become document IDs with the
 * prefix {@code k}, which keeps their order and avoids the IDs Firestore reserves ({@code .},
 * {@code ..}, {@code __…__}). The partition's path is part of the query URL, where a colon
 * would end the path, so in the partition's ID every byte of the key's UTF-8 form other than
 * letters, digits, {@code -}, {@code _} and {@code .} is written as {@code ~} and two hex digits
 * ({@code acct:42} becomes {@code kacct~3a42}). Its attributes are the document's fields. A list directly inside a
 * list, which Firestore does not store, is wrapped in a map with the single field
 * {@value #LIST}; keys of maps that start with {@code ~} get another {@code ~} in front, so a
 * map of the application never looks like such a wrapper.
 *
 * <p>Conditional updates read the document, compare the expected values and write with the
 * document's update time as precondition, so a concurrent change makes the write fail and the
 * comparison is repeated. Throttling, contention, server errors and I/O errors are retried with
 * backoff; HTTP 401 and 403 become a {@link KeyValueAccessDeniedException}.</p>
 */
class FirestoreKeyValueStore implements KeyValueStore {

    /** The field of a map that wraps a list inside a list. */
    static final String LIST = "~list";

    private static final int ATTEMPTS = 5;
    /** Firestore's limit of writes in one commit. */
    private static final int BATCH = 500;
    private static final String MAX = new String(Character.toChars(Character.MAX_CODE_POINT));
    private static final Set<String> RETRYABLE = Set.of("ABORTED", "UNAVAILABLE", "RESOURCE_EXHAUSTED",
            "DEADLINE_EXCEEDED", "INTERNAL");

    interface TokenSource {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final String api;
    /** {@code projects/<project>/databases/<database>/documents}. */
    private final String documents;
    private final String database;
    private final TokenSource tokens;
    private final Duration backoff;

    /**
     * @param api the API's base URL, ending with "/", e.g. {@code https://firestore.googleapis.com/v1/}
     */
    FirestoreKeyValueStore(HttpClient http, URI api, String project, String database, TokenSource tokens,
            Duration backoff) {
        this.http = http;
        this.api = api.toString();
        this.documents = "projects/" + project + "/databases/" + database + "/documents";
        this.database = database;
        this.tokens = tokens;
        this.backoff = backoff;
    }

    @Override
    public Optional<Item> get(Key key) {
        JsonObject document = read(key);
        return document == null ? Optional.empty() : Optional.of(new Item(key, attributes(document)));
    }

    @Override
    public void put(Item item) {
        write(update(item.key(), item.attributes()), null, "put " + item.key());
    }

    @Override
    public boolean putIfAbsent(Item item) {
        JsonObject precondition = new JsonObject();
        precondition.addProperty("exists", false);
        return write(update(item.key(), item.attributes()), precondition, "putIfAbsent " + item.key());
    }

    @Override
    public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            JsonObject document = read(key);
            if (document == null) {
                return false;
            }
            Map<String, Object> current = attributes(document);
            for (Map.Entry<String, Object> e : expected.entrySet()) {
                if (!Objects.equals(current.get(e.getKey()), e.getValue())) {
                    return false;
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            JsonArray paths = new JsonArray();
            for (Map.Entry<String, Object> e : set.entrySet()) {
                paths.add(e.getKey()); // a path without a value removes the field
                if (e.getValue() != null) {
                    values.put(e.getKey(), e.getValue());
                }
            }
            JsonObject write = update(key, values);
            JsonObject mask = new JsonObject();
            mask.add("fieldPaths", paths);
            write.add("updateMask", mask);
            JsonObject precondition = new JsonObject();
            precondition.add("updateTime", document.get("updateTime"));
            if (write(write, precondition, "update " + key)) {
                return true;
            }
            // changed since it was read: compare again
        }
        throw new KeyValueStoreException("update " + key + " in Firestore database " + database
                + " failed: the item kept changing");
    }

    @Override
    public void delete(Key key) {
        JsonObject write = new JsonObject();
        write.addProperty("delete", name(key));
        write(write, null, "delete " + key);
    }

    @Override
    public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
        String parent = documents + "/partitions/" + partitionId(partition);
        JsonObject query = new JsonObject();
        JsonObject from = new JsonObject();
        from.addProperty("collectionId", "items");
        query.add("from", array(from));
        List<JsonElement> filters = new ArrayList<>();
        if (!sortKeyPrefix.isEmpty()) {
            filters.add(nameFilter("GREATER_THAN_OR_EQUAL", parent + "/items/" + id(sortKeyPrefix)));
            filters.add(nameFilter("LESS_THAN", parent + "/items/" + id(sortKeyPrefix) + MAX));
        }
        if (filters.size() == 2) {
            JsonObject composite = new JsonObject();
            composite.addProperty("op", "AND");
            JsonArray list = new JsonArray();
            filters.forEach(list::add);
            composite.add("filters", list);
            JsonObject where = new JsonObject();
            where.add("compositeFilter", composite);
            query.add("where", where);
        }
        JsonObject order = new JsonObject();
        order.add("field", field("__name__"));
        order.addProperty("direction", "ASCENDING");
        query.add("orderBy", array(order));
        query.addProperty("limit", limit + 1);
        if (pageToken != null) {
            String after;
            try {
                after = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new KeyValueStoreException("Invalid page token for Firestore database " + database, e);
            }
            JsonObject cursor = new JsonObject();
            cursor.add("values", array(reference(parent + "/items/" + id(after))));
            cursor.addProperty("before", false);
            query.add("startAt", cursor);
        }
        JsonObject body = new JsonObject();
        body.add("structuredQuery", query);
        JsonElement response = call("POST", url(parent) + ":runQuery", body, "query " + partition);
        List<Item> items = new ArrayList<>();
        boolean more = false;
        for (JsonElement result : response.getAsJsonArray()) {
            JsonObject document = result.getAsJsonObject().getAsJsonObject("document");
            if (document == null) {
                continue;
            }
            if (items.size() == limit) {
                more = true;
                break;
            }
            Key key = key(document.get("name").getAsString());
            if (key.sort().startsWith(sortKeyPrefix)) {
                items.add(new Item(key, attributes(document)));
            }
        }
        String next = more ? Base64.getUrlEncoder().withoutPadding()
                .encodeToString(items.get(items.size() - 1).key().sort().getBytes(StandardCharsets.UTF_8)) : null;
        return new Page(items, next);
    }

    @Override
    public void batchPut(Collection<Item> items) {
        List<JsonObject> writes = new ArrayList<>();
        for (Item item : items) {
            writes.add(update(item.key(), item.attributes()));
        }
        commit(writes, "batchPut");
    }

    @Override
    public void batchDelete(Collection<Key> keys) {
        List<JsonObject> writes = new ArrayList<>();
        for (Key key : keys) {
            JsonObject write = new JsonObject();
            write.addProperty("delete", documents + "/" + path(key));
            writes.add(write);
        }
        commit(writes, "batchDelete");
    }

    /** Commits the writes in chunks of {@value #BATCH}; each chunk is atomic, the whole is not. */
    private void commit(List<JsonObject> writes, String what) {
        for (int start = 0; start < writes.size(); start += BATCH) {
            JsonArray chunk = new JsonArray();
            writes.subList(start, Math.min(start + BATCH, writes.size())).forEach(chunk::add);
            JsonObject body = new JsonObject();
            body.add("writes", chunk);
            call("POST", api + documents + ":commit", body, what);
        }
    }

    /** A write that replaces the document of an item, or with an update mask changes fields of it. */
    private JsonObject update(Key key, Map<String, Object> attributes) {
        JsonObject document = fields(attributes);
        document.addProperty("name", name(key));
        JsonObject write = new JsonObject();
        write.add("update", document);
        return write;
    }

    /** Commits one write: false if its precondition does not hold. */
    private boolean write(JsonObject write, JsonObject precondition, String what) {
        if (precondition != null) {
            write.add("currentDocument", precondition);
        }
        JsonObject body = new JsonObject();
        body.add("writes", array(write));
        return call("POST", api + documents + ":commit", body, what) != null;
    }

    /** The document of an item, or null if there is none. */
    private JsonObject read(Key key) {
        JsonObject body = new JsonObject();
        body.add("documents", array(new JsonPrimitive(name(key))));
        for (JsonElement result : call("POST", api + documents + ":batchGet", body, "get " + key).getAsJsonArray()) {
            JsonObject found = result.getAsJsonObject().getAsJsonObject("found");
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The full name of an item's document. */
    private String name(Key key) {
        return documents + "/" + path(key);
    }

    private static String path(Key key) {
        return "partitions/" + partitionId(key.partition()) + "/items/" + id(key.sort());
    }

    /** The document ID of a sort key. */
    static String id(String sort) {
        return "k" + sort;
    }

    /** The document ID of a partition key. */
    static String partitionId(String partition) {
        StringBuilder id = new StringBuilder("k");
        for (byte b : partition.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                id.append(c);
            } else {
                id.append('~').append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
        }
        return id.toString();
    }

    /** The partition key of a partition's document ID. */
    static String partition(String id) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '~') {
                bytes.write(Integer.parseInt(id.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.write(c);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** The key of a document name {@code …/partitions/k<partition>/items/k<sort>}. */
    static Key key(String name) {
        String[] segments = name.split("/");
        return new Key(partition(segments[segments.length - 3]),
                segments[segments.length - 1].substring(1));
    }

    /** The URL of a resource name, each segment encoded. */
    private String url(String name) {
        StringBuilder url = new StringBuilder(api);
        String[] segments = name.split("/");
        for (int i = 0; i < segments.length; i++) {
            url.append(i == 0 ? "" : "/").append(encode(segments[i]));
        }
        return url.toString();
    }

    private static JsonObject nameFilter(String op, String reference) {
        JsonObject filter = new JsonObject();
        filter.add("field", field("__name__"));
        filter.addProperty("op", op);
        filter.add("value", reference(reference));
        JsonObject wrapper = new JsonObject();
        wrapper.add("fieldFilter", filter);
        return wrapper;
    }

    private static JsonObject field(String path) {
        JsonObject field = new JsonObject();
        field.addProperty("fieldPath", path);
        return field;
    }

    private static JsonObject reference(String name) {
        JsonObject value = new JsonObject();
        value.addProperty("referenceValue", name);
        return value;
    }

    private static JsonArray array(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    /** A document body with the attributes as fields. */
    private JsonObject fields(Map<String, Object> attributes) {
        JsonObject fields = new JsonObject();
        attributes.forEach((name, value) -> fields.add(name, value(value, false)));
        JsonObject document = new JsonObject();
        document.add("fields", fields);
        return document;
    }

    private Map<String, Object> attributes(JsonObject document) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        JsonObject fields = document.getAsJsonObject("fields");
        if (fields != null) {
            for (Map.Entry<String, JsonElement> field : fields.entrySet()) {
                Object value = value(field.getValue().getAsJsonObject());
                if (value != null) {
                    attributes.put(field.getKey(), value);
                }
            }
        }
        return attributes;
    }

    /** A value as a Firestore value; a list in a list is wrapped in a map. */
    JsonObject value(Object value, boolean inList) {
        JsonObject node = new JsonObject();
        if (value instanceof String s) {
            node.addProperty("stringValue", s);
        } else if (value instanceof Boolean b) {
            node.addProperty("booleanValue", b);
        } else if (value instanceof Long l) {
            node.addProperty("integerValue", l.toString());
        } else if (value instanceof Double d) {
            node.add("doubleValue", d.isNaN() || d.isInfinite() ? new JsonPrimitive(d.toString()) : new JsonPrimitive(d));
        } else if (value instanceof List<?> list) {
            JsonArray values = new JsonArray();
            list.forEach(element -> values.add(value(element, true)));
            JsonObject array = new JsonObject();
            array.add("values", values);
            if (inList) {
                JsonObject wrapper = new JsonObject();
                JsonObject arrayValue = new JsonObject();
                arrayValue.add("arrayValue", array);
                wrapper.add(LIST, arrayValue);
                JsonObject map = new JsonObject();
                map.add("fields", wrapper);
                node.add("mapValue", map);
            } else {
                node.add("arrayValue", array);
            }
        } else if (value instanceof Map<?, ?> map) {
            JsonObject fields = new JsonObject();
            map.forEach((k, v) -> fields.add(((String) k).startsWith("~") ? "~" + k : (String) k, value(v, false)));
            JsonObject mapValue = new JsonObject();
            mapValue.add("fields", fields);
            node.add("mapValue", mapValue);
        } else {
            throw new KeyValueStoreException("Cannot store " + value + " in Firestore database " + database);
        }
        return node;
    }

    /** A Firestore value as a value; whole numbers as Long, others as Double; null for null. */
    Object value(JsonObject node) {
        if (node.has("stringValue")) {
            return node.get("stringValue").getAsString();
        }
        if (node.has("booleanValue")) {
            return node.get("booleanValue").getAsBoolean();
        }
        if (node.has("integerValue")) {
            return Long.parseLong(node.get("integerValue").getAsString());
        }
        if (node.has("doubleValue")) {
            double d = node.get("doubleValue").getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < 0x1p63 ? (Object) (long) d : (Object) d;
        }
        if (node.has("nullValue")) {
            return null;
        }
        if (node.has("arrayValue")) {
            List<Object> list = new ArrayList<>();
            JsonArray values = node.getAsJsonObject("arrayValue").getAsJsonArray("values");
            if (values != null) {
                values.forEach(element -> list.add(value(element.getAsJsonObject())));
            }
            return list;
        }
        if (node.has("mapValue")) {
            JsonObject fields = node.getAsJsonObject("mapValue").getAsJsonObject("fields");
            Map<String, Object> map = new LinkedHashMap<>();
            if (fields != null) {
                if (fields.size() == 1 && fields.has(LIST)) {
                    return value(fields.getAsJsonObject(LIST));
                }
                fields.entrySet().forEach(field -> map.put(field.getKey().startsWith("~") ? field.getKey().substring(1)
                        : field.getKey(), value(field.getValue().getAsJsonObject())));
            }
            return map;
        }
        throw new KeyValueStoreException("Firestore database " + database
                + " holds a value of a type KeyValueStore does not read: " + node);
    }

    /**
     * One request, retried on throttling, contention, server errors and I/O errors. Returns the
     * response body, or {@code null} for a failed precondition of a commit;
     * anything else is an exception.
     */
    private JsonElement call(String method, String uri, JsonObject body, String what) {
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + tokens.token())
                        .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
                if (body != null) {
                    builder.header("Content-Type", "application/json");
                }
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body());
                }
                JsonObject error = error(response.body());
                String code = error.has("status") ? error.get("status").getAsString() : "";
                String message = error.has("message") ? error.get("message").getAsString() : response.body();
                lastError = status + " " + code + ": " + message;
                if (uri.endsWith(":commit") && ("ALREADY_EXISTS".equals(code) || "FAILED_PRECONDITION".equals(code))) {
                    return null; // the precondition of a write does not hold
                }
                if (status == 401 || status == 403) {
                    throw new KeyValueAccessDeniedException("Firestore refused " + what + " in database " + database
                            + ": " + lastError);
                }
                if (status < 500 && status != 408 && status != 429 && !RETRYABLE.contains(code)) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KeyValueStoreException("Interrupted during " + what + " in Firestore database " + database, e);
            } catch (RuntimeException e) {
                if (e instanceof KeyValueStoreException k) {
                    throw k;
                }
                throw new KeyValueStoreException("Cannot read the Firestore response to " + what, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, what);
            }
        }
        throw new KeyValueStoreException("Firestore " + what + " in database " + database + " failed: " + lastError);
    }

    /** The error of a response: {@code {"error": {...}}}, or for queries {@code [{"error": {...}}]}. */
    private static JsonObject error(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) {
                root = root.getAsJsonArray().get(0);
            }
            JsonObject error = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("error") : null;
            return error == null ? new JsonObject() : error;
        } catch (RuntimeException e) {
            return new JsonObject();
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
            throw new KeyValueStoreException("Interrupted during " + what + " in Firestore database " + database, e);
        }
    }
}
