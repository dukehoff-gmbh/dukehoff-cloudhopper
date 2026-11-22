package eu.cloudhopper.mc.provider.aws.secrets;

/*-
 * #%L
 * provider-aws - a library from the "Cloudhopper" project.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecretsManagerClientTest {

    record Received(Map<String, List<String>> headers, JsonNode body) {
    }

    record Reply(int status, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ARN = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:report-key-AbCdEf";

    private HttpServer server;
    private final List<Received> received = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            received.add(new Received(exchange.getRequestHeaders(), MAPPER.readTree(request)));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}") : replies.poll();
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private SecretsManagerClient client() {
        return new SecretsManagerClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "eu-central-1",
                () -> new AwsCredentials("AKIDEXAMPLE", "secret", "token"), Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Test
    public void readsTheSecretString() {
        replies.add(new Reply(200, "{\"ARN\":\"" + ARN + "\",\"SecretString\":\"s3cr3t\"}"));
        assertEquals("s3cr3t", client().getSecretValue(ARN));

        Received request = received.get(0);
        assertEquals(ARN, request.body().get("SecretId").asText());
        assertEquals("secretsmanager.GetSecretValue", request.headers().get("X-amz-target").get(0));
        assertEquals("application/x-amz-json-1.1", request.headers().get("Content-type").get(0));
        String authorization = request.headers().get("Authorization").get(0);
        assertTrue(authorization.contains("/eu-central-1/secretsmanager/aws4_request"), authorization);
        assertEquals("token", request.headers().get("X-amz-security-token").get(0));
    }

    @Test
    public void readsABinarySecretAsUtf8() {
        String binary = Base64.getEncoder().encodeToString("{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8));
        replies.add(new Reply(200, "{\"SecretBinary\":\"" + binary + "\"}"));
        assertEquals("{\"type\":\"service_account\"}", client().getSecretValue(ARN));
    }

    @Test
    public void retriesThrottling() {
        replies.add(new Reply(400, "{\"__type\":\"ThrottlingException\",\"message\":\"Rate exceeded\"}"));
        replies.add(new Reply(200, "{\"SecretString\":\"s3cr3t\"}"));
        assertEquals("s3cr3t", client().getSecretValue(ARN));
        assertEquals(2, received.size());
    }

    @Test
    public void doesNotRetryAMissingSecretOrPermission() {
        replies.add(new Reply(400, "{\"__type\":\"ResourceNotFoundException\",\"Message\":\"Secrets Manager can't find the specified secret.\"}"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> client().getSecretValue(ARN));
        assertEquals(1, received.size());
        assertTrue(e.getMessage().contains(ARN) && e.getMessage().contains("ResourceNotFoundException"), e.getMessage());
    }

    @Test
    public void takesTheRegionFromTheArn() {
        assertEquals("eu-west-3", SecretsManagerClient.regionOf("arn:aws:secretsmanager:eu-west-3:123456789012:secret:x-AbCdEf"));
    }

    @Test
    public void sourceReadsReferencedSecretsOnceAndNothingElse() {
        AtomicInteger reads = new AtomicInteger();
        SecretsConfigSource source = new SecretsConfigSource(
                SecretsConfigSource.parse("{\"report.api-key\":\"" + ARN + "\"}"),
                id -> {
                    reads.incrementAndGet();
                    return "value of " + id;
                });
        assertEquals(java.util.Set.of("report.api-key"), source.getPropertyNames());
        assertNull(source.getValue("report.bucket"));
        assertEquals(0, reads.get());
        assertEquals("value of " + ARN, source.getValue("report.api-key"));
        assertEquals("value of " + ARN, source.getValue("report.api-key"));
        assertEquals(1, reads.get());
        assertEquals(250, source.getOrdinal());
    }

    @Test
    public void sourceWithoutReferencesIsEmpty() {
        assertTrue(SecretsConfigSource.parse(null).isEmpty());
        assertFalse(new SecretsConfigSource(Map.of(), id -> "x").getPropertyNames().iterator().hasNext());
    }
}
