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


import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.Headers;
import eu.cloudhopper.mc.runtime.HttpReply;
import eu.cloudhopper.mc.runtime.HttpResponse;
import eu.cloudhopper.mc.provider.http.LocalHandlerContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for exposing a Cloudhopper function as a Spring Boot HTTP endpoint.
 * <p>
 * Generated Spring controllers should extend this class and implement
 * the {@link #handle(Object)} method by delegating to the actual function logic.
 *
 * @param <I> input type
 * @param <O> output type
 */
public abstract class SpringBootRequestHandler<I, O> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(SpringBootRequestHandler.class.getName());

    private final CloudRequestHandler<I, O> handler;

    protected SpringBootRequestHandler(CloudRequestHandler<I, O> handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    /**
     * Handles the HTTP POST request with JSON body, invoking the CloudRequestHandler.
     *
     * @param input input deserialized from the HTTP request body
     * @return HTTP response with serialized output
     */
    public ResponseEntity<?> handle(I input) {
        return handle(input, Map.of(), Map.of(), Map.of());
    }

    /**
     * Handles the HTTP request, passing path, query and header parameters on to the function.
     * The answer follows {@link HttpReply}: the result as JSON with status 200, an
     * {@link HttpResponse} with its status and headers, the status of an
     * {@link eu.cloudhopper.mc.runtime.HttpStatusException}, or 500 without details.
     *
     * @param input       input deserialized from the HTTP request body, or {@code null}
     * @param pathParams  path variables of the matched route
     * @param queryParams query parameters (first value of each)
     * @param headers     request headers (first value of each), matched case-insensitively
     * @return HTTP response with serialized output
     */
    public ResponseEntity<?> handle(I input, Map<String, String> pathParams, Map<String, String> queryParams,
                                    Map<String, String> headers) {
        O output;
        try {
            output = handler.handleRequest(input, pathParams, queryParams, Headers.of(headers),
                    new LocalHandlerContext());
        } catch (RuntimeException e) {
            HttpReply reply = HttpReply.of(e);
            if (reply.isUnexpected()) {
                LOG.log(Level.SEVERE, "Function failed", e);
            }
            return toEntity(reply);
        }
        if (output instanceof HttpResponse<?> response) {
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatus());
            response.getHeaders().forEach((name, value) -> builder.header(name, value));
            Object body = response.getBody();
            if (body == null) {
                return builder.build();
            }
            String contentType = response.getHeaders().get(HttpReply.CONTENT_TYPE);
            if (contentType == null) {
                builder.contentType(MediaType.APPLICATION_JSON);
                return builder.body(body instanceof CharSequence text ? toJson(text) : body);
            }
            if (HttpReply.isJson(contentType)) {
                return builder.body(body instanceof CharSequence text ? toJson(text) : body);
            }
            // another media type: text as it is, anything else as JSON, as on the other targets
            return builder.body(body instanceof CharSequence text ? text.toString() : toJson(body));
        }
        if (output instanceof CharSequence text) {
            // Spring would write a String as plain text; every other target answers with JSON
            // ("pong" becomes "\"pong\""), so a function behaves the same on all of them
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(toJson(text));
        }
        return ResponseEntity.ok(output);
    }

    /**
     * Answers a request body that cannot be read as the input type with 400, as on the other
     * targets.
     *
     * @param e the exception of the message converter
     * @return the response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> invalidBody(HttpMessageNotReadableException e) {
        return toEntity(HttpReply.invalidBody());
    }

    private static ResponseEntity<String> toEntity(HttpReply reply) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(reply.status());
        reply.headers().forEach((name, value) -> builder.header(name, value));
        return builder.body(reply.body());
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value instanceof CharSequence text ? text.toString() : value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize the result as JSON", e);
        }
    }
}
