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
 * Routes Lambda invocations to the appropriate handler based on input type: API Gateway
 * events, SQS and S3 events, schedules and direct calls.
 */
public class ApiGatewayEventRouter<I, O> implements RequestHandler<Object, Object> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestHandler<I, O> plain;
    private final RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy;
    private final RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http;
    private final SqsEventHandler<I, O> sqs;
    private final S3EventHandler<I, O> s3;
    private final Class<I> inputType;

    public ApiGatewayEventRouter(
            RequestHandler<I, O> plain,
            RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy,
            RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http
    ) {
        this(plain, proxy, http, null);
    }

    /**
     * @param sqs handles SQS events (queue triggers); {@code null} if the function has none
     */
    public ApiGatewayEventRouter(
            RequestHandler<I, O> plain,
            RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy,
            RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http,
            SqsEventHandler<I, O> sqs
    ) {
        this(plain, proxy, http, sqs, null);
    }

    /**
     * @param sqs handles SQS events (queue triggers); {@code null} if the function has none
     * @param s3  handles S3 events (storage triggers); {@code null} if the function has none
     */
    public ApiGatewayEventRouter(
            RequestHandler<I, O> plain,
            RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy,
            RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http,
            SqsEventHandler<I, O> sqs,
            S3EventHandler<I, O> s3
    ) {
        this(plain, proxy, http, sqs, s3, null);
    }

    /**
     * @param sqs       handles SQS events (queue triggers); {@code null} if the function has none
     * @param s3        handles S3 events (storage triggers); {@code null} if the function has none
     * @param inputType the function's input type: a direct invocation with a JSON object is
     *                  converted to it; {@code null} passes no input
     */
    public ApiGatewayEventRouter(
            RequestHandler<I, O> plain,
            RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> proxy,
            RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> http,
            SqsEventHandler<I, O> sqs,
            S3EventHandler<I, O> s3,
            Class<I> inputType
    ) {
        this.plain = plain;
        this.proxy = proxy;
        this.http = http;
        this.sqs = sqs;
        this.s3 = s3;
        this.inputType = inputType;
        if (SnapStartPriming.ACTIVE) {
            SnapStartPriming.register(this);
        }
    }

    /**
     * Runs the path of an API Gateway request without calling the function: the conversion of
     * the event, and {@link ApiGatewayV2HttpEventHandler#prime()}.
     */
    void prime() {
        Map<String, Object> http = Map.of("method", "GET", "path", "/", "protocol", "HTTP/1.1",
                "sourceIp", "127.0.0.1", "userAgent", "cloudhopper-priming");
        Map<String, Object> event = Map.of("version", "2.0", "routeKey", "GET /", "rawPath", "/",
                "rawQueryString", "", "headers", Map.of("content-type", "application/json"),
                "requestContext", Map.of("http", http, "requestId", "priming", "routeKey", "GET /",
                        "stage", "$default"),
                "body", "{}", "isBase64Encoded", false);
        objectMapper.convertValue(event, APIGatewayV2HTTPEvent.class);
        if (this.http instanceof ApiGatewayV2HttpEventHandler<?, ?> handler) {
            handler.prime();
        }
        // the function's own dependencies (@OnPrime)
        if (plain instanceof AwsLambdaRequestHandler<?, ?> function) {
            try {
                function.prime();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("@OnPrime failed: " + e.getMessage(), e);
            }
        }
    }

    /** The payload of a direct invocation as the function's input, or {@code null} if it takes none. */
    private I directInput(Map<?, ?> payload) {
        if (inputType == null || inputType == Void.class || payload.isEmpty() && !Map.class.isAssignableFrom(inputType)) {
            return null;
        }
        return objectMapper.convertValue(payload, inputType);
    }

    @Override
    public Object handleRequest(Object input, Context context) {
        if (input instanceof APIGatewayV2ProxyRequestEvent) {
            return proxy.handleRequest((APIGatewayV2ProxyRequestEvent) input, context);
        } else if (input instanceof APIGatewayV2HTTPEvent) {
            return http.handleRequest((APIGatewayV2HTTPEvent) input, context);
        } else if (input instanceof Map) {
            // Try to detect based on available fields
            Map<?, ?> map = (Map<?, ?>) input;

            if (sqs != null && SqsEventHandler.isSqsEvent(map)) {
                return sqs.handle(map, context);
            }
            if (s3 != null && S3EventHandler.isS3Event(map)) {
                return s3.handle(map, context);
            }

            if (map.containsKey("version") && map.containsKey("routeKey") && map.containsKey("rawPath")) {
                // Looks like an APIGatewayV2HTTPEvent
                APIGatewayV2HTTPEvent event = objectMapper.convertValue(map, APIGatewayV2HTTPEvent.class);
                return http.handleRequest(event, context);
            }

            if (map.containsKey("source") && "aws.events".equals(map.get("source"))) {
                // Scheduled event
                return plain.handleRequest(null, context);
            }
            // a direct invocation: the payload is the function's input
            return plain.handleRequest(directInput(map), context);
        }

        // fallback
        return plain.handleRequest((I) input, context);
    }
}
