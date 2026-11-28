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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.HttpResponse;
import eu.cloudhopper.mc.runtime.HttpStatusException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiGatewayV2HttpEventHandlerTest {

    public static class Order {
        public int id;
    }

    private final List<String> log = new ArrayList<>();

    private APIGatewayV2HTTPResponse call(CloudRequestHandler<Order, Object> function, String body) {
        ApiGatewayV2HttpEventHandler<Order, Object> handler = new ApiGatewayV2HttpEventHandler<>(function, Order.class) { };
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                log.add(message);
            }

            @Override
            public void log(byte[] message) {
                log.add(new String(message, StandardCharsets.UTF_8));
            }
        };
        Context context = (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[] {Context.class},
                (proxy, method, args) -> "getLogger".equals(method.getName()) ? logger : null);
        return handler.handleRequest(event, context);
    }

    @Test
    void resultIsJsonWithStatus200() {
        APIGatewayV2HTTPResponse response = call((input, path, query, context) -> Map.of("id", input.id), "{\"id\":7}");
        assertEquals(200, response.getStatusCode());
        assertEquals("{\"id\":7}", response.getBody());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
    }

    @Test
    void responseSetsStatusAndHeaders() {
        APIGatewayV2HTTPResponse response = call((input, path, query, context) ->
                HttpResponse.status(201, "x").header("Location", "/orders/7"), null);
        assertEquals(201, response.getStatusCode());
        assertEquals("/orders/7", response.getHeaders().get("Location"));
        assertEquals("\"x\"", response.getBody());
        APIGatewayV2HTTPResponse empty = call((input, path, query, context) -> HttpResponse.noContent(), null);
        assertEquals(204, empty.getStatusCode());
        assertNull(empty.getBody());
    }

    @Test
    void anErrorOfTheFunctionAnswers500() {
        // in a native image, missing reachability metadata is an Error, not an exception
        APIGatewayV2HTTPResponse response = call((input, path, query, context) -> {
            throw new LinkageError("Cannot reflectively access com.example.Item[]");
        }, null);
        assertEquals(500, response.getStatusCode());
        assertEquals("{\"error\":\"Internal Server Error\"}", response.getBody());
        assertEquals(1, log.size());
    }

    @Test
    void statusExceptionSetsStatus() {
        APIGatewayV2HTTPResponse response = call((input, path, query, context) -> {
            throw new HttpStatusException(404, "no order");
        }, null);
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"no order\"}", response.getBody());
        assertEquals(List.of(), log);
    }

    @Test
    void otherExceptionsAnswer500WithoutDetailsAndAreLogged() {
        APIGatewayV2HTTPResponse response = call((input, path, query, context) -> {
            throw new IllegalStateException("db password wrong");
        }, null);
        assertEquals(500, response.getStatusCode());
        assertFalse(response.getBody().contains("password"));
        assertEquals(1, log.size());
    }

    @Test
    void invalidBodyAnswers400() {
        APIGatewayV2HTTPResponse response = call((input, path, query, context) -> "never", "{not json");
        assertEquals(400, response.getStatusCode());
        assertEquals("{\"error\":\"Invalid request body\"}", response.getBody());
    }
}
