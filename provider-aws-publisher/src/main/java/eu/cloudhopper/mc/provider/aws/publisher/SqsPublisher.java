package eu.cloudhopper.mc.provider.aws.publisher;

/*-
 * #%L
 * provider-aws-publisher - a library from the "Cloudhopper" project.
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.provider.aws.auth.SigV4Signer;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sends messages to an SQS queue through the SQS JSON API ({@code AmazonSQS.SendMessage} and
 * {@code SendMessageBatch}), signed with SigV4. Throttling and server errors are retried with
 * backoff; a batch respects SQS's limits of 10 messages and 256 KiB.
 *
 * @param <T> the message type
 */
class SqsPublisher<T> implements Publisher<T> {

    static final int MAX_BATCH_MESSAGES = 10;
    static final int MAX_BYTES = 256 * 1024;
    private static final int ATTEMPTS = 4;
    private static final Set<String> RETRYABLE = Set.of("ThrottlingException", "RequestThrottled",
            "ServiceUnavailable", "InternalError", "KmsThrottled", "RequestThrottledException");

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final String region;
    private final String queueUrl;
    private final Supplier<AwsCredentials> credentials;
    private final Clock clock;
    private final Duration backoff;

    SqsPublisher(ObjectMapper mapper, HttpClient http, URI endpoint, String region, String queueUrl,
            Supplier<AwsCredentials> credentials, Clock clock, Duration backoff) {
        this.mapper = mapper;
        this.http = http;
        this.endpoint = endpoint;
        this.region = region;
        this.queueUrl = queueUrl;
        this.credentials = credentials;
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public void send(T message) {
        String body = serialize(message);
        ObjectNode request = mapper.createObjectNode().put("QueueUrl", queueUrl).put("MessageBody", body);
        call("SendMessage", request);
    }

    @Override
    public void sendAll(Collection<? extends T> messages) {
        List<String> bodies = new ArrayList<>();
        messages.forEach(message -> bodies.add(serialize(message)));
        List<String> batch = new ArrayList<>();
        int batchBytes = 0;
        for (String body : bodies) {
            int bytes = body.getBytes(StandardCharsets.UTF_8).length;
            if (!batch.isEmpty() && (batch.size() == MAX_BATCH_MESSAGES || batchBytes + bytes > MAX_BYTES)) {
                sendBatch(batch);
                batch = new ArrayList<>();
                batchBytes = 0;
            }
            batch.add(body);
            batchBytes += bytes;
        }
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    /** Sends one batch; entries SQS rejects for its own reasons are sent again, sender faults fail. */
    private void sendBatch(List<String> bodies) {
        List<String> pending = new ArrayList<>(bodies);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            ids.add(i);
        }
        for (int attempt = 1; ; attempt++) {
            ObjectNode request = mapper.createObjectNode().put("QueueUrl", queueUrl);
            ArrayNode entries = request.putArray("Entries");
            for (int i = 0; i < pending.size(); i++) {
                entries.addObject().put("Id", String.valueOf(ids.get(i))).put("MessageBody", pending.get(i));
            }
            JsonNode response = call("SendMessageBatch", request);
            JsonNode failed = response.path("Failed");
            if (!failed.isArray() || failed.isEmpty()) {
                return;
            }
            List<String> retry = new ArrayList<>();
            List<Integer> retryIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (JsonNode failure : failed) {
                int id = Integer.parseInt(failure.path("Id").asText());
                String error = failure.path("Code").asText() + ": " + failure.path("Message").asText();
                if (failure.path("SenderFault").asBoolean(false) || attempt == ATTEMPTS) {
                    errors.add(error);
                } else {
                    retryIds.add(id);
                    retry.add(pending.get(ids.indexOf(id)));
                }
            }
            if (!errors.isEmpty()) {
                throw new PublishException(errors.size() + " message(s) not accepted by " + queueUrl + ": " + errors);
            }
            pending = retry;
            ids = retryIds;
            pause(attempt);
        }
    }

    private String serialize(T message) {
        try {
            // like the queue triggers read their input: a String as it is, anything else as JSON
            String body = message instanceof String text ? text : mapper.writeValueAsString(message);
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new PublishException("Message for " + queueUrl + " exceeds SQS's limit of 256 KiB");
            }
            return body;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new PublishException("Cannot serialize the message for " + queueUrl, e);
        }
    }

    /** One API call, retried on throttling, server errors and I/O errors. */
    private JsonNode call(String action, ObjectNode request) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new PublishException("Cannot write the request for " + queueUrl, e);
        }
        Map<String, String> headers = Map.of("Content-Type", "application/x-amz-json-1.0",
                "X-Amz-Target", "AmazonSQS." + action);
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, String> signed = SigV4Signer.sign("POST", endpoint, headers, body, "sqs", region,
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
                if (response.statusCode() == 200) {
                    return mapper.readTree(response.body().isEmpty() ? "{}" : response.body());
                }
                JsonNode error = parse(response.body());
                String type = error.path("__type").asText("");
                type = type.substring(type.lastIndexOf('#') + 1);
                lastError = response.statusCode() + " " + type + ": " + error.path("message").asText(response.body());
                if (response.statusCode() < 500 && !RETRYABLE.contains(type)) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PublishException("Interrupted while sending to " + queueUrl, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt);
            }
        }
        throw new PublishException("SQS " + action + " to " + queueUrl + " failed: " + lastError);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
        } catch (IOException e) {
            return mapper.createObjectNode();
        }
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishException("Interrupted while sending to " + queueUrl, e);
        }
    }
}
