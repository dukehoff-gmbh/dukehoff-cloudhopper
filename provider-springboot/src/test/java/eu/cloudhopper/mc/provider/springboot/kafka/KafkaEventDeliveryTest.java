package eu.cloudhopper.mc.provider.springboot.kafka;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.provider.springboot.EventEndpoint;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "cloudhopper.events.broker=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "cloudhopper.events.kafka.max-attempts=3",
    "cloudhopper.events.kafka.backoff=100ms"
})
@EmbeddedKafka(partitions = 1, topics = {"orders", "retries", "poison", "poison.DLT"})
class KafkaEventDeliveryTest {

    /** One delivery as the function saw it. */
    record Delivery(String body, Map<String, String> headers, int attempt) {
    }

    /** Records every delivery; "fail-once" throws on the first attempt, "fail-always" on every one. */
    static class RecordingEndpoint implements EventEndpoint {

        final BlockingQueue<Delivery> deliveries = new LinkedBlockingQueue<>();
        private final String functionId;
        private final EventSource source;
        private final String name;

        RecordingEndpoint(String functionId, EventSource source, String name) {
            this.functionId = functionId;
            this.source = source;
            this.name = name;
        }

        @Override
        public String functionId() {
            return functionId;
        }

        @Override
        public EventSource source() {
            return source;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void deliver(String body, Map<String, String> headers, int deliveryAttempt) {
            deliveries.add(new Delivery(body, headers, deliveryAttempt));
            if (body.equals("fail-always") || (body.equals("fail-once") && deliveryAttempt == 1)) {
                throw new IllegalStateException("failing on purpose: " + body);
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        RecordingEndpoint orders() {
            return new RecordingEndpoint("orders", EventSource.QUEUE, "orders");
        }

        @Bean
        RecordingEndpoint retries() {
            return new RecordingEndpoint("retries", EventSource.QUEUE, "retries");
        }

        @Bean
        RecordingEndpoint poison() {
            return new RecordingEndpoint("poison", EventSource.QUEUE, "poison");
        }

        @Bean
        RecordingEndpoint uploads() {
            // storage events do not come from Kafka
            return new RecordingEndpoint("uploads", EventSource.STORAGE, "uploads");
        }
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafka;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private RecordingEndpoint orders;

    @Autowired
    private RecordingEndpoint retries;

    @Autowired
    private RecordingEndpoint poison;

    @Test
    void messagesReachTheFunctionWithTheirMetadata() throws Exception {
        ProducerRecord<Object, Object> record = new ProducerRecord<>("orders", "{\"id\":1}");
        record.headers().add("ce_id", "order-1".getBytes(StandardCharsets.UTF_8));
        record.headers().add("tenant", "acme".getBytes(StandardCharsets.UTF_8));
        record.headers().add("id", "not-the-id".getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(10, TimeUnit.SECONDS);

        Delivery delivery = orders.deliveries.poll(30, TimeUnit.SECONDS);
        assertEquals("{\"id\":1}", delivery.body());
        assertEquals(1, delivery.attempt());
        assertEquals("order-1", delivery.headers().get("ce-id"));
        assertEquals("acme", delivery.headers().get("ce-tenant"));
        assertTrue(delivery.headers().containsKey("ce-time"));
    }

    @Test
    void aFailedMessageIsDeliveredAgain() throws Exception {
        kafka.send("retries", "fail-once").get(10, TimeUnit.SECONDS);

        Delivery first = retries.deliveries.poll(30, TimeUnit.SECONDS);
        Delivery second = retries.deliveries.poll(30, TimeUnit.SECONDS);
        assertEquals(1, first.attempt());
        assertEquals(2, second.attempt());
        // without ce_id the id is topic-partition-offset, the same for every attempt
        assertEquals(first.headers().get("ce-id"), second.headers().get("ce-id"));
        assertTrue(first.headers().get("ce-id").startsWith("retries-0-"));
    }

    @Test
    void aMessageThatKeepsFailingMovesToTheDeadLetterTopic() throws Exception {
        Map<String, Object> config = KafkaTestUtils.consumerProps("dlt-reader", "false", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "poison.DLT");
            kafka.send("poison", "fail-always").get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(consumer, "poison.DLT",
                    java.time.Duration.ofSeconds(30));
            assertEquals("fail-always", dead.value());
        }
        List<Integer> attempts = poison.deliveries.stream().map(Delivery::attempt).toList();
        assertEquals(List.of(1, 2, 3), attempts);
    }
}
