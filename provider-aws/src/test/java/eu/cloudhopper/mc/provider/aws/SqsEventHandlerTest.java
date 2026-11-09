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
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.runtime.HandlerContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqsEventHandlerTest {

    public static class Order {
        public String item;
        public int quantity;
    }

    private final List<String> received = new ArrayList<>();
    private final List<EventMetadata> metadata = new ArrayList<>();

    private final CloudRequestHandler<Order, Void> handler = new CloudRequestHandler<>() {
        @Override
        public Void handleRequest(Order order, Map<String, String> path, Map<String, String> query, HandlerContext ctx) {
            if (order.quantity < 0) {
                throw new IllegalArgumentException("negative quantity");
            }
            received.add(order.item + "x" + order.quantity);
            metadata.add(ctx.getEventMetadata());
            return null;
        }
    };

    private static Map<String, Object> record(String id, String body) {
        return Map.of(
                "messageId", id,
                "body", body,
                "eventSource", "aws:sqs",
                "eventSourceARN", "arn:aws:sqs:eu-central-1:123:orders",
                "attributes", Map.of("SentTimestamp", "1700000000000", "ApproximateReceiveCount", "2"),
                "messageAttributes", Map.of("tenant", Map.of("stringValue", "acme", "dataType", "String")));
    }

    @Test
    void recognizesSqsEvents() {
        assertTrue(SqsEventHandler.isSqsEvent(Map.of("Records", List.of(record("1", "{}")))));
        assertFalse(SqsEventHandler.isSqsEvent(Map.of("Records", List.of(Map.of("eventSource", "aws:s3")))));
        assertFalse(SqsEventHandler.isSqsEvent(Map.of("source", "aws.events")));
        assertFalse(SqsEventHandler.isSqsEvent(Map.of("Records", List.of())));
    }

    @Test
    void invokesOncePerMessageAndReportsOnlyTheFailedOnes() {
        Map<String, Object> event = Map.of("Records", List.of(
                record("m1", "{\"item\":\"book\",\"quantity\":2}"),
                record("m2", "{\"item\":\"pen\",\"quantity\":-1}"),
                record("m3", "not json"),
                record("m4", "{\"item\":\"cup\",\"quantity\":1}")));

        Map<String, Object> response = new SqsEventHandler<>(handler, Order.class).handle(event, null);

        assertEquals(List.of("bookx2", "cupx1"), received);
        assertEquals(List.of(Map.of("itemIdentifier", "m2"), Map.of("itemIdentifier", "m3")),
                response.get("batchItemFailures"));
    }

    @Test
    void passesTheMessageMetadata() {
        new SqsEventHandler<>(handler, Order.class)
                .handle(Map.of("Records", List.of(record("m1", "{\"item\":\"book\",\"quantity\":1}"))), null);

        EventMetadata meta = metadata.get(0);
        assertEquals("m1", meta.getId());
        assertEquals("arn:aws:sqs:eu-central-1:123:orders", meta.getSource());
        assertEquals(Instant.ofEpochMilli(1700000000000L), meta.getTime());
        assertEquals(2, meta.getDeliveryAttempt());
        assertEquals(Map.of("tenant", "acme"), meta.getAttributes());
    }
}
