package eu.cloudhopper.mc.provider.azure.secrets;

/*-
 * #%L
 * provider-azure - a library from the "Cloudhopper" project.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KeyVaultClientTest {

    record Reply(int status, String body) {
    }

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Reply> vaultReplies = new ConcurrentLinkedDeque<>();

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/msi/token", exchange -> {
            requests.add("token " + exchange.getRequestURI().getQuery() + " "
                    + exchange.getRequestHeaders().getFirst("X-IDENTITY-HEADER"));
            reply(exchange, new Reply(200, "{\"access_token\":\"tok\",\"expires_on\":\"0\"}"));
        });
        server.createContext("/secrets/", exchange -> {
            requests.add("secret " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, vaultReplies.isEmpty() ? new Reply(500, "{}") : vaultReplies.poll());
        });
        server.start();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, Reply reply) throws java.io.IOException {
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private KeyVaultClient client() {
        return new KeyVaultClient(HttpClient.newHttpClient(), URI.create(base() + "/msi/token"), "hdr", Duration.ofMillis(1));
    }

    @Test
    public void readsTheSecretWithAManagedIdentityToken() {
        vaultReplies.add(new Reply(200, "{\"value\":\"s3cr3t\",\"id\":\"x\"}"));
        assertEquals("s3cr3t", client().getSecret(base() + "/secrets/report-key"));
        assertTrue(requests.get(0).startsWith("token resource=https://vault.azure.net&api-version=2019-08-01 hdr"), requests.get(0));
        assertEquals("secret /secrets/report-key?api-version=7.4 Bearer tok", requests.get(1));
    }

    @Test
    public void retriesWhileTheRoleAssignmentIsNotEffective() {
        vaultReplies.add(new Reply(403, "{\"error\":{\"code\":\"Forbidden\",\"message\":\"no access\"}}"));
        vaultReplies.add(new Reply(200, "{\"value\":\"s3cr3t\"}"));
        assertEquals("s3cr3t", client().getSecret(base() + "/secrets/report-key"));
    }

    @Test
    public void doesNotRetryAMissingSecret() {
        vaultReplies.add(new Reply(404, "{\"error\":{\"code\":\"SecretNotFound\",\"message\":\"not found\"}}"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> client().getSecret(base() + "/secrets/missing"));
        assertTrue(e.getMessage().contains("SecretNotFound") && e.getMessage().contains("/secrets/missing"), e.getMessage());
        assertEquals(2, requests.size());
    }
}
