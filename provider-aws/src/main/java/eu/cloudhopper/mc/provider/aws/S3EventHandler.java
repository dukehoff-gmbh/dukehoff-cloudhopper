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
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.EventHandlerContext;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.runtime.StorageEvent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Handles an S3 event notification by invoking the function once per record with a
 * {@link StorageEvent}.
 * <p>
 * S3 invokes the function asynchronously: if the function throws, the exception propagates and
 * Lambda retries the invocation (twice by default).
 *
 * @param <I> the input type ({@link StorageEvent}, or {@link Void} if the function takes none)
 * @param <O> the output type (ignored for events)
 */
public class S3EventHandler<I, O> {

    private final CloudRequestHandler<I, O> handler;
    private final Class<I> inputType;
    private final String namePrefix;

    /**
     * @param handler   the cloud-neutral handler
     * @param inputType {@link StorageEvent} or {@link Void}
     */
    public S3EventHandler(CloudRequestHandler<I, O> handler, Class<I> inputType) {
        this.handler = handler;
        this.inputType = inputType;
        String prefix = System.getenv("CLOUDHOPPER_NAME_PREFIX");
        this.namePrefix = prefix == null ? "" : prefix;
    }

    /**
     * Checks whether a raw Lambda input is an S3 event notification.
     *
     * @param event the input as deserialized by the Lambda runtime
     * @return {@code true} if all records come from S3
     */
    public static boolean isS3Event(Map<?, ?> event) {
        Object records = event.get("Records");
        if (!(records instanceof List) || ((List<?>) records).isEmpty()) {
            return false;
        }
        for (Object r : (List<?>) records) {
            if (!(r instanceof Map) || !"aws:s3".equals(((Map<?, ?>) r).get("eventSource"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invokes the function for each record of the event.
     *
     * @param event   the S3 event as deserialized by the Lambda runtime
     * @param context the Lambda context
     * @return {@code null}
     */
    @SuppressWarnings("unchecked")
    public Object handle(Map<?, ?> event, Context context) {
        for (Object r : (List<?>) event.get("Records")) {
            Map<?, ?> record = (Map<?, ?>) r;
            Map<?, ?> s3 = map(record.get("s3"));
            Map<?, ?> bucket = map(s3.get("bucket"));
            Map<?, ?> object = map(s3.get("object"));

            String bucketName = String.valueOf(bucket.get("name"));
            // keys arrive URL-encoded, with '+' for spaces
            String key = URLDecoder.decode(String.valueOf(object.get("key")), StandardCharsets.UTF_8);
            long size = object.get("size") instanceof Number ? ((Number) object.get("size")).longValue() : -1;
            StorageEvent storageEvent = new StorageEvent(stripPrefix(bucketName), key, size, StorageEvent.Kind.CREATED);

            Instant time = record.get("eventTime") == null ? null : Instant.parse(record.get("eventTime").toString());
            String id = object.get("sequencer") != null ? bucketName + "/" + key + "/" + object.get("sequencer") : null;
            EventMetadata meta = new EventMetadata(id, String.valueOf(bucket.get("arn")), time, 0,
                    Map.of("eventName", String.valueOf(record.get("eventName"))));

            I input = inputType == StorageEvent.class ? (I) storageEvent : null;
            handler.handleRequest(input, new EventHandlerContext(new AwsContextAdapter(context), meta));
        }
        return null;
    }

    private String stripPrefix(String bucketName) {
        return !namePrefix.isEmpty() && bucketName.startsWith(namePrefix)
                ? bucketName.substring(namePrefix.length())
                : bucketName;
    }

    private static Map<?, ?> map(Object o) {
        return o instanceof Map ? (Map<?, ?>) o : Map.of();
    }
}
