package eu.cloudhopper.mc.provider.aws;

import eu.cloudhopper.mc.runtime.Headers;
import eu.cloudhopper.mc.runtime.HttpReply;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

/**
 * AWS API Gateway V2 (HTTP API) request handler for Cloudhopper functions.
 *
 * @param <I> input type (from JSON body)
 * @param <O> output type (to JSON body)
 */
public abstract class ApiGatewayV2ProxyRequestHandler<I, O> implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final CloudRequestHandler<I, O> handler;
    private final Class<I> inputClass;

    protected ApiGatewayV2ProxyRequestHandler(CloudRequestHandler<I, O> handler, Class<I> inputClass) {
        this.handler = handler;
        this.inputClass = inputClass;
    }

    @Override
    public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent event, Context context) {
        HttpReply reply;
        I input;
        try {
            String body = event.getBody();
            if (event.isIsBase64Encoded() && body != null) {
                body = new String(java.util.Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            }
            input = (body != null && !body.isBlank()) ? mapper.readValue(body, inputClass) : null;
        } catch (LinkageError e) {
            return toResponse(failure(e, context));
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
                    new AwsContextAdapter(context));
            reply = HttpReply.of(output, this::json);
        } catch (Exception | LinkageError e) {
            // a LinkageError: e.g. missing reachability metadata in a native image
            reply = failure(e, context);
        }
        return toResponse(reply);
    }

    private static HttpReply failure(Throwable e, Context context) {
        HttpReply reply = HttpReply.of(e);
        if (reply.isUnexpected()) {
            String hint = HttpReply.nativeImageHint(e);
            context.getLogger().log((hint != null ? hint + "\n" : "") + "Handler error: "
                    + ApiGatewayV2HttpEventHandler.stackTrace(e));
        }
        return reply;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the result as JSON", e);
        }
    }

    private static APIGatewayV2ProxyResponseEvent toResponse(HttpReply reply) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.putAll(reply.headers());
        APIGatewayV2ProxyResponseEvent response = new APIGatewayV2ProxyResponseEvent();
        response.setStatusCode(reply.status());
        response.setHeaders(headers);
        response.setBody(reply.body());
        return response;
    }
}
