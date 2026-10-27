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
import eu.cloudhopper.mc.provider.http.LocalHandlerContext;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

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
    public ResponseEntity<O> handle(I input) {
        O output = handler.handleRequest(input, new LocalHandlerContext());
        return ResponseEntity.ok(output);
    }
}
