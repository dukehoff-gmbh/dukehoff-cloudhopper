package eu.cloudhopper.mc.provider.aws.publisher;

/*-
 * #%L
 * provider-aws-publisher - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.runtime.PublishException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqsPublisherTest {

    record Received(Map<String, List<String>> headers, JsonNode body) {
    }

    record Reply(int status, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE = "https://sqs.eu-central-1.amazonaws.com/123456789012/orders";

    private HttpServer server;
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();

    public record Order(int id, String item) {
    }

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode body = MAPPER.readTree(exchange.getRequestBody());
            received.add(new Received(exchange.getRequestHeaders(), body));
            Reply reply = replies.poll();
            if (reply == null) {
                reply = new Reply(200, exchange.getRequestHeaders().getFirst("X-Amz-Target").endsWith("Batch")
                        ? "{\"Successful\":[]}" : "{\"MessageId\":\"m-1\"}");
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private SqsPublisher<Order> publisher() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new SqsPublisher<>(MAPPER, HttpClient.newHttpClient(), endpoint, "eu-central-1", QUEUE,
                () -> new AwsCredentials("AKIDEXAMPLE", "secret", "token"), Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Test
    public void sendsOneSignedMessageAsJson() throws Exception {
        publisher().send(new Order(1, "book"));

        assertEquals(1, received.size());
        Received request = received.get(0);
        assertEquals("AmazonSQS.SendMessage", request.headers().get("X-amz-target").get(0));
        assertEquals("application/x-amz-json-1.0", request.headers().get("Content-type").get(0));
        assertEquals("token", request.headers().get("X-amz-security-token").get(0));
        String authorization = request.headers().get("Authorization").get(0);
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"), authorization);
        assertTrue(authorization.contains("/eu-central-1/sqs/aws4_request"), authorization);
        assertEquals(QUEUE, request.body().get("QueueUrl").asText());
        assertEquals(MAPPER.readTree("{\"id\":1,\"item\":\"book\"}"), MAPPER.readTree(request.body().get("MessageBody").asText()));
    }

    @Test
    public void sendAllSplitsIntoBatchesOfTen() {
        publisher().sendAll(IntStream.range(0, 25).mapToObj(i -> new Order(i, "x")).toList());

        assertEquals(List.of(10, 10, 5), received.stream().map(r -> r.body().get("Entries").size()).toList());
        assertTrue(received.stream().allMatch(r -> r.headers().get("X-amz-target").get(0).equals("AmazonSQS.SendMessageBatch")));
    }

    @Test
    public void sendAllSplitsBySize() {
        String big = "x".repeat(100 * 1024);
        publisher().sendAll(List.of(new Order(1, big), new Order(2, big), new Order(3, big)));

        assertEquals(List.of(2, 1), received.stream().map(r -> r.body().get("Entries").size()).toList());
    }

    @Test
    public void throttlingIsRetried() {
        replies.add(new Reply(400, "{\"__type\":\"com.amazonaws.sqs#ThrottlingException\",\"message\":\"slow down\"}"));
        replies.add(new Reply(503, "{}"));

        publisher().send(new Order(1, "book"));

        assertEquals(3, received.size());
    }

    @Test
    public void clientErrorsAreNotRetried() {
        replies.add(new Reply(400, "{\"__type\":\"com.amazonaws.sqs#QueueDoesNotExist\",\"message\":\"The specified queue does not exist.\"}"));

        PublishException e = assertThrows(PublishException.class, () -> publisher().send(new Order(1, "book")));

        assertEquals(1, received.size());
        assertTrue(e.getMessage().contains("QueueDoesNotExist"), e.getMessage());
    }

    @Test
    public void failedBatchEntriesAreRetriedUnlessTheSenderIsAtFault() {
        replies.add(new Reply(200, "{\"Successful\":[{\"Id\":\"0\"}],\"Failed\":[{\"Id\":\"1\",\"SenderFault\":false,\"Code\":\"InternalError\",\"Message\":\"try again\"}]}"));

        publisher().sendAll(List.of(new Order(1, "a"), new Order(2, "b")));

        assertEquals(2, received.size());
        JsonNode retried = received.get(1).body().get("Entries");
        assertEquals(1, retried.size());
        assertEquals("1", retried.get(0).get("Id").asText());

        received.clear();
        replies.add(new Reply(200, "{\"Failed\":[{\"Id\":\"0\",\"SenderFault\":true,\"Code\":\"InvalidMessageContents\",\"Message\":\"bad\"}]}"));
        PublishException e = assertThrows(PublishException.class, () -> publisher().sendAll(List.of(new Order(3, "c"))));
        assertTrue(e.getMessage().contains("InvalidMessageContents"), e.getMessage());
        assertEquals(1, received.size());
    }

    @Test
    public void stringsAreSentAsTheyAre() {
        SqsPublisher<String> strings = new SqsPublisher<>(MAPPER, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "eu-central-1", QUEUE,
                () -> new AwsCredentials("AKIDEXAMPLE", "secret", null), Clock.systemUTC(), Duration.ofMillis(1));
        strings.send("plain text");
        assertEquals("plain text", received.get(0).body().get("MessageBody").asText());
    }

    @Test
    public void tooLargeMessagesAreRejectedBeforeSending() {
        assertThrows(PublishException.class, () -> publisher().send(new Order(1, "x".repeat(300 * 1024))));
        assertTrue(received.isEmpty());
    }

    @Test
    public void regionComesFromTheQueueUrl() {
        assertEquals("eu-central-1", SqsPublisherFactory.region(URI.create(QUEUE)));
    }
}
