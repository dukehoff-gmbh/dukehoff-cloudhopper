package eu.cloudhopper.mc.provider.azure.publisher;

/*-
 * #%L
 * provider-azure-publisher - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.PublishException;
import java.time.Clock;
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

public class StorageQueuePublisherTest {

    private static final int OK = 201;

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

    private <T> StorageQueuePublisher<T> publisher() {
        return new StorageQueuePublisher<>(new ObjectMapper(), HttpClient.newHttpClient(),
                new StorageAccount("acct", "c2VjcmV0", "http", "local"), URI.create(base()), "orders",
                Clock.systemUTC(), Duration.ofMillis(1));
    }

    private static String text(Received request) {
        String body = request.body();
        String data = body.substring(body.indexOf("<MessageText>") + 13, body.indexOf("</MessageText>"));
        return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
    }

    @Test
    public void putsASignedBase64Message() {
        this.<Order>publisher().send(new Order(1, "book"));

        Received request = received.get(0);
        assertTrue(request.headers().get("Authorization").get(0).startsWith("SharedKey acct:"));
        assertEquals(StorageQueuePublisher.VERSION, request.headers().get("X-ms-version").get(0));
        assertEquals("{\"id\":1,\"item\":\"book\"}", text(request));
    }

    @Test
    public void sendAllSendsOneRequestPerMessage() {
        this.<String>publisher().sendAll(List.of("a", "b", "c"));

        assertEquals(List.of("a", "b", "c"), received.stream().map(StorageQueuePublisherTest::text).toList());
    }

    @Test
    public void serverBusyIsRetried() {
        replies.add(new Reply(503, "<Error><Code>ServerBusy</Code></Error>"));

        this.<String>publisher().send("x");

        assertEquals(2, received.size());
    }

    @Test
    public void missingQueuesAreNotRetried() {
        replies.add(new Reply(404, "<Error><Code>QueueNotFound</Code></Error>"));

        PublishException e = assertThrows(PublishException.class, () -> this.<String>publisher().send("x"));
        assertEquals(1, received.size());
        assertTrue(e.getMessage().contains("QueueNotFound"), e.getMessage());
    }

    @Test
    public void tooLargeMessagesAreRejectedBeforeSending() {
        assertThrows(PublishException.class, () -> this.<String>publisher().send("x".repeat(60 * 1024)));
        assertTrue(received.isEmpty());
    }
}
