package eu.cloudhopper.mc.provider.aws;

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

import eu.cloudhopper.mc.runtime.Headers;
import eu.cloudhopper.mc.runtime.HttpReply;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for AWS Lambda functions handling APIGatewayV2HTTPEvent from API Gateway v2 (HTTP APIs).
 *
 * @param <I> the input type (JSON body)
 * @param <O> the output type (JSON response)
 */
public abstract class ApiGatewayV2HttpEventHandler<I, O> implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final CloudRequestHandler<I, O> handler;
    private final Class<I> inputClass;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected ApiGatewayV2HttpEventHandler(CloudRequestHandler<I, O> handler, Class<I> inputClass) {
        this.handler = handler;
        this.inputClass = inputClass;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context awsContext) {
        HttpReply reply;
        I input;
        try {
            String body = event.getBody();
            if (event.getIsBase64Encoded() && body != null) {
                body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            }
            input = (body != null && !body.isBlank()) ? objectMapper.readValue(body, inputClass) : null;
        } catch (Exception e) {
            return toResponse(HttpReply.invalidBody());
        }
        try {
            Map<String, String> pathParams = event.getPathParameters() != null
                    ? event.getPathParameters()
                    : Collections.emptyMap();
            Map<String, String> queryParams = event.getQueryStringParameters() != null
                    ? event.getQueryStringParameters()
                    : Collections.emptyMap();
            O output = handler.handleRequest(input, pathParams, queryParams, Headers.of(event.getHeaders()),
                    new AwsContextAdapter(awsContext));
            reply = HttpReply.of(output, this::json);
        } catch (Exception e) {
            reply = HttpReply.of(e);
            if (reply.isUnexpected()) {
                awsContext.getLogger().log("Handler error: " + stackTrace(e));
            }
        }
        return toResponse(reply);
    }

    /**
     * Runs the path of a request without the function, so that a SnapStart snapshot contains it
     * loaded and initialized: Jackson for the body type (from an empty object, if that is valid
     * for it) and for JSON results, and the responses of a result, an error and an invalid body.
     */
    void prime() {
        Object body = null;
        try {
            body = objectMapper.readValue("{}", inputClass);
        } catch (Exception e) {
            // not an object type, or one that needs properties: the reader is initialized anyway
        }
        toResponse(HttpReply.of(body != null ? body : Map.of("primed", true), this::json));
        toResponse(HttpReply.of(new IllegalStateException("priming")));
        toResponse(HttpReply.invalidBody());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the result as JSON", e);
        }
    }

    private static APIGatewayV2HTTPResponse toResponse(HttpReply reply) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.putAll(reply.headers());
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(reply.status());
        response.setHeaders(headers);
        response.setBody(reply.body());
        return response;
    }

    static String stackTrace(Throwable e) {
        java.io.StringWriter text = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }
}
