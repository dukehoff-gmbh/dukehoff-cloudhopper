package eu.cloudhopper.mc.provider.aws.keyvalue;


/*-
 * #%L
 * provider-aws-keyvalue - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.provider.aws.auth.SigV4Signer;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A DynamoDB table, through the DynamoDB JSON API signed with SigV4. The table has the string
 * partition key {@value #PARTITION} and the string sort key {@value #SORT}; the attributes are
 * stored as DynamoDB attributes of the same names. Reads are strongly consistent. Throttling,
 * server errors and I/O errors are retried with backoff; {@code AccessDeniedException} becomes a
 * {@link KeyValueAccessDeniedException}.
 */
class DynamoKeyValueStore implements KeyValueStore {

    /** The name of the partition key attribute; attribute names of items start with a letter. */
    static final String PARTITION = "_pk";
    /** The name of the sort key attribute. */
    static final String SORT = "_sk";

    private static final int ATTEMPTS = 5;
    private static final int BATCH = 25;
    private static final Set<String> RETRYABLE = Set.of("ProvisionedThroughputExceededException",
            "ThrottlingException", "RequestLimitExceeded", "InternalServerError", "ServiceUnavailable",
            "LimitExceededException", "TransactionConflictException");

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final String region;
    private final String table;
    private final Supplier<AwsCredentials> credentials;
    private final Clock clock;
    private final Duration backoff;

