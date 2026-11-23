package eu.cloudhopper.mc.provider.aws.auth;

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

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AwsCredentialsTest {

    private HttpServer server;
    private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    private volatile String expiration = "2026-11-23T12:00:00Z";

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/2018-06-01/credentials", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization);
            byte[] body = ("{\"AccessKeyId\":\"ASIA" + authorizations.size() + "\",\"SecretAccessKey\":\"secret\","
                    + "\"Token\":\"session\",\"Expiration\":\"" + expiration + "\"}").getBytes(StandardCharsets.UTF_8);
            int status = "token-a".equals(authorization) || "token-b".equals(authorization) ? 200 : 403;
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/2018-06-01/credentials";
    }

    private static ContainerCredentials container(Instant now) {
        return new ContainerCredentials(HttpClient.newHttpClient(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    public void theEnvironmentVariablesComeFirst() {
        AwsCredentials credentials = AwsCredentials.resolve(Map.of("AWS_ACCESS_KEY_ID", "AKID", "AWS_SECRET_ACCESS_KEY", "s",
                "AWS_SESSION_TOKEN", "t", "AWS_CONTAINER_CREDENTIALS_FULL_URI", uri()), container(Instant.now()));
        assertEquals(new AwsCredentials("AKID", "s", "t"), credentials);
        assertEquals(List.of(), authorizations);
    }

    /** SnapStart functions get no access keys in the environment, only the endpoint. */
    @Test
    public void withoutAccessKeysTheContainerEndpointIsAskedAndCachedUntilShortlyBeforeExpiry() {
        Map<String, String> environment = Map.of("AWS_CONTAINER_CREDENTIALS_FULL_URI", uri(),
                "AWS_CONTAINER_AUTHORIZATION_TOKEN", "token-a");
        Instant[] now = {Instant.parse("2026-11-23T11:00:00Z")};
        ContainerCredentials container = new ContainerCredentials(HttpClient.newHttpClient(), new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now[0];
            }
        });

        assertEquals(new AwsCredentials("ASIA1", "secret", "session"), AwsCredentials.resolve(environment, container));
        now[0] = Instant.parse("2026-11-23T11:54:00Z");
        assertEquals("ASIA1", AwsCredentials.resolve(environment, container).accessKeyId());
        assertEquals(List.of("token-a"), authorizations);

        // five minutes before the expiry the credentials are read again, then cached until the new expiry
        expiration = "2026-11-23T13:00:00Z";
        now[0] = Instant.parse("2026-11-23T11:56:00Z");
        assertEquals("ASIA2", AwsCredentials.resolve(environment, container).accessKeyId());
        assertEquals("ASIA2", AwsCredentials.resolve(environment, container).accessKeyId());
        assertEquals(2, authorizations.size());
    }

    /** After a SnapStart restore the token is new: cached credentials of the old one are not used. */
    @Test
    public void aNewTokenReadsNewCredentials() {
        ContainerCredentials container = container(Instant.parse("2026-11-23T11:00:00Z"));
        container.get(Map.of("AWS_CONTAINER_CREDENTIALS_FULL_URI", uri(), "AWS_CONTAINER_AUTHORIZATION_TOKEN", "token-a"));
        AwsCredentials restored = container.get(Map.of("AWS_CONTAINER_CREDENTIALS_FULL_URI", uri(),
                "AWS_CONTAINER_AUTHORIZATION_TOKEN", "token-b"));
        assertEquals("ASIA2", restored.accessKeyId());
        assertEquals(List.of("token-a", "token-b"), authorizations);
    }

    @Test
    public void aRefusedRequestOrNoSourceIsAnError() {
        assertThrows(IllegalStateException.class, () -> AwsCredentials.resolve(Map.of(
                "AWS_CONTAINER_CREDENTIALS_FULL_URI", uri(), "AWS_CONTAINER_AUTHORIZATION_TOKEN", "wrong"),
                container(Instant.now())));
        assertNull(container(Instant.now()).get(Map.of()));
        assertThrows(IllegalStateException.class, () -> AwsCredentials.resolve(Map.of(), container(Instant.now())));
    }
}
