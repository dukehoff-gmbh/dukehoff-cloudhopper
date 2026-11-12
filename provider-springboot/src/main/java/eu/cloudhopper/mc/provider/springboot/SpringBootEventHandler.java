package eu.cloudhopper.mc.provider.springboot;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.provider.http.LocalHandlerContext;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.EventHandlerContext;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.runtime.Headers;
import eu.cloudhopper.mc.runtime.StorageEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for exposing an event-triggered Cloudhopper function as a Spring Boot endpoint,
 * {@code POST /events/<function name>}.
 * <p>
 * The request follows the CloudEvents HTTP binding in binary mode: the body is the event data
 * (for a queue, the message as JSON; for a bucket, {@code {"bucket": ..., "key": ..., "size": ...}},
 * where {@code bucket} defaults to the name in the annotation), and the optional headers {@code ce-id}, {@code ce-source}
 * and {@code ce-time} fill the {@link EventMetadata}; other {@code ce-*} headers become its
 * attributes. A successful invocation answers {@code 204 No Content}; an exception answers with
 * an error status, so that the caller can deliver the event again.
 *
 * @param <I> input type
 * @param <O> output type (ignored for events)
 */
public abstract class SpringBootEventHandler<I, O> implements EventEndpoint {

    private static final Set<String> CORE_ATTRIBUTES = Set.of("ce-id", "ce-source", "ce-time", "ce-type", "ce-specversion");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloudRequestHandler<I, O> handler;
    private final JavaType inputType;
    private final String functionId;
    private final EventSource source;
    private final String name;

    /**
     * @param handler    the function
     * @param inputType  the type of the event data
     * @param functionId the function id
     * @param source     where the events come from
     * @param name       the name of the queue or bucket
     */
    protected SpringBootEventHandler(CloudRequestHandler<I, O> handler, TypeReference<I> inputType,
                                     String functionId, EventSource source, String name) {
        this.handler = Objects.requireNonNull(handler);
        this.inputType = objectMapper.getTypeFactory().constructType(inputType);
        this.functionId = functionId;
        this.source = source;
        this.name = name;
    }

    /**
     * Creates a handler that only serves its HTTP endpoint; broker bindings do not see it,
     * because it does not know its queue.
     *
     * @param handler   the function
     * @param inputType the type of the event data
     * @deprecated generated code passes function id, source and name
     */
    @Deprecated
    protected SpringBootEventHandler(CloudRequestHandler<I, O> handler, TypeReference<I> inputType) {
        this(handler, inputType, null, null, null);
    }

    @Override
    public String functionId() {
        return functionId;
    }

    @Override
    public EventSource source() {
        return source;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void deliver(String body, Map<String, String> headers, int deliveryAttempt) throws IOException {
        invoke(body, headers, name, deliveryAttempt);
    }

    /**
     * Invokes the function for one event.
     *
     * @param body          the event data, or {@code null}
     * @param headers       the request headers (first value of each)
     * @param defaultSource the source reported when the request has no {@code ce-source} header
     * @return {@code 204 No Content}
     * @throws IOException if the body is not valid JSON for the input type
     */
    public ResponseEntity<Void> handleEvent(String body, Map<String, String> headers, String defaultSource)
            throws IOException {
        invoke(body, headers, defaultSource, 0);
        return ResponseEntity.noContent().build();
    }

    private void invoke(String body, Map<String, String> headers, String defaultSource, int deliveryAttempt)
            throws IOException {
        Map<String, String> h = Headers.of(headers);
        Map<String, String> attributes = new HashMap<>();
        h.forEach((name, value) -> {
            String key = name.toLowerCase(Locale.ROOT);
            if (key.startsWith("ce-") && !CORE_ATTRIBUTES.contains(key)) {
                attributes.put(key.substring(3), value);
            }
        });
        EventMetadata meta = new EventMetadata(
                h.getOrDefault("ce-id", UUID.randomUUID().toString()),
                h.getOrDefault("ce-source", defaultSource),
                parseTime(h.get("ce-time")),
                deliveryAttempt,
                attributes);
        handler.handleRequest(parseBody(body, defaultSource), new EventHandlerContext(new LocalHandlerContext(), meta));
    }

    private static Instant parseTime(String time) {
        if (time == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(time).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private I parseBody(String body, String defaultSource) throws IOException {
        if (body == null || body.isEmpty() || inputType.getRawClass() == Void.class) {
            return null;
        }
        if (inputType.getRawClass() == String.class) {
            return (I) body;
        }
        if (inputType.getRawClass() == StorageEvent.class) {
            JsonNode node = objectMapper.readTree(body);
            if (!node.hasNonNull("key")) {
                throw new IllegalArgumentException("A storage event needs a \"key\"");
            }
            return (I) new StorageEvent(node.path("bucket").asText(defaultSource), node.path("key").asText(),
                    node.path("size").asLong(-1), StorageEvent.Kind.CREATED);
        }
        return objectMapper.readValue(body, inputType);
    }
}