    DynamoKeyValueStore(ObjectMapper mapper, HttpClient http, URI endpoint, String region, String table,
            Supplier<AwsCredentials> credentials, Clock clock, Duration backoff) {
        this.mapper = mapper;
        this.http = http;
        this.endpoint = endpoint;
        this.region = region;
        this.table = table;
        this.credentials = credentials;
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public Optional<Item> get(Key key) {
        ObjectNode request = request();
        request.set("Key", key(key));
        request.put("ConsistentRead", true);
        JsonNode item = call("GetItem", request).path("Item");
        return item.isMissingNode() || item.isEmpty() ? Optional.empty() : Optional.of(item(item));
    }

    @Override
    public void put(Item item) {
        ObjectNode request = request();
        request.set("Item", item(item));
        call("PutItem", request);
    }

    @Override
    public boolean putIfAbsent(Item item) {
        ObjectNode request = request();
        request.set("Item", item(item));
        request.put("ConditionExpression", "attribute_not_exists(#pk)");
        request.putObject("ExpressionAttributeNames").put("#pk", PARTITION);
        return conditional("PutItem", request);
    }

    @Override
    public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
        ObjectNode request = request();
        request.set("Key", key(key));
        ObjectNode names = mapper.createObjectNode().put("#pk", PARTITION);
        ObjectNode values = mapper.createObjectNode();
        List<String> sets = new ArrayList<>();
        List<String> removes = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> e : set.entrySet()) {
            names.put("#s" + i, e.getKey());
            if (e.getValue() == null) {
                removes.add("#s" + i);
            } else {
                values.set(":s" + i, value(e.getValue()));
                sets.add("#s" + i + " = :s" + i);
            }
            i++;
        }
        StringBuilder condition = new StringBuilder("attribute_exists(#pk)");
        i = 0;
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            names.put("#e" + i, e.getKey());
            if (e.getValue() == null) {
                condition.append(" AND attribute_not_exists(#e").append(i).append(')');
            } else {
                values.set(":e" + i, value(e.getValue()));
                condition.append(" AND #e").append(i).append(" = :e").append(i);
            }
            i++;
        }
        StringBuilder expression = new StringBuilder();
        if (!sets.isEmpty()) {
            expression.append("SET ").append(String.join(", ", sets));
        }
        if (!removes.isEmpty()) {
            expression.append(expression.length() == 0 ? "" : " ").append("REMOVE ").append(String.join(", ", removes));
        }
        request.put("UpdateExpression", expression.toString());
        request.put("ConditionExpression", condition.toString());
        request.set("ExpressionAttributeNames", names);
        if (!values.isEmpty()) {
            request.set("ExpressionAttributeValues", values);
        }
        return conditional("UpdateItem", request);
    }

    @Override
    public void delete(Key key) {
        ObjectNode request = request();
        request.set("Key", key(key));
        call("DeleteItem", request);
    }

    @Override
    public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
        ObjectNode request = request();
        request.put("ConsistentRead", true);
        request.put("Limit", limit);
        ObjectNode names = request.putObject("ExpressionAttributeNames").put("#pk", PARTITION);
        ObjectNode values = request.putObject("ExpressionAttributeValues");
        values.putObject(":p").put("S", partition);
        if (sortKeyPrefix.isEmpty()) {
            request.put("KeyConditionExpression", "#pk = :p");
        } else {
            names.put("#sk", SORT);
            values.putObject(":s").put("S", sortKeyPrefix);
            request.put("KeyConditionExpression", "#pk = :p AND begins_with(#sk, :s)");
        }
        if (pageToken != null) {
            try {
                request.set("ExclusiveStartKey", mapper.readTree(Base64.getUrlDecoder().decode(pageToken)));
            } catch (IOException | IllegalArgumentException e) {
                throw new KeyValueStoreException("Invalid page token for table " + table, e);
            }
        }
        JsonNode response = call("Query", request);
        List<Item> items = new ArrayList<>();
        for (JsonNode item : response.path("Items")) {
            items.add(item(item));
        }
        JsonNode last = response.path("LastEvaluatedKey");
        String next = last.isMissingNode() || last.isEmpty() ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(last.toString().getBytes(StandardCharsets.UTF_8));
        return new Page(items, next);
    }

    @Override
    public void batchPut(Collection<Item> items) {
        List<JsonNode> requests = new ArrayList<>();
        for (Item item : items) {
            ObjectNode put = mapper.createObjectNode();
            put.putObject("PutRequest").set("Item", item(item));
            requests.add(put);
        }
        batchWrite(requests);
    }

    @Override
    public void batchDelete(Collection<Key> keys) {
        List<JsonNode> requests = new ArrayList<>();
        for (Key key : keys) {
            ObjectNode delete = mapper.createObjectNode();
            delete.putObject("DeleteRequest").set("Key", key(key));
            requests.add(delete);
        }
        batchWrite(requests);
    }

    /** BatchWriteItem in batches of 25, retrying the unprocessed requests with backoff. */
    private void batchWrite(List<JsonNode> requests) {
        for (int start = 0; start < requests.size(); start += BATCH) {
            List<JsonNode> pending = requests.subList(start, Math.min(start + BATCH, requests.size()));
            for (int attempt = 1; !pending.isEmpty(); attempt++) {
                ObjectNode request = mapper.createObjectNode();
                ArrayNode writes = request.putObject("RequestItems").putArray(table);
                pending.forEach(writes::add);
                JsonNode unprocessed = call("BatchWriteItem", request).path("UnprocessedItems").path(table);
                List<JsonNode> left = new ArrayList<>();
                unprocessed.forEach(left::add);
                if (!left.isEmpty() && attempt >= ATTEMPTS) {
                    throw new KeyValueStoreException("DynamoDB did not process " + left.size()
                            + " writes to table " + table + " after " + ATTEMPTS + " attempts");
                }
                if (!left.isEmpty()) {
                    pause(attempt);
                }
                pending = left;
            }
        }
    }

    private ObjectNode request() {
        return mapper.createObjectNode().put("TableName", table);
    }

    private ObjectNode key(Key key) {
        ObjectNode node = mapper.createObjectNode();
        node.putObject(PARTITION).put("S", key.partition());
        node.putObject(SORT).put("S", key.sort());
        return node;
    }

    private ObjectNode item(Item item) {
        ObjectNode node = key(item.key());
        item.attributes().forEach((name, value) -> node.set(name, value(value)));
        return node;
    }

    private Item item(JsonNode node) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!PARTITION.equals(field.getKey()) && !SORT.equals(field.getKey())) {
                attributes.put(field.getKey(), value(field.getValue()));
            }
        }
        return new Item(new Key(node.path(PARTITION).path("S").asText(), node.path(SORT).path("S").asText()), attributes);
    }

    /** A value as a DynamoDB attribute value. */
    ObjectNode value(Object value) {
        ObjectNode node = mapper.createObjectNode();
        if (value instanceof String s) {
            node.put("S", s);
        } else if (value instanceof Boolean b) {
            node.put("BOOL", b);
        } else if (value instanceof Long || value instanceof Double) {
            node.put("N", value.toString());
        } else if (value instanceof List<?> list) {
            ArrayNode array = node.putArray("L");
            list.forEach(element -> array.add(value(element)));
        } else if (value instanceof Map<?, ?> map) {
            ObjectNode fields = node.putObject("M");
            map.forEach((k, v) -> fields.set((String) k, value(v)));
        } else {
            throw new KeyValueStoreException("Cannot store " + value + " in table " + table);
        }
        return node;
    }

    /** A DynamoDB attribute value as a value; numbers as Long or Double. */
    Object value(JsonNode node) {
        if (node.has("S")) {
            return node.get("S").asText();
        }
        if (node.has("BOOL")) {
            return node.get("BOOL").asBoolean();
        }
        if (node.has("N")) {
            String n = node.get("N").asText();
            try {
                return Long.parseLong(n);
            } catch (NumberFormatException e) {
                return Double.parseDouble(n);
            }
        }
        if (node.has("L")) {
            List<Object> list = new ArrayList<>();
            node.get("L").forEach(element -> list.add(value(element)));
            return list;
        }
        if (node.has("M")) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.get("M").fields().forEachRemaining(field -> map.put(field.getKey(), value(field.getValue())));
            return map;
        }
        throw new KeyValueStoreException("Table " + table + " holds a value of a type KeyValueStore does not read: " + node);
    }

    /** A conditional write: false if the condition does not hold. */
    private boolean conditional(String action, ObjectNode request) {
        try {
            call(action, request);
            return true;
        } catch (ConditionFailed e) {
            return false;
        }
    }

    /** The condition of a write does not hold. */
    private static final class ConditionFailed extends KeyValueStoreException {

        private static final long serialVersionUID = 1L;

        ConditionFailed(String message) {
            super(message);
        }
    }

    /** One API call, retried on throttling, server errors and I/O errors. */
    private JsonNode call(String action, ObjectNode request) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new KeyValueStoreException("Cannot write the request for table " + table, e);
        }
        Map<String, String> headers = Map.of("Content-Type", "application/x-amz-json-1.0",
                "X-Amz-Target", "DynamoDB_20120810." + action);
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, String> signed = SigV4Signer.sign("POST", endpoint, headers, body, "dynamodb", region,
                    credentials.get(), clock.instant());
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            signed.forEach((name, value) -> {
                if (!"Host".equals(name)) {
                    builder.header(name, value);
                }
            });
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return mapper.readTree(response.body().isEmpty() ? "{}" : response.body());
                }
                JsonNode error = parse(response.body());
                String type = error.path("__type").asText("");
                type = type.substring(type.lastIndexOf('#') + 1);
                String message = error.has("message") ? error.path("message").asText() : error.path("Message").asText(response.body());
                lastError = response.statusCode() + " " + type + ": " + message;
                if ("ConditionalCheckFailedException".equals(type)) {
                    throw new ConditionFailed(lastError);
                }
                if ("AccessDeniedException".equals(type) || "UnrecognizedClientException".equals(type)
                        || response.statusCode() == 403) {
                    throw new KeyValueAccessDeniedException("DynamoDB refused " + action + " on table " + table + ": " + lastError);
                }
                if (response.statusCode() < 500 && !RETRYABLE.contains(type)) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KeyValueStoreException("Interrupted while calling DynamoDB for table " + table, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt);
            }
        }
        throw new KeyValueStoreException("DynamoDB " + action + " on table " + table + " failed: " + lastError);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
        } catch (IOException e) {
            return mapper.createObjectNode();
        }
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyValueStoreException("Interrupted while calling DynamoDB for table " + table, e);
        }
    }
}
