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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reads secrets from Azure Key Vault without the Azure SDK, with a token of the function app's
 * managed identity from the App Service identity endpoint ({@code IDENTITY_ENDPOINT},
 * {@code IDENTITY_HEADER}). Retries throttling, server and I/O errors, and {@code 403} for a while:
 * a new role assignment takes a few minutes to take effect. Error messages name the secret, never
 * its value.
 */
public class KeyVaultClient {

    private static final String VAULT_RESOURCE = "https://vault.azure.net";
    private static final int ATTEMPTS = 6;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final URI identityEndpoint;
    private final String identityHeader;
    private final Duration backoff;

    /**
     * @param http             the HTTP client
     * @param identityEndpoint the managed identity endpoint
     * @param identityHeader   the secret header value of the identity endpoint
     * @param backoff          the first pause before a retry; it doubles per attempt
     */
    public KeyVaultClient(HttpClient http, URI identityEndpoint, String identityHeader, Duration backoff) {
        this.http = http;
        this.identityEndpoint = identityEndpoint;
        this.identityHeader = identityHeader;
        this.backoff = backoff;
    }

    /** The client of the function app's managed identity. */
    public static KeyVaultClient fromEnvironment() {
        String endpoint = System.getenv("IDENTITY_ENDPOINT");
        String header = System.getenv("IDENTITY_HEADER");
        if (endpoint == null || header == null) {
            throw new IllegalStateException("No managed identity: IDENTITY_ENDPOINT and IDENTITY_HEADER are not set; "
                    + "the function app needs a system-assigned identity to read secrets");
        }
        return new KeyVaultClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create(endpoint), header, Duration.ofSeconds(2));
    }

    /**
     * The current value of a secret.
     *
     * @param secretUri the secret's URI, e.g. {@code https://my-vault.vault.azure.net/secrets/report-key}
     * @return the value
     */
    public String getSecret(String secretUri) {
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                                URI.create(secretUri + (secretUri.contains("?") ? "&" : "?") + "api-version=7.4"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token())
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                JsonNode json = parse(response.body());
                if (response.statusCode() == 200 && json.hasNonNull("value")) {
                    return json.get("value").asText();
                }
                lastError = response.statusCode() + " " + json.path("error").path("code").asText("")
                        + ": " + json.path("error").path("message").asText("");
                int status = response.statusCode();
                // 403: a role assignment that has not taken effect yet
                if (status != 403 && status != 429 && status < 500) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading secret " + secretUri, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, secretUri);
            }
        }
        throw new IllegalStateException("Cannot read secret " + secretUri + " from Key Vault: " + lastError);
    }

    private String token() throws IOException, InterruptedException {
        URI uri = URI.create(identityEndpoint + (identityEndpoint.toString().contains("?") ? "&" : "?")
                + "resource=" + URLEncoder.encode(VAULT_RESOURCE, StandardCharsets.UTF_8) + "&api-version=2019-08-01");
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("X-IDENTITY-HEADER", identityHeader).GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = parse(response.body());
        if (response.statusCode() != 200 || !json.hasNonNull("access_token")) {
            throw new IOException("The managed identity endpoint answered " + response.statusCode());
        }
        return json.get("access_token").asText();
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
        } catch (IOException e) {
            return mapper.createObjectNode();
        }
    }

    private void pause(int attempt, String secretUri) {
        try {
            Thread.sleep(backoff.toMillis() << (attempt - 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading secret " + secretUri, e);
        }
    }
}
