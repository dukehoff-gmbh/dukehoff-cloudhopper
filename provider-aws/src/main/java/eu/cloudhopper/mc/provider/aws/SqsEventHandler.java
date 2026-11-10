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
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.EventHandlerContext;
import eu.cloudhopper.mc.runtime.EventMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles an SQS event (a batch of records) by invoking the function once per message.
 * <p>
 * Messages whose invocation throws are reported back as batch item failures, so SQS delivers
 * only those again. This requires {@code function_response_types = ["ReportBatchItemFailures"]}
 * on the event source mapping, which the generated Terraform sets.
 *
 * @param <I> the input type the message body is deserialized into
 * @param <O> the output type (ignored for events)
 */
public class SqsEventHandler<I, O> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CloudRequestHandler<I, O> handler;
    private final Class<I> inputType;

    /**
     * @param handler   the cloud-neutral handler
     * @param inputType the class the message body is deserialized into
     */
    public SqsEventHandler(CloudRequestHandler<I, O> handler, Class<I> inputType) {
        this.handler = handler;
        this.inputType = inputType;
    }

    /**
     * Checks whether a raw Lambda input is an SQS event.
     *
     * @param event the input as deserialized by the Lambda runtime
     * @return {@code true} if all records come from SQS
     */
    public static boolean isSqsEvent(Map<?, ?> event) {
        Object records = event.get("Records");
        if (!(records instanceof List) || ((List<?>) records).isEmpty()) {
            return false;
        }
        for (Object r : (List<?>) records) {
            if (!(r instanceof Map) || !"aws:sqs".equals(((Map<?, ?>) r).get("eventSource"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invokes the function for each record of the event.
     *
     * @param event   the SQS event as deserialized by the Lambda runtime
     * @param context the Lambda context
     * @return the partial batch response: {@code {"batchItemFailures": [{"itemIdentifier": id}, ...]}}
     */
    public Map<String, Object> handle(Map<?, ?> event, Context context) {
        List<Map<String, String>> failures = new ArrayList<>();
        for (Object r : (List<?>) event.get("Records")) {
            Map<?, ?> record = (Map<?, ?>) r;
            String messageId = String.valueOf(record.get("messageId"));
            try {
                I input = parseBody((String) record.get("body"));
                EventMetadata meta = metadata(record);
                handler.handleRequest(input, new EventHandlerContext(new AwsContextAdapter(context), meta));
            } catch (Exception e) {
                System.err.println("Message " + messageId + " failed and will be delivered again: " + e);
                e.printStackTrace();
                failures.add(Map.of("itemIdentifier", messageId));
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("batchItemFailures", failures);
        return response;
    }

    @SuppressWarnings("unchecked")
    private I parseBody(String body) throws Exception {
        if (inputType == null || inputType == Void.class || body == null) {
            return null;
        }
        if (inputType == String.class) {
            return (I) body;
        }
        return objectMapper.readValue(body, inputType);
    }

    private static EventMetadata metadata(Map<?, ?> record) {
        Map<?, ?> attrs = record.get("attributes") instanceof Map ? (Map<?, ?>) record.get("attributes") : Map.of();
        Instant time = null;
        Object sent = attrs.get("SentTimestamp");
        if (sent != null) {
            time = Instant.ofEpochMilli(Long.parseLong(sent.toString()));
        }
        int attempt = 0;
        Object count = attrs.get("ApproximateReceiveCount");
        if (count != null) {
            attempt = Integer.parseInt(count.toString());
        }
        Map<String, String> attributes = new HashMap<>();
        if (record.get("messageAttributes") instanceof Map) {
            ((Map<?, ?>) record.get("messageAttributes")).forEach((k, v) -> {
                if (v instanceof Map && ((Map<?, ?>) v).get("stringValue") != null) {
                    attributes.put(k.toString(), ((Map<?, ?>) v).get("stringValue").toString());
                }
            });
        }
        return new EventMetadata(String.valueOf(record.get("messageId")),
                String.valueOf(record.get("eventSourceARN")), time, attempt, attributes);
    }
}
