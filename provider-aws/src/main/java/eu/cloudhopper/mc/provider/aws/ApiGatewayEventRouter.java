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
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Routes Lambda invocations to the appropriate handler based on input type.
 */
public class ApiGatewayEventRouter<I, O> implements RequestHandler<Object, Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestHandler<I, O> plain;
    private final RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy;
    private final RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http;

    public ApiGatewayEventRouter(
            RequestHandler<I, O> plain,
            RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy,
            RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http
    ) {
        this.plain = plain;
        this.proxy = proxy;
        this.http = http;
    }

    @Override
    public Object handleRequest(Object input, Context context) {
        System.out.println("input = " + input);

        if (input instanceof APIGatewayV2ProxyRequestEvent) {
            return proxy.handleRequest((APIGatewayV2ProxyRequestEvent) input, context);
        } else if (input instanceof APIGatewayV2HTTPEvent) {
            return http.handleRequest((APIGatewayV2HTTPEvent) input, context);
        } else if (input instanceof Map) {
            // Try to detect based on available fields
            Map<?, ?> map = (Map<?, ?>) input;

            if (map.containsKey("version") && map.containsKey("routeKey") && map.containsKey("rawPath")) {
                // Looks like an APIGatewayV2HTTPEvent
                APIGatewayV2HTTPEvent event = objectMapper.convertValue(map, APIGatewayV2HTTPEvent.class);
                return http.handleRequest(event, context);
            }

            if (map.containsKey("source") && "aws.events".equals(map.get("source"))) {
                // Scheduled event
                return plain.handleRequest(null, context);
            }
            // simple call
            return plain.handleRequest(null, context);
        }

        // fallback
        return plain.handleRequest((I) input, context);
    }
}
