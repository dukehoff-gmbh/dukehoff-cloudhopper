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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Reads credentials from the container credentials endpoint, as Lambda provides it to functions
 * with SnapStart: a GET of {@code AWS_CONTAINER_CREDENTIALS_FULL_URI} (or
 * {@code http://169.254.170.2} + {@code AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}) with the token of
 * {@code AWS_CONTAINER_AUTHORIZATION_TOKEN} (or the file {@code AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE})
 * as {@code Authorization} header. The credentials are cached for the endpoint and token until five
 * minutes before they expire, so a SnapStart restore with a new token reads new ones.
 */
class ContainerCredentials {

    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final int ATTEMPTS = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Cached(String key, AwsCredentials credentials, Instant refreshAt) {
    }

    private final HttpClient http;
    private final Clock clock;
    private volatile Cached cached;

    ContainerCredentials() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), Clock.systemUTC());
    }

    ContainerCredentials(HttpClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    /**
     * @param environment the environment variables
     * @return the credentials, or {@code null} if the environment names no endpoint
     */
    AwsCredentials get(Map<String, String> environment) {
        String uri = endpoint(environment);
        if (uri == null) {
            return null;
        }
        String token = token(environment);
        String key = uri + "|" + token;
        Cached current = cached;
        if (current != null && current.key().equals(key) && clock.instant().isBefore(current.refreshAt())) {
            return current.credentials();
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.key().equals(key) && clock.instant().isBefore(current.refreshAt())) {
                return current.credentials();
            }
            Cached fetched = fetch(key, URI.create(uri), token);
            cached = fetched;
            return fetched.credentials();
        }
    }

    private static String endpoint(Map<String, String> environment) {
        String full = environment.get("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        if (full != null && !full.isBlank()) {
            return full;
        }
        String relative = environment.get("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI");
        return relative == null || relative.isBlank() ? null : "http://169.254.170.2" + relative;
    }

    private static String token(Map<String, String> environment) {
        String token = environment.get("AWS_CONTAINER_AUTHORIZATION_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        String file = environment.get("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read the container authorization token from " + file, e);
            }
        }
        return null;
    }

    private Cached fetch(String key, URI uri, String token) {
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET();
            if (token != null) {
                request.header("Authorization", token);
            }
            try {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode json = MAPPER.readTree(response.body());
                    String id = json.path("AccessKeyId").asText("");
                    String secret = json.path("SecretAccessKey").asText("");
                    if (id.isEmpty() || secret.isEmpty()) {
                        throw new IllegalStateException("The container credentials endpoint returned no credentials");
                    }
                    String sessionToken = json.path("Token").asText("");
                    Instant refreshAt = json.hasNonNull("Expiration")
                            ? Instant.parse(json.get("Expiration").asText()).minus(REFRESH_BEFORE_EXPIRY)
                            : clock.instant().plus(REFRESH_BEFORE_EXPIRY);
                    return new Cached(key, new AwsCredentials(id, secret, sessionToken.isEmpty() ? null : sessionToken),
                            refreshAt);
                }
                lastError = "HTTP " + response.statusCode();
                if (response.statusCode() < 500 && response.statusCode() != 429) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading the container credentials", e);
            }
        }
        throw new IllegalStateException("Cannot read the credentials from the container credentials endpoint: " + lastError);
    }
}
