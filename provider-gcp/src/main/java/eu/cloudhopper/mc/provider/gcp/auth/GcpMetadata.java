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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The project and an access token of the function's service account, from the metadata server
 * that every Cloud Run service and Cloud Function can reach. The token is cached until shortly
 * before it expires. The metadata host can be changed with {@code GCE_METADATA_HOST}, as with the
 * Google client libraries.
 */
public class GcpMetadata {

    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(2);

    private final HttpClient http;
    private final URI base;
    private final Clock clock;
    private String token;
    private Instant expiry = Instant.MIN;
    private String project;

    /** The metadata server of the environment. */
    public GcpMetadata() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                URI.create("http://" + host() + "/computeMetadata/v1/"), Clock.systemUTC());
    }

    /**
     * @param http  the HTTP client
     * @param base  the base URI of the metadata API, ending with {@code /computeMetadata/v1/}
     * @param clock the clock for the token's expiry
     */
    public GcpMetadata(HttpClient http, URI base, Clock clock) {
        this.http = http;
        this.base = base;
        this.clock = clock;
    }

    private static String host() {
        String host = System.getenv("GCE_METADATA_HOST");
        return host == null || host.isBlank() ? "metadata.google.internal" : host;
    }

    /**
     * @return an access token of the service account the function runs as
     * @throws IOException if the metadata server cannot be reached
     */
    public synchronized String accessToken() throws IOException {
        if (token == null || clock.instant().isAfter(expiry.minus(REFRESH_BEFORE_EXPIRY))) {
            JsonObject response = JsonParser.parseString(get("instance/service-accounts/default/token")).getAsJsonObject();
            token = response.get("access_token").getAsString();
            expiry = clock.instant().plusSeconds(response.get("expires_in").getAsLong());
        }
        return token;
    }

    /**
     * @return the project id, from {@code GOOGLE_CLOUD_PROJECT} or the metadata server
     * @throws IOException if the metadata server cannot be reached
     */
    public synchronized String projectId() throws IOException {
        if (project == null) {
            String configured = System.getenv("GOOGLE_CLOUD_PROJECT");
            project = configured != null && !configured.isBlank() ? configured : get("project/project-id").trim();
        }
        return project;
    }

    private String get(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).header("Metadata-Flavor", "Google")
                .timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Metadata server answered " + response.statusCode() + " for " + path);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while asking the metadata server", e);
        }
    }
}
