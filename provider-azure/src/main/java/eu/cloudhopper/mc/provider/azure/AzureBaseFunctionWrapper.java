package eu.cloudhopper.mc.provider.azure;

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
import eu.cloudhopper.mc.runtime.Headers;
import com.microsoft.azure.functions.*;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.HandlerContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.cloudhopper.mc.runtime.EventHandlerContext;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.runtime.StorageEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class used by generated Azure Functions to invoke
 * vendor-neutral {@link CloudRequestHandler} implementations.
 * <p>
 * This wrapper handles input parsing, context adaptation, and response
 * formatting for both HTTP-triggered and timer-triggered Azure Functions.
 *
 * @param <I> the input type
 * @param <O> the output type
 */
public abstract class AzureBaseFunctionWrapper<I, O> {

    /**
     * The wrapped Cloudhopper handler implementation.
     */
    protected final CloudRequestHandler<I, O> handler;

    /**
     * The Java type of the input payload, used for JSON deserialization.
     */
    protected final Type inputType;

    /**
     * The Jackson object mapper used for parsing and serializing JSON.
     */
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs a new Azure function wrapper.
     *
     * @param handler the Cloudhopper handler to delegate to
     * @param inputType the expected input type, used for deserialization
     */
    protected AzureBaseFunctionWrapper(CloudRequestHandler<I, O> handler, Type inputType) {
        this.handler = handler;
        this.inputType = inputType;
    }

    /**
     * Handles HTTP requests by parsing the input body, invoking the handler,
     * and returning a JSON response.
     *
     * @param request the incoming HTTP request
     * @param context the Azure execution context
     * @return the HTTP response message
     */
    protected HttpResponseMessage handleRequest(HttpRequestMessage<String> request, ExecutionContext context) {
        try {
            I input = null;

            if (expectsRequestBody(request)) {
                input = parseInput(request);
            }

            O output = handler.handleRequest(input, Collections.emptyMap(), request.getQueryParameters(),
                    Headers.of(request.getHeaders()), new AzureContextAdapter(context));
            return createSuccessResponse(request, output);
        } catch (Exception e) {
            return createErrorResponse(request, e);
        }
    }

    /**
     * Handles HTTP requests by parsing the input body, invoking the handler,
     * and returning a JSON response.
     *
     * @param request the incoming HTTP request
     * @param context the Azure execution context
     * @param routePattern the pattern with path params, e.g /hello/{id}
     * @return the HTTP response message
     */
    protected HttpResponseMessage handleRequest(HttpRequestMessage<String> request, ExecutionContext context, String routePattern) {
        try {
            I input = null;

            if (expectsRequestBody(request)) {
                input = parseInput(request);
            }

            Map<String, String> pathParams = extractPathParams(request, routePattern);
            Map<String, String> queryParams = request.getQueryParameters();

            O output = handler.handleRequest(input, pathParams, queryParams, Headers.of(request.getHeaders()),
                    new AzureContextAdapter(context));
            return createSuccessResponse(request, output);
        } catch (Exception e) {
            return createErrorResponse(request, e);
        }
    }

