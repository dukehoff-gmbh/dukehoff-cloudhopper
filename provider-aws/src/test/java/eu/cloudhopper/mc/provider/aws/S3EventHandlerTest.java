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
import eu.cloudhopper.mc.runtime.StorageEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S3EventHandlerTest {

    private final List<StorageEvent> received = new ArrayList<>();
    private final List<EventMetadata> metadata = new ArrayList<>();

    private final CloudRequestHandler<StorageEvent, Void> handler = new CloudRequestHandler<>() {
        @Override
        public Void handleRequest(StorageEvent event, Map<String, String> path, Map<String, String> query, HandlerContext ctx) {
            received.add(event);
            metadata.add(ctx.getEventMetadata());
            return null;
        }
    };

    private static Map<String, Object> record(String bucket, String key, long size) {
        return Map.of(
                "eventSource", "aws:s3",
                "eventName", "ObjectCreated:Put",
                "eventTime", "2026-11-10T12:00:00.000Z",
                "s3", Map.of(
                        "bucket", Map.of("name", bucket, "arn", "arn:aws:s3:::" + bucket),
                        "object", Map.of("key", key, "size", size, "sequencer", "0055AED6DCD90281E5")));
    }

    @Test
    void recognizesS3Events() {
        assertTrue(S3EventHandler.isS3Event(Map.of("Records", List.of(record("b", "k", 1)))));
        assertFalse(S3EventHandler.isS3Event(Map.of("Records", List.of(Map.of("eventSource", "aws:sqs")))));
        assertFalse(S3EventHandler.isS3Event(Map.of("source", "aws.events")));
    }

    @Test
    void passesAStorageEventWithTheDecodedKey() {
        new S3EventHandler<>(handler, StorageEvent.class)
                .handle(Map.of("Records", List.of(record("invoices", "2026/my+invoice%C3%A4.pdf", 42))), null);

        assertEquals(List.of(new StorageEvent("invoices", "2026/my invoiceä.pdf", 42, StorageEvent.Kind.CREATED)), received);
        EventMetadata meta = metadata.get(0);
        assertEquals("arn:aws:s3:::invoices", meta.getSource());
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), meta.getTime());
        assertEquals("ObjectCreated:Put", meta.getAttributes().get("eventName"));
    }
}
