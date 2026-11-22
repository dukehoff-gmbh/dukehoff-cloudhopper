package eu.cloudhopper.mc.provider.gcp.publisher;

/*-
 * #%L
 * provider-gcp-publisher - a library from the "Cloudhopper" project.
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import eu.cloudhopper.mc.runtime.PublishException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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

public class PubSubPublisherTest {

    private static final int OK = 200;
    private static final String TOPIC = "projects/p/topics/orders";

    record Received(Map<String, List<String>> headers, String body) {
    }

    record Reply(int status, String body) {
    }

    private HttpServer server;
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(new Received(exchange.getRequestHeaders(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply = replies.poll();
            if (reply == null) {
                reply = new Reply(OK, "{}");
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public record Order(int id, String item) {
    }

    private <T> PubSubPublisher<T> publisher() {
        return new PubSubPublisher<>(new Gson(), HttpClient.newHttpClient(), URI.create(base() + "/v1/" + TOPIC + ":publish"),
                TOPIC, () -> "token-1", Duration.ofMillis(1));
    }

    private static List<String> data(Received request) {
        JsonArray messages = JsonParser.parseString(request.body()).getAsJsonObject().getAsJsonArray("messages");
        List<String> result = new ArrayList<>();
        messages.forEach(m -> result.add(new String(Base64.getDecoder().decode(
                m.getAsJsonObject().get("data").getAsString()), StandardCharsets.UTF_8)));
        return result;
    }

    @Test
    public void publishesJsonAndStringsBase64Encoded() {
        this.<Order>publisher().send(new Order(1, "book"));
        this.<String>publisher().send("plain text");

        assertEquals("Bearer token-1", received.get(0).headers().get("Authorization").get(0));
        assertEquals(List.of("{\"id\":1,\"item\":\"book\"}"), data(received.get(0)));
        assertEquals(List.of("plain text"), data(received.get(1)));
    }

    @Test
    public void sendAllSplitsIntoBatchesOfAThousand() {
        this.<Order>publisher().sendAll(IntStream.range(0, 2500).mapToObj(i -> new Order(i, "x")).toList());

        assertEquals(List.of(1000, 1000, 500), received.stream().map(r -> data(r).size()).toList());
    }

    @Test
    public void unavailableIsRetried() {
        replies.add(new Reply(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}"));
        replies.add(new Reply(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        this.<Order>publisher().send(new Order(1, "book"));

        assertEquals(3, received.size());
    }

    @Test
    public void permissionErrorsAreNotRetried() {
        replies.add(new Reply(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));

        PublishException e = assertThrows(PublishException.class, () -> this.<Order>publisher().send(new Order(1, "book")));

        assertEquals(1, received.size());
        assertTrue(e.getMessage().contains("PERMISSION_DENIED"), e.getMessage());
    }
}
