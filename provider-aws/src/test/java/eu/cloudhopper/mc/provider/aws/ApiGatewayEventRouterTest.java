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

import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ApiGatewayEventRouterTest {

    public static class Order {
        public int id;
        public String item;
    }

    @Test
    public void directInvocationPassesThePayloadAsInput() {
        List<Order> received = new ArrayList<>();
        RequestHandler<Order, String> plain = (order, context) -> {
            received.add(order);
            return "ok";
        };
        ApiGatewayEventRouter<Order, String> router = new ApiGatewayEventRouter<>(plain, null, null, null, null, Order.class);

        assertEquals("ok", router.handleRequest(Map.of("id", 7, "item", "book"), null));
        assertEquals(7, received.get(0).id);
        assertEquals("book", received.get(0).item);
    }

    @Test
    public void scheduledEventsAndEmptyPayloadsPassNoInput() {
        List<Order> received = new ArrayList<>();
        RequestHandler<Order, String> plain = (order, context) -> {
            received.add(order);
            return "ok";
        };
        ApiGatewayEventRouter<Order, String> router = new ApiGatewayEventRouter<>(plain, null, null, null, null, Order.class);

        router.handleRequest(Map.of("source", "aws.events", "detail-type", "Scheduled Event"), null);
        router.handleRequest(Map.of(), null);
        assertNull(received.get(0));
        assertNull(received.get(1));
    }

    @Test
    public void withoutInputTypeNoInputIsPassed() {
        List<Object> received = new ArrayList<>();
        RequestHandler<Object, String> plain = (input, context) -> {
            received.add(input);
            return "ok";
        };
        new ApiGatewayEventRouter<>(plain, null, null).handleRequest(Map.of("id", 7), null);
        assertNull(received.get(0));
    }
}
