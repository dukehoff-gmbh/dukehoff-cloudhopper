package eu.cloudhopper.mc.provider.gcp;

/*-
 * #%L
 * provider-gcp - a library from the "Cloudhopper" project.
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
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.HandlerContext;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for GCP HTTP-triggered functions that delegate execution
 * to a cloud-neutral {@link CloudRequestHandler}.
 * <p>
 * This wrapper handles JSON input parsing, output serialization, and context
 * adaptation for Google Cloud Functions.
 *
 * @param <I> the input type
 * @param <O> the output type
 */
public abstract class GcpCloudFunctionRequestHandler<I, O> implements HttpFunction {

    private final CloudRequestHandler<I, O> handler;
    private final Type inputType;
    private final Gson gson = new Gson();

    /**
     * Constructs a new request handler for GCP Cloud Functions.
     *
     * @param handler the Cloudhopper handler that contains the actual logic
     * @param typeToken the input type token used for generic deserialization
     */
    protected GcpCloudFunctionRequestHandler(CloudRequestHandler<I, O> handler, TypeToken<I> typeToken) {
        this.handler = handler;
        this.inputType = typeToken.getType();
    }

    /**
     * Entry point for the HTTP function. Parses the input, delegates to the
     * handler, and writes the JSON output.
     *
     * @param request the incoming HTTP request
     * @param response the HTTP response to be written
     * @throws IOException if parsing or writing fails
     */
    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        I input = parseInput(request);

        Map<String, String> queryParams = new HashMap<>();
        request.getQueryParameters().forEach((key, valueList) -> {
            if (valueList != null && !valueList.isEmpty()) {
                queryParams.put(key, valueList.get(0));
            }
        });

        Map<String, String> pathParams = Collections.emptyMap();
        String routePattern = getRoutePattern();
        
        if (routePattern != null && !routePattern.isBlank()) {
            pathParams = extractPathParams(request.getPath(), routePattern);
        }

        O output = handler.handleRequest(input, pathParams, queryParams, new GcpContextAdapter(request));
        writeOutput(response, output);
    }

    /**
     * Parses the incoming request body as JSON into the expected input type.
     *
     * @param request the incoming HTTP request
     * @return the deserialized input
     * @throws IOException if the request body is invalid
     */
    private I parseInput(HttpRequest request) throws IOException {
        String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
        return gson.fromJson(body, inputType);
    }

    /**
     * Writes the function output as a JSON response.
     *
     * @param response the HTTP response
     * @param output the output object to serialize
     * @throws IOException if writing fails
     */
    private void writeOutput(HttpResponse response, O output) throws IOException {
        response.setContentType("application/json");
        BufferedWriter writer = response.getWriter();
        writer.write(gson.toJson(output));
    }

    private Map<String, String> extractPathParams(String actualPath, String routePattern) {
        System.out.println("Extract path params from " + actualPath);
        System.out.println("routePattern " + routePattern);
        String[] actualParts = actualPath.replaceAll("^/+", "").split("/");
        String[] routeParts = routePattern.replaceAll("^/+", "").split("/");

        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < Math.min(routeParts.length, actualParts.length); i++) {
            if (routeParts[i].startsWith("{") && routeParts[i].endsWith("}")) {
                String name = routeParts[i].substring(1, routeParts[i].length() - 1);
                pathParams.put(name, actualParts[i]);
            }
        }
        return pathParams;
    }

    /**
     * Should return the route pattern (e.g., "/hello/{id}").This is used to
     * extract path parameters from the incoming URL.
     *
     * @return the route pattern
     */
    protected abstract String getRoutePattern();

    /**
     * Adapter that maps the GCP {@link HttpRequest} to Cloudhopper’s
     * {@link HandlerContext}.
     */
    private static class GcpContextAdapter implements HandlerContext {

        private final HttpRequest request;

        /**
         * Creates a new context adapter.
         *
         * @param request the GCP HTTP request
         */
        public GcpContextAdapter(HttpRequest request) {
            this.request = request;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getRequestId() {
            return request.getHeaders().get("X-Cloud-Trace-Context").get(0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionName() {
            return System.getenv("K_SERVICE");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionVersion() {
            return System.getenv("K_REVISION");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getInvokedFunctionArn() {
            return null; // Not available on GCP
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getLogGroupName() {
            return null; // Not available on GCP
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getLogStreamName() {
            return null; // Not available on GCP
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getRemainingTimeInMillis() {
           String mem = System.getenv("FUNCTION_TIMEOUT_SECONDS");
            if (mem == null || mem.isBlank()) {
                return -1; // or a default, or throw a meaningful exception
            }
            return Integer.parseInt(mem)*1000;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getMemoryLimitInMB() {
            String mem = System.getenv("FUNCTION_MEMORY_MB");
            if (mem == null || mem.isBlank()) {
                return -1; // or a default, or throw a meaningful exception
            }
            return Integer.parseInt(mem);
        }
    }
}
