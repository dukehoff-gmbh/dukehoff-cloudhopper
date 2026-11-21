package eu.cloudhopper.mc.provider.azure.publisher;

/*-
 * #%L
 * provider-azure-publisher - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sends to an Azure Storage queue through the Queue service REST API ({@code Put Message}),
 * signed with the account's SharedKey. Messages are Base64-encoded, as the queue trigger of
 * Azure Functions expects; a message is limited to 64 KiB. The Queue service has no batch
 * operation, so {@code sendAll} sends one request per message.
 *
 * @param <T> the message type
 */
class StorageQueuePublisher<T> implements Publisher<T> {

    static final int MAX_MESSAGE_CHARS = 64 * 1024;
    static final String VERSION = "2021-08-06";
    private static final int ATTEMPTS = 4;
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504);
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final StorageAccount account;
    private final String queue;
    private final URI messages;
    private final Clock clock;
    private final Duration backoff;

    StorageQueuePublisher(ObjectMapper mapper, HttpClient http, StorageAccount account, URI endpoint, String queue,
            Clock clock, Duration backoff) {
        this.mapper = mapper;
        this.http = http;
        this.account = account;
        this.queue = queue;
        this.messages = URI.create(endpoint.toString().replaceAll("/$", "") + "/" + queue + "/messages");
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public void send(T message) {
        put(encode(message));
    }

    @Override
    public void sendAll(Collection<? extends T> messages) {
        List<String> encoded = new ArrayList<>();
        messages.forEach(m -> encoded.add(encode(m)));
        encoded.forEach(this::put);
    }

    /** The message as the queue trigger reads it (a String as it is, anything else as JSON), Base64-encoded. */
    private String encode(T message) {
        try {
            String body = message instanceof String text ? text : mapper.writeValueAsString(message);
            String data = Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
            if (data.length() > MAX_MESSAGE_CHARS) {
                throw new PublishException("Message for queue " + queue + " exceeds the Storage queue limit of 64 KiB"
                        + " (after Base64 encoding)");
            }
            return data;
        } catch (JsonProcessingException e) {
            throw new PublishException("Cannot serialize the message for queue " + queue, e);
        }
    }

    private void put(String data) {
        byte[] body = ("<QueueMessage><MessageText>" + data + "</MessageText></QueueMessage>").getBytes(StandardCharsets.UTF_8);
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String date = RFC_1123.format(clock.instant());
            String contentType = "application/xml";
            String authorization = account.sharedKey("POST", messages.getRawPath(), Map.of(), body.length, contentType,
                    Map.of("x-ms-date", date, "x-ms-version", VERSION));
            HttpRequest request = HttpRequest.newBuilder(messages).timeout(Duration.ofSeconds(30))
                    .header("x-ms-date", date).header("x-ms-version", VERSION)
                    .header("Content-Type", contentType).header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 201) {
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
                throw new PublishException("Interrupted while sending to queue " + queue, e);
            }
            if (attempt < ATTEMPTS) {
                try {
                    Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PublishException("Interrupted while sending to queue " + queue, e);
                }
            }
        }
        throw new PublishException("Sending to queue " + queue + " of storage account " + account.name() + " failed: " + lastError);
    }
}
