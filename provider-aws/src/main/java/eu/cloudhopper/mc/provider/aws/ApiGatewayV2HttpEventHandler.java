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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
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
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Content-Type", "application/json");

        try {
            // Decode and deserialize input
            String body = event.getBody();
            if (event.getIsBase64Encoded() && body != null) {
                body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            }

            I input = (body != null && !body.isBlank())
                    ? objectMapper.readValue(body, inputClass)
                    : null;

            Map<String, String> pathParams = event.getPathParameters() != null
                    ? event.getPathParameters()
                    : Collections.emptyMap();

            Map<String, String> queryParams = event.getQueryStringParameters() != null
                    ? event.getQueryStringParameters()
                    : Collections.emptyMap();

            O output = handler.handleRequest(input, pathParams, queryParams, new AwsContextAdapter(awsContext));

            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(output));
            response.setHeaders(headers);
            return response;

        } catch (Exception e) {
            awsContext.getLogger().log("Handler error: " + e.getMessage());

            APIGatewayV2HTTPResponse error = new APIGatewayV2HTTPResponse();
            error.setStatusCode(500);
            error.setBody("{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}");
            error.setHeaders(headers);
            return error;
        }
    }
}
