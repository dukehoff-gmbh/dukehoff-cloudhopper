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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FirestoreKeyValueStoreTest {

    private static final String DOCS = "projects/p/databases/db/documents";

    /** The paths and bodies the store sent. */
    private final List<String> paths = new ArrayList<>();
    private final List<JsonObject> bodies = new ArrayList<>();
    /** The responses to send: status and body. */
    private final Deque<Object[]> responses = new ArrayDeque<>();
    private HttpServer server;
    private FirestoreKeyValueStore store;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body.isEmpty() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject());
            assertEquals("Bearer token", exchange.getRequestHeaders().getFirst("Authorization"));
            Object[] response = responses.isEmpty() ? new Object[]{200, "{}"} : responses.poll();
            byte[] bytes = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders((int) response[0], bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        store = new FirestoreKeyValueStore(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/"), "p", "db", () -> "token",
                Duration.ofMillis(1));
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        responses.add(new Object[]{status, body});
    }

    @Test
    public void valuesRoundTripThroughFirestoreValues() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("s", "x");
        attributes.put("n", 42L);
        attributes.put("d", 1.5);
        attributes.put("b", true);
        attributes.put("l", List.of(1L, List.of("nested")));
        attributes.put("m", Map.of("~k", "v"));
        JsonObject fields = new JsonObject();
        attributes.forEach((name, value) -> fields.add(name, store.value(value, false)));
        assertEquals("42", fields.getAsJsonObject("n").get("integerValue").getAsString());
        assertTrue(fields.getAsJsonObject("l").getAsJsonObject("arrayValue").getAsJsonArray("values").get(1)
                .getAsJsonObject().getAsJsonObject("mapValue").getAsJsonObject("fields").has(FirestoreKeyValueStore.LIST));
        assertTrue(fields.getAsJsonObject("m").getAsJsonObject("mapValue").getAsJsonObject("fields").has("~~k"));
        Map<String, Object> back = new LinkedHashMap<>();
        fields.entrySet().forEach(e -> back.put(e.getKey(), store.value(e.getValue().getAsJsonObject())));
        assertEquals(attributes, back);
        JsonObject whole = new JsonObject();
        whole.addProperty("doubleValue", 3.0);
        assertEquals(3L, store.value(whole));
    }

    @Test
    public void partitionIdsAvoidColonsAndKeepTheKey() {
        assertEquals("kacct~3a42", FirestoreKeyValueStore.partitionId("acct:42"));
        assertEquals("kp~c3~a9~7e", FirestoreKeyValueStore.partitionId("pé~"));
        assertEquals(new Key("pé~:x", "a:b"),
                FirestoreKeyValueStore.key(DOCS + "/partitions/" + FirestoreKeyValueStore.partitionId("pé~:x") + "/items/ka:b"));
    }

    @Test
    public void getReadsTheDocumentWithBatchGet() {
        respond(200, "[{\"missing\":\"" + DOCS + "/partitions/ka/items/kb\"}]");
        assertTrue(store.get(new Key("a", "b")).isEmpty());
        respond(200, "[{\"found\":{\"name\":\"" + DOCS + "/partitions/ka/items/kb\",\"fields\":{\"x\":{\"stringValue\":\"y\"}},"
                + "\"updateTime\":\"t\"}}]");
        assertEquals(Map.of("x", "y"), store.get(new Key("a", "b")).orElseThrow().attributes());
        assertEquals("/v1/" + DOCS + ":batchGet", paths.get(0));
        assertEquals(DOCS + "/partitions/ka/items/kb", bodies.get(0).getAsJsonArray("documents").get(0).getAsString());
    }

    @Test
    public void putIfAbsentIsFalseWhenTheDocumentExists() {
        respond(409, "{\"error\":{\"code\":409,\"message\":\"exists\",\"status\":\"ALREADY_EXISTS\"}}");
        assertFalse(store.putIfAbsent(new Item(new Key("a", "b"), Map.of("x", 1L))));
        JsonObject write = bodies.get(0).getAsJsonArray("writes").get(0).getAsJsonObject();
        assertFalse(write.getAsJsonObject("currentDocument").get("exists").getAsBoolean());
    }

    @Test
    public void updateComparesAndRetriesWhenTheDocumentChanged() {
        String doc = "[{\"found\":{\"name\":\"" + DOCS + "/partitions/ka/items/kb\",\"fields\":{\"v\":{\"integerValue\":\"1\"},"
                + "\"old\":{\"stringValue\":\"o\"}},\"updateTime\":\"t%d\"}}]";
        respond(200, String.format(doc, 1));
        respond(400, "{\"error\":{\"code\":400,\"message\":\"version\",\"status\":\"FAILED_PRECONDITION\"}}");
        respond(200, String.format(doc, 2));
        respond(200, "{\"writeResults\":[{}]}");
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("v", 2L);
        set.put("old", null);
        assertTrue(store.update(new Key("a", "b"), set, Map.of("v", 1L)));
        JsonObject write = bodies.get(3).getAsJsonArray("writes").get(0).getAsJsonObject();
        assertEquals("t2", write.getAsJsonObject("currentDocument").get("updateTime").getAsString());
        JsonArray mask = write.getAsJsonObject("updateMask").getAsJsonArray("fieldPaths");
        assertEquals(2, mask.size());
        assertFalse(write.getAsJsonObject("update").getAsJsonObject("fields").has("old"));

        respond(200, String.format(doc, 3));
        assertFalse(store.update(new Key("a", "b"), Map.of("v", 3L), Map.of("v", 5L)));
    }

    @Test
    public void queryPagesWithTheLastSortKey() {
        StringBuilder page = new StringBuilder("[");
        for (int i = 0; i < 3; i++) {
            page.append(i == 0 ? "" : ",").append("{\"document\":{\"name\":\"").append(DOCS)
                    .append("/partitions/ka/items/kp:").append(i).append("\",\"fields\":{}}}");
        }
        respond(200, page.append("]").toString());
        Page first = store.query("a", "p:", 2, null);
        assertEquals(2, first.items().size());
        assertEquals("/v1/" + DOCS + "/partitions/ka:runQuery", paths.get(0));
        JsonObject query = bodies.get(0).getAsJsonObject("structuredQuery");
        assertEquals(3, query.get("limit").getAsInt());
        respond(200, "[{\"document\":{\"name\":\"" + DOCS + "/partitions/ka/items/kp:2\",\"fields\":{}}},{\"readTime\":\"t\"}]");
        Page second = store.query("a", "p:", 2, first.nextPageToken());
        assertEquals(List.of(new Item(new Key("a", "p:2"), Map.of())), second.items());
        assertNull(second.nextPageToken());
        assertEquals(DOCS + "/partitions/ka/items/kp:1", bodies.get(1).getAsJsonObject("structuredQuery")
                .getAsJsonObject("startAt").getAsJsonArray("values").get(0).getAsJsonObject().get("referenceValue").getAsString());
    }

    @Test
    public void retriesServerErrorsAndRefusesForbiddenCalls() {
        respond(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}");
        respond(200, "{}");
        store.delete(new Key("a", "b"));
        assertEquals(2, paths.size());
        respond(403, "{\"error\":{\"code\":403,\"message\":\"Missing or insufficient permissions.\",\"status\":\"PERMISSION_DENIED\"}}");
        assertThrows(KeyValueAccessDeniedException.class, () -> store.put(new Item(new Key("a", "b"), Map.of())));
    }

    @Test
    public void batchPutCommitsInChunksOf500() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            items.add(new Item(new Key("a", "k" + i), Map.of()));
        }
        store.batchPut(items);
        assertEquals(2, paths.size());
        assertEquals(500, bodies.get(0).getAsJsonArray("writes").size());
        assertEquals(1, bodies.get(1).getAsJsonArray("writes").size());
    }
}
