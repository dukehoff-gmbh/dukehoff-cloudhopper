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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.provider.aws.auth.SigV4Signer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads secrets from AWS Secrets Manager ({@code GetSecretValue}) without the AWS SDK, signed with
 * the credentials of the function's execution role. Retries throttling, server and I/O errors.
 * Error messages name the secret, never its value.
 */
public class SecretsManagerClient {

    private static final int ATTEMPTS = 4;
    private static final Set<String> RETRYABLE = Set.of("ThrottlingException", "InternalServiceError");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final URI endpoint;
    private final String region;
    private final Supplier<AwsCredentials> credentials;
    private final Clock clock;
    private final Duration backoff;

    /**
     * @param http        the HTTP client
     * @param endpoint    the Secrets Manager endpoint, e.g. {@code https://secretsmanager.eu-central-1.amazonaws.com/}
     * @param region      the region the requests are signed for
     * @param credentials the credentials, read per request
     * @param clock       the clock of the signature
     * @param backoff     the first pause before a retry; it grows fourfold per attempt
     */
    public SecretsManagerClient(HttpClient http, URI endpoint, String region, Supplier<AwsCredentials> credentials,
            Clock clock, Duration backoff) {
        this.http = http;
        this.endpoint = endpoint;
        this.region = region;
        this.credentials = credentials;
        this.clock = clock;
        this.backoff = backoff;
    }

    /**
     * The client for the region of a secret: the region of its ARN, otherwise {@code AWS_REGION}.
     *
     * @param secretId the ARN or name of the secret
     * @return the client
     */
    public static SecretsManagerClient forSecret(String secretId) {
        String region = regionOf(secretId);
        return new SecretsManagerClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://secretsmanager." + region + ".amazonaws.com/"), region,
                AwsCredentials::fromEnvironment, Clock.systemUTC(), Duration.ofMillis(100));
    }

    static String regionOf(String secretId) {
        // arn:aws:secretsmanager:<region>:<account>:secret:<name>
        String[] arn = secretId.split(":", 6);
        if (arn.length == 6 && arn[0].equals("arn") && !arn[3].isEmpty()) {
            return arn[3];
        }
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("Cannot tell the region of secret " + secretId + ": use its ARN or set AWS_REGION");
        }
        return region;
    }

    /**
     * The current value of a secret: its string, or its binary value as UTF-8.
     *
     * @param secretId the ARN or name of the secret
     * @return the value
     */
    public String getSecretValue(String secretId) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(Map.of("SecretId", secretId));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot write the request for secret " + secretId, e);
        }
        Map<String, String> headers = Map.of("Content-Type", "application/x-amz-json-1.1",
                "X-Amz-Target", "secretsmanager.GetSecretValue");
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, String> signed = SigV4Signer.sign("POST", endpoint, headers, body, "secretsmanager", region,
                    credentials.get(), clock.instant());
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            signed.forEach((name, value) -> {
                if (!"Host".equals(name)) {
                    builder.header(name, value);
                }
            });
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode json = parse(response.body());
                if (response.statusCode() == 200) {
                    if (json.hasNonNull("SecretString")) {
                        return json.get("SecretString").asText();
                    }
                    if (json.hasNonNull("SecretBinary")) {
                        return new String(Base64.getDecoder().decode(json.get("SecretBinary").asText()),
                                StandardCharsets.UTF_8);
                    }
                    throw new IllegalStateException("Secret " + secretId + " has no value");
                }
                String type = json.path("__type").asText("");
                type = type.substring(type.lastIndexOf('#') + 1);
                lastError = response.statusCode() + " " + type + ": " + json.path("message").asText(json.path("Message").asText(""));
                if (response.statusCode() < 500 && !RETRYABLE.contains(type)) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading secret " + secretId, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, secretId);
            }
        }
        throw new IllegalStateException("Cannot read secret " + secretId + " from Secrets Manager: " + lastError);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
        } catch (IOException e) {
            return mapper.createObjectNode();
        }
    }

    private void pause(int attempt, String secretId) {
        try {
            Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading secret " + secretId, e);
        }
    }
}
