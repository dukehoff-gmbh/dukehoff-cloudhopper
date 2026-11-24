package eu.cloudhopper.mc.provider.aws.keyvalue;


/*-
 * #%L
 * provider-aws-keyvalue - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
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

public class DynamoKeyValueStoreTest {

    private final ObjectMapper mapper = new ObjectMapper();
    /** The actions and bodies the store sent. */
    private final List<String> actions = new ArrayList<>();
    private final List<JsonNode> bodies = new ArrayList<>();
    /** The responses to send: status and body. */
    private final Deque<Object[]> responses = new ArrayDeque<>();
    private HttpServer server;
    private DynamoKeyValueStore store;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String target = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            actions.add(target.substring(target.indexOf('.') + 1));
            bodies.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            assertTrue(exchange.getRequestHeaders().getFirst("Authorization").contains("/dynamodb/aws4_request"));
            Object[] response = responses.isEmpty() ? new Object[]{200, "{}"} : responses.poll();
            byte[] body = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders((Integer) response[0], body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        store = new DynamoKeyValueStore(mapper, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "eu-central-1", "tck-accounts",
                () -> new AwsCredentials("AKID", "secret", null), Clock.systemUTC(), Duration.ofMillis(1));
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        responses.add(new Object[]{status, body});
    }

    @Test
    public void putWritesTheKeyAndTypedAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", "Ada");
        attributes.put("count", 1L);
        attributes.put("ratio", 0.5);
        attributes.put("active", true);
        attributes.put("tags", List.of("a", 2L));
        attributes.put("meta", Map.of("k", "v"));
        store.put(new Item(new Key("account:1", "profile"), attributes));
        assertEquals(List.of("PutItem"), actions);
        assertEquals("{\"TableName\":\"tck-accounts\",\"Item\":{\"_pk\":{\"S\":\"account:1\"},\"_sk\":{\"S\":\"profile\"},"
                + "\"name\":{\"S\":\"Ada\"},\"count\":{\"N\":\"1\"},\"ratio\":{\"N\":\"0.5\"},\"active\":{\"BOOL\":true},"
                + "\"tags\":{\"L\":[{\"S\":\"a\"},{\"N\":\"2\"}]},\"meta\":{\"M\":{\"k\":{\"S\":\"v\"}}}}}", bodies.get(0).toString());
    }

    @Test
    public void getReadsConsistentlyAndConvertsTheValues() {
        respond(200, "{\"Item\":{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"s\"},\"count\":{\"N\":\"2\"},"
                + "\"ratio\":{\"N\":\"2.5\"},\"tags\":{\"L\":[{\"BOOL\":false}]},\"meta\":{\"M\":{\"n\":{\"N\":\"7\"}}}}}");
        Item item = store.get(new Key("p", "s")).orElseThrow();
        assertEquals(new Key("p", "s"), item.key());
        assertEquals(Map.of("count", 2L, "ratio", 2.5, "tags", List.of(false), "meta", Map.of("n", 7L)), item.attributes());
        assertTrue(bodies.get(0).path("ConsistentRead").asBoolean());

        respond(200, "{}");
        assertTrue(store.get(new Key("p", "none")).isEmpty());
    }

    @Test
    public void putIfAbsentAndUpdateAreConditional() {
        respond(400, "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ConditionalCheckFailedException\",\"message\":\"The conditional request failed\"}");
        assertFalse(store.putIfAbsent(new Item(new Key("p", "s"), Map.of("a", "b"))));
        assertEquals("attribute_not_exists(#pk)", bodies.get(0).path("ConditionExpression").asText());
        assertTrue(store.putIfAbsent(new Item(new Key("p", "s"), Map.of("a", "b"))));

        Map<String, Object> set = new LinkedHashMap<>();
        set.put("count", 2L);
        set.put("gone", null);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("count", 1L);
        expected.put("lock", null);
        assertTrue(store.update(new Key("p", "s"), set, expected));
        JsonNode update = bodies.get(2);
        assertEquals("SET #s0 = :s0 REMOVE #s1", update.path("UpdateExpression").asText());
        assertEquals("attribute_exists(#pk) AND #e0 = :e0 AND attribute_not_exists(#e1)", update.path("ConditionExpression").asText());
        assertEquals("{\"#pk\":\"_pk\",\"#s0\":\"count\",\"#s1\":\"gone\",\"#e0\":\"count\",\"#e1\":\"lock\"}",
                update.path("ExpressionAttributeNames").toString());
        assertEquals("{\":s0\":{\"N\":\"2\"},\":e0\":{\"N\":\"1\"}}", update.path("ExpressionAttributeValues").toString());

        respond(400, "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ConditionalCheckFailedException\"}");
        assertFalse(store.update(new Key("p", "s"), set, Map.of()));
    }

    @Test
    public void queryPagesWithATokenOfTheLastKey() {
        respond(200, "{\"Items\":[{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"c1\"},\"n\":{\"N\":\"1\"}}],"
                + "\"LastEvaluatedKey\":{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"c1\"}}}");
        Page first = store.query("p", "c", 1, null);
        assertEquals(List.of(new Item(new Key("p", "c1"), Map.of("n", 1L))), first.items());
        assertEquals("#pk = :p AND begins_with(#sk, :s)", bodies.get(0).path("KeyConditionExpression").asText());
        assertEquals(1, bodies.get(0).path("Limit").asInt());

        respond(200, "{\"Items\":[]}");
        Page second = store.query("p", "c", 1, first.nextPageToken());
        assertEquals("{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"c1\"}}", bodies.get(1).path("ExclusiveStartKey").toString());
        assertNull(second.nextPageToken());

        store.query("p", "", 10, null);
        assertEquals("#pk = :p", bodies.get(2).path("KeyConditionExpression").asText());
        assertThrows(KeyValueStoreException.class, () -> store.query("p", "", 10, "not a token!"));
    }

    @Test
    public void batchesHaveAtMost25WritesAndUnprocessedOnesAreRetried() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(new Item(new Key("p", "s" + i), Map.of("i", (long) i)));
        }
        respond(200, "{\"UnprocessedItems\":{\"tck-accounts\":[{\"PutRequest\":{\"Item\":{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"s3\"}}}}]}}");
        store.batchPut(items);
        assertEquals(List.of("BatchWriteItem", "BatchWriteItem", "BatchWriteItem"), actions);
        assertEquals(25, bodies.get(0).path("RequestItems").path("tck-accounts").size());
        assertEquals(1, bodies.get(1).path("RequestItems").path("tck-accounts").size());
        assertEquals(5, bodies.get(2).path("RequestItems").path("tck-accounts").size());

        store.batchDelete(List.of(new Key("p", "s1")));
        assertEquals("{\"DeleteRequest\":{\"Key\":{\"_pk\":{\"S\":\"p\"},\"_sk\":{\"S\":\"s1\"}}}}",
                bodies.get(3).path("RequestItems").path("tck-accounts").get(0).toString());
    }

    @Test
    public void throttlingIsRetriedAndAccessDeniedIsReported() {
        respond(400, "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ProvisionedThroughputExceededException\"}");
        respond(500, "{}");
        store.delete(new Key("p", "s"));
        assertEquals(3, actions.size());

        respond(400, "{\"__type\":\"com.amazon.coral.service#AccessDeniedException\",\"Message\":\"not authorized to perform: dynamodb:PutItem\"}");
        KeyValueAccessDeniedException denied = assertThrows(KeyValueAccessDeniedException.class,
                () -> store.put(new Item(new Key("p", "s"), Map.of())));
        assertTrue(denied.getMessage().contains("dynamodb:PutItem"));

        respond(400, "{\"__type\":\"com.amazonaws.dynamodb.v20120810#ResourceNotFoundException\",\"message\":\"Requested resource not found\"}");
        KeyValueStoreException missing = assertThrows(KeyValueStoreException.class, () -> store.get(new Key("p", "s")));
        assertTrue(missing.getMessage().contains("ResourceNotFoundException"));
    }

    @Test
    public void theEndpointIsThatOfTheRegionUnlessConfigured() {
        assertEquals(URI.create("https://dynamodb.eu-central-1.amazonaws.com/"), DynamoKeyValueStoreFactory.endpoint("eu-central-1", null));
        assertEquals(URI.create("https://dynamodb.cn-north-1.amazonaws.com.cn/"), DynamoKeyValueStoreFactory.endpoint("cn-north-1", null));
        assertEquals(URI.create("http://localhost:8000/"), DynamoKeyValueStoreFactory.endpoint("eu-central-1", "http://localhost:8000"));
    }
}
