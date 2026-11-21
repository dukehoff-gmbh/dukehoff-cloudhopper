package eu.cloudhopper.mc.provider.gcp.auth;

/*-
 * #%L
 * provider-gcp - a library from the "Cloudhopper" project.
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

public class GcpMetadataTest {

    private static final int OK = 200;

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

    @Test
    public void tokenIsCachedUntilShortlyBeforeItExpires() throws Exception {
        replies.add(new Reply(200, "{\"access_token\":\"t1\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));
        replies.add(new Reply(200, "{\"access_token\":\"t2\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));
        Instant[] now = {Instant.parse("2026-11-21T10:00:00Z")};
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            public Instant instant() {
                return now[0];
            }
        };
        GcpMetadata metadata = new GcpMetadata(HttpClient.newHttpClient(), URI.create(base() + "/computeMetadata/v1/"), clock);

        assertEquals("t1", metadata.accessToken());
        now[0] = now[0].plusSeconds(3000);
        assertEquals("t1", metadata.accessToken());
        now[0] = now[0].plusSeconds(500);
        assertEquals("t2", metadata.accessToken());

        assertEquals(2, received.size());
        assertEquals("Google", received.get(0).headers().get("Metadata-flavor").get(0));
    }
}