    private boolean expectsRequestBody(HttpRequestMessage<String> request) {
        HttpMethod method = request.getHttpMethod();
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    /**
     * Extract the path params by using the routePattern as a template for
     * extracting the ids and the coresponding values
     *
     * @param request
     * @param routePattern
     * @return a map of param id -> param value
     */
    protected Map<String, String> extractPathParams(HttpRequestMessage<?> request, String routePattern) {
        String actualPath = request.getUri().getPath().replaceFirst("^/api/","").replaceFirst("^/+", "");
        String normalizedRoutePattern = routePattern.replaceFirst("^/+", "");

        String[] routeParts = normalizedRoutePattern.split("/");
        String[] pathParts = actualPath.split("/");

        Map<String, String> pathParams = new HashMap<>();

        if (routeParts.length != pathParts.length) {
            throw new IllegalArgumentException("Path mismatch: expected " + routeParts.length + " parts, but got " + pathParts.length);
        }

        for (int i = 0; i < routeParts.length; i++) {
            if (routeParts[i].startsWith("{") && routeParts[i].endsWith("}")) {
                String name = routeParts[i].substring(1, routeParts[i].length() - 1);
                pathParams.put(name, pathParts[i]);
            } else {
                if (!routeParts[i].equals(pathParts[i])) {
                    throw new IllegalArgumentException("Path mismatch at segment " + i + ": expected '" + routeParts[i] + "' but got '" + pathParts[i] + "'");
                }
            }
        }

        return pathParams;
    }

    /**
     * Handles scheduled (timer-triggered) function invocations.
     *
     * @param context the Azure execution context
     */
    protected void handleScheduledRequest(ExecutionContext context) {
        try {
            handler.handleRequest(null, new AzureContextAdapter(context));
        } catch (Exception e) {
            context.getLogger().severe("Error in scheduled request: " + e.getMessage());
        }
    }

    /**
     * Handles one message from a storage queue (queue-triggered function invocations).
     * <p>
     * An exception is passed on, so that the Functions host delivers the message again; after
     * five attempts it moves the message to the {@code <queue>-poison} queue.
     *
     * @param message       the message body
     * @param id            the message id
     * @param dequeueCount  how often the message has been delivered, starting at 1
     * @param insertionTime when the message was added to the queue, as sent by the host
     * @param queueName     the name of the queue
     * @param context       the Azure execution context
     */
    protected void handleQueueMessage(String message, String id, long dequeueCount, String insertionTime,
                                      String queueName, ExecutionContext context) {
        I input;
        try {
            input = parseBody(message);
        } catch (IOException e) {
            throw new IllegalArgumentException("Queue message " + id + " is not valid JSON for " + inputType, e);
        }
        EventMetadata meta = new EventMetadata(id, queueName, parseTime(insertionTime), (int) dequeueCount, null);
        handler.handleRequest(input, new EventHandlerContext(new AzureContextAdapter(context), meta));
    }

    /**
     * Handles a "blob created" event that Event Grid put on a storage queue (Event Grid schema),
     * by invoking the function with a {@link StorageEvent}. If the function throws, the exception
     * propagates and the queue delivers the event again.
     *
     * @param message      the queue message: one Event Grid event as JSON
     * @param dequeueCount how often the message has been delivered
     * @param context      the Azure execution context
     */
    @SuppressWarnings("unchecked")
    protected void handleBlobCreatedEvent(String message, long dequeueCount, ExecutionContext context) {
        JsonNode node;
        try {
            String json = message.trim();
            if (!json.startsWith("{") && !json.startsWith("[")) {
                // tolerate an additional Base64 layer
                json = new String(Base64.getDecoder().decode(json), StandardCharsets.UTF_8);
            }
            node = objectMapper.readTree(json);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Storage event is not an Event Grid event: " + message, e);
        }
        for (JsonNode event : node.isArray() ? node : java.util.List.of(node)) {
            // subject: /blobServices/default/containers/<container>/blobs/<blob name>
            String subject = event.path("subject").asText();
            String marker = "/containers/";
            int c = subject.indexOf(marker);
            int b = subject.indexOf("/blobs/");
            if (c < 0 || b < c) {
                throw new IllegalArgumentException("Unexpected storage event subject: " + subject);
            }
            String container = subject.substring(c + marker.length(), b);
            String blob = subject.substring(b + "/blobs/".length());
            long size = event.path("data").path("contentLength").asLong(-1);
            StorageEvent storageEvent = new StorageEvent(container, blob, size, StorageEvent.Kind.CREATED);

            Map<String, String> attributes = new HashMap<>();
            attributes.put("eventType", event.path("eventType").asText());
            if (event.path("data").hasNonNull("contentType")) {
                attributes.put("contentType", event.path("data").path("contentType").asText());
            }
            EventMetadata meta = new EventMetadata(event.path("id").asText(null), event.path("topic").asText(null),
                    parseTime(event.path("eventTime").asText(null)), (int) dequeueCount, attributes);
            I input = inputType == StorageEvent.class ? (I) storageEvent : null;
            handler.handleRequest(input, new EventHandlerContext(new AzureContextAdapter(context), meta));
        }
    }

    private static Instant parseTime(String time) {
        if (time == null) {
            return null;
        }
        String t = time.trim();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() > 1) {
            t = t.substring(1, t.length() - 1);
        }
        try {
            return OffsetDateTime.parse(t).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private I parseBody(String body) throws IOException {
        if (body == null || inputType == Void.class) {
            return null;
        }
        if (inputType == String.class) {
            return (I) body;
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(inputType);
        return objectMapper.readValue(body, javaType);
    }

    /**
     * Parses the input payload from the request body using Jackson.
     *
     * @param request the HTTP request
     * @return the deserialized input
     * @throws IOException if the payload is invalid
     */
    private I parseInput(HttpRequestMessage<String> request) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(inputType);
        return objectMapper.readValue(request.getBody(), javaType);
    }

    /**
     * Creates a successful JSON response.
     *
     * @param request the original HTTP request
     * @param output the function output
     * @return the success response
     * @throws JsonProcessingException if serialization fails
     */
    private HttpResponseMessage createSuccessResponse(HttpRequestMessage<String> request, O output)
            throws JsonProcessingException {
        return request.createResponseBuilder(HttpStatus.OK)
                .body(objectMapper.writeValueAsString(output))
                .header("Content-Type", "application/json")
                .build();
    }

    /**
     * Creates a generic error response.
     *
     * @param request the original HTTP request
     * @param e the exception thrown
     * @return the error response
     */
    private HttpResponseMessage createErrorResponse(HttpRequestMessage<String> request, Exception e) {
        Logger.getLogger(AzureBaseFunctionWrapper.class.getName()).log(Level.SEVERE, "Error creating Response", e);
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing request: " + e.getMessage())
                .build();
    }

    /**
     * Adapter that translates Azure's {@link ExecutionContext} to Cloudhopper's
     * {@link HandlerContext}.
     */
    private static class AzureContextAdapter implements HandlerContext {

        private final ExecutionContext context;

        /**
         * Creates a new context adapter.
         *
         * @param context the Azure execution context
         */
        public AzureContextAdapter(ExecutionContext context) {
            this.context = context;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getRequestId() {
            return context.getInvocationId();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionName() {
            return context.getFunctionName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionVersion() {
            return System.getenv("FUNCTIONS_EXTENSION_VERSION");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getInvokedFunctionArn() {
            return null; // Not available in Azure
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getLogGroupName() {
            return null; // Not available in Azure
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getLogStreamName() {
            return null; // Not available in Azure
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getRemainingTimeInMillis() {
            return -1; // Not available in Azure
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getMemoryLimitInMB() {
            return Integer.parseInt(System.getenv("WEBSITE_MEMORY_LIMIT_MB"));
        }
    }
}
