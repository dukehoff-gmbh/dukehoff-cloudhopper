package eu.cloudhopper.mc.runtime;

import java.util.Collections;
import java.util.Map;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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

/**
 * Handler interface for user-defined cloud functions.
 *
 * <p>This interface defines the contract for processing function invocations. It is used by the
 * runtime to delegate the execution of a function with deserialized input and request context.</p>
 *
 * <p><strong>Note:</strong> Implementing this interface manually is no longer required in most cases.
 * If a method is annotated with {@link eu.cloudhopper.mc.annotations.Function}, a corresponding
 * implementation of this interface will be generated automatically during annotation processing.</p>
 *
 * <p>Advanced users may still implement this interface directly to provide custom invocation logic
 * or to bypass the code generation step.</p>
 *
 * @param <I> the input type (e.g., deserialized JSON request body)
 * @param <O> the output type (e.g., response body)
 *
 * @see eu.cloudhopper.mc.annotations.Function
 * @see HandlerContext
 */
public interface CloudRequestHandler<I, O> {

    /**
     * Handles a simplified function invocation with only input and context.
     *
     * <p>This default method delegates to the full handler method with empty path and query parameter maps.</p>
     *
     * @param input   the deserialized input object
     * @param context runtime context information about the invocation
     * @return the response object
     */
    default O handleRequest(I input, HandlerContext context) {
        return handleRequest(input, Collections.emptyMap(), Collections.emptyMap(), context);
    }

    /**
     * Handles an incoming request with full request metadata.
     *
     * <p>This method provides access to HTTP path and query parameters in addition to the input and context.
     * It is typically invoked by the generated infrastructure code.</p>
     *
     * @param input       the deserialized input object (e.g., request body)
     * @param pathParams  map of path parameters extracted from the request URL
     * @param queryParams map of query parameters
     * @param context     runtime context information about the invocation
     * @return the response object
     */
    O handleRequest(I input, Map<String, String> pathParams, Map<String, String> queryParams, HandlerContext context);
}