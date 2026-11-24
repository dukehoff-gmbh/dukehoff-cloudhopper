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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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

public class AzureTableKeyValueStoreTest {

    private static final StorageAccount ACCOUNT = StorageAccount.parse("AccountName=acct;AccountKey="
            + java.util.Base64.getEncoder().encodeToString(new byte[32]));

    private final ObjectMapper mapper = new ObjectMapper();
    /** The requests the store sent: method, raw path and query, headers, body. */
    private final List<String> requests = new ArrayList<>();
    private final List<Map<String, String>> headers = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    /** The responses to send: status, body and headers. */
    private final Deque<Object[]> responses = new ArrayDeque<>();
    private HttpServer server;
    private AzureTableKeyValueStore store;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            requests.add(exchange.getRequestMethod() + " " + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8)));
            Map<String, String> sent = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> sent.put(name.toLowerCase(), values.get(0)));
            headers.add(sent);
            byte[] body = exchange.getRequestBody().readAllBytes();
            bodies.add(body.length == 0 ? mapper.createObjectNode() : mapper.readTree(body));
            Object[] response = responses.isEmpty() ? new Object[]{204, "", Map.of()} : responses.poll();
            @SuppressWarnings("unchecked")
            Map<String, String> extra = (Map<String, String>) response[2];
            extra.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] bytes = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders((int) response[0], bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        store = new AzureTableKeyValueStore(mapper, HttpClient.newHttpClient(), ACCOUNT,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/acct"), "orders", Clock.systemUTC(),
                Duration.ofMillis(1));
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private void respond(int status, String body, Map<String, String> extra) {
        responses.add(new Object[]{status, body, extra});
    }

    @Test
    public void putWritesTypedPropertiesAndJsonForListsAndMaps() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("s", "x");
        attributes.put("n", 42L);
        attributes.put("d", 1.5);
        attributes.put("b", true);
        attributes.put("l", List.of(1L, "a"));
        store.put(new Item(new Key("p'1", "a:b"), attributes));
        assertEquals("PUT /acct/orders(PartitionKey='p%27%271',RowKey='a%3Ab')", requests.get(0));
        JsonNode body = bodies.get(0);
        assertEquals("42", body.path("n").asText());
        assertEquals("Edm.Int64", body.path("n@odata.type").asText());
        assertEquals("Edm.Double", body.path("d@odata.type").asText());
        assertEquals("[1,\"a\"]", body.path("_l").asText());
        assertTrue(headers.get(0).get("authorization").startsWith("SharedKey acct:"));
        assertFalse(headers.get(0).containsKey("if-match"));
    }

    @Test
    public void getReadsPropertiesBack() {
        respond(404, "", Map.of("x-ms-error-code", "ResourceNotFound"));
        assertTrue(store.get(new Key("p", "s")).isEmpty());
        respond(200, "{\"odata.etag\":\"W/1\",\"PartitionKey\":\"p\",\"RowKey\":\"s\",\"Timestamp\":\"t\","
                + "\"Timestamp@odata.type\":\"Edm.DateTime\",\"s\":\"x\",\"n\":\"42\",\"n@odata.type\":\"Edm.Int64\","
                + "\"i\":7,\"d\":2.0,\"d@odata.type\":\"Edm.Double\",\"b\":false,\"_m\":\"{\\\"k\\\":[1.5,2]}\"}", Map.of());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("s", "x");
        expected.put("n", 42L);
        expected.put("i", 7L);
        expected.put("d", 2L);
        expected.put("b", false);
        expected.put("m", Map.of("k", List.of(1.5, 2L)));
        assertEquals(expected, store.get(new Key("p", "s")).orElseThrow().attributes());
    }

    @Test
    public void putIfAbsentIsFalseWhenTheEntityExists() {
        respond(409, "", Map.of("x-ms-error-code", "EntityAlreadyExists"));
        assertFalse(store.putIfAbsent(new Item(new Key("p", "s"), Map.of())));
        assertEquals("POST /acct/orders", requests.get(0));
        assertEquals("p", bodies.get(0).path("PartitionKey").asText());
        assertTrue(store.putIfAbsent(new Item(new Key("p", "t"), Map.of())));
    }

    @Test
    public void updateReplacesWithTheETagAndRetriesWhenItChanged() {
        String entity = "{\"odata.etag\":\"W/%d\",\"PartitionKey\":\"p\",\"RowKey\":\"s\",\"v\":\"1\",\"v@odata.type\":\"Edm.Int64\","
                + "\"old\":\"o\",\"keep\":true}";
        respond(200, String.format(entity, 1), Map.of());
        respond(412, "", Map.of("x-ms-error-code", "UpdateConditionNotSatisfied"));
        respond(200, String.format(entity, 2), Map.of());
        respond(204, "", Map.of());
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("v", 2L);
        set.put("old", null);
        assertTrue(store.update(new Key("p", "s"), set, Map.of("v", 1L)));
        assertEquals("W/2", headers.get(3).get("if-match"));
        JsonNode body = bodies.get(3);
        assertEquals("2", body.path("v").asText());
        assertTrue(body.path("keep").asBoolean());
        assertTrue(body.path("old").isMissingNode());

        respond(200, String.format(entity, 3), Map.of());
        assertFalse(store.update(new Key("p", "s"), Map.of("v", 3L), Map.of("v", 9L)));
        respond(404, "", Map.of("x-ms-error-code", "ResourceNotFound"));
        assertFalse(store.update(new Key("p", "x"), Map.of("v", 3L), Map.of()));
    }

    @Test
    public void queryFiltersThePrefixAndPagesWithContinuations() {
        respond(200, "{\"value\":[{\"PartitionKey\":\"p\",\"RowKey\":\"o:1\"}]}",
                Map.of("x-ms-continuation-NextPartitionKey", "1!4!cA--", "x-ms-continuation-NextRowKey", "1!8!bzoy"));
        Page first = store.query("p", "o:", 1, null);
        assertEquals(List.of(new Item(new Key("p", "o:1"), Map.of())), first.items());
        assertEquals("GET /acct/orders()?$filter=PartitionKey eq 'p' and RowKey ge 'o:' and RowKey lt 'o;'&$top=1",
                requests.get(0));
        respond(200, "{\"value\":[{\"PartitionKey\":\"p\",\"RowKey\":\"o:2\"}]}", Map.of());
        Page second = store.query("p", "o:", 1, first.nextPageToken());
        assertTrue(requests.get(1).endsWith("&NextPartitionKey=1!4!cA--&NextRowKey=1!8!bzoy"));
        assertNull(second.nextPageToken());
        assertEquals("ab", AzureTableKeyValueStore.upperBound("aa"));
    }

    @Test
    public void deleteIgnoresMissingEntitiesRetriesAndRefuses() {
        respond(404, "", Map.of("x-ms-error-code", "ResourceNotFound"));
        store.delete(new Key("p", "s"));
        assertEquals("*", headers.get(0).get("if-match"));
        respond(503, "", Map.of("x-ms-error-code", "ServerBusy"));
        store.delete(new Key("p", "s"));
        assertEquals(3, requests.size());
        respond(403, "", Map.of("x-ms-error-code", "AuthorizationFailure"));
        assertThrows(KeyValueAccessDeniedException.class, () -> store.delete(new Key("p", "s")));
    }
}
