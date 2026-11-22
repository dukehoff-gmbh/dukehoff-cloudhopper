package eu.cloudhopper.mc.provider.gcp.publisher;

/*-
 * #%L
 * provider-gcp-publisher - a library from the "Cloudhopper" project.
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Publishes to a Pub/Sub topic through the REST API ({@code topics.publish}). Batches stay within
 * Pub/Sub's limits of 1,000 messages and 10 MB per request; rate limits and server errors are
 * retried with backoff.
 *
 * @param <T> the message type
 */
class PubSubPublisher<T> implements Publisher<T> {

    static final int MAX_BATCH_MESSAGES = 1000;
    /** 10 MB minus room for the JSON around the Base64 data. */
    static final int MAX_BATCH_BYTES = 9 * 1024 * 1024;
    private static final int ATTEMPTS = 4;
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504);

    interface TokenSource {
        String token() throws IOException;
    }

    private final Gson gson;
    private final HttpClient http;
    private final URI publish;
    private final String topic;
    private final TokenSource tokens;
    private final Duration backoff;

    /**
     * @param publish the publish URI, e.g. {@code https://pubsub.googleapis.com/v1/projects/p/topics/t:publish}
     * @param topic   the topic's resource name, for messages
     */
    PubSubPublisher(Gson gson, HttpClient http, URI publish, String topic, TokenSource tokens, Duration backoff) {
        this.gson = gson;
        this.http = http;
        this.publish = publish;
        this.topic = topic;
        this.tokens = tokens;
        this.backoff = backoff;
    }

    @Override
    public void send(T message) {
        publish(List.of(encode(message)));
    }

    @Override
    public void sendAll(Collection<? extends T> messages) {
        List<String> batch = new ArrayList<>();
        int bytes = 0;
        for (T message : messages) {
            String data = encode(message);
            if (!batch.isEmpty() && (batch.size() == MAX_BATCH_MESSAGES || bytes + data.length() > MAX_BATCH_BYTES)) {
                publish(batch);
                batch = new ArrayList<>();
                bytes = 0;
            }
            batch.add(data);
            bytes += data.length();
        }
        if (!batch.isEmpty()) {
            publish(batch);
        }
    }

    /** The message as the queue triggers read it (a String as it is, anything else as JSON), Base64-encoded. */
    private String encode(T message) {
        String body = message instanceof String text ? text : gson.toJson(message);
        String data = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        if (data.length() > MAX_BATCH_BYTES) {
            throw new PublishException("Message for " + topic + " exceeds Pub/Sub's limit of 10 MB");
        }
        return data;
    }

    private void publish(List<String> data) {
        JsonObject request = new JsonObject();
        JsonArray messages = new JsonArray();
        data.forEach(d -> {
            JsonObject message = new JsonObject();
            message.addProperty("data", d);
            messages.add(message);
        });
        request.add("messages", messages);
        String body = gson.toJson(request);
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(publish).timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + tokens.token())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
                lastError = response.statusCode() + ": " + response.body();
                if (!RETRYABLE.contains(response.statusCode())) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PublishException("Interrupted while publishing to " + topic, e);
            }
            if (attempt < ATTEMPTS) {
                try {
                    Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PublishException("Interrupted while publishing to " + topic, e);
                }
            }
        }
        throw new PublishException("Publishing " + data.size() + " message(s) to " + topic + " failed: " + lastError);
    }
}
