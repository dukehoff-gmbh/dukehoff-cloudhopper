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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.provider.springboot.EventEndpoint;
import eu.cloudhopper.mc.provider.springboot.publisher.KafkaPublisherFactory;
import eu.cloudhopper.mc.runtime.Publishers;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * {@code cloudhopper.publisher=kafka}: publishers send to the topic, and the application does
 * not consume it itself (on Knative a KafkaSource delivers it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "cloudhopper.publisher=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = {"players"})
class KafkaPublisherOnlyTest {

    static class RecordingEndpoint implements EventEndpoint {

        final BlockingQueue<String> deliveries = new LinkedBlockingQueue<>();

        @Override
        public String functionId() {
            return "players";
        }

        @Override
        public EventSource source() {
            return EventSource.QUEUE;
        }

        @Override
        public String name() {
            return "players";
        }

        @Override
        public void deliver(String body, Map<String, String> headers, int deliveryAttempt) {
            deliveries.add(body);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        RecordingEndpoint players() {
            return new RecordingEndpoint();
        }
    }

    record Player(String name) {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private RecordingEndpoint players;

    @Test
    void sendsToTheTopicWithoutConsumingIt() throws Exception {
        assertInstanceOf(KafkaPublisherFactory.class, context.getBean(PublisherFactory.class));
        assertTrue(context.getBeansOfType(KafkaEventListeners.class).isEmpty(), "no Kafka listeners");

        Publishers.queue("players", false).send(new Player("Ada"));

        Map<String, Object> config = KafkaTestUtils.consumerProps("kafka-publisher-only-test", "false", broker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "players");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "players",
                    Duration.ofSeconds(30));
            assertEquals("{\"name\":\"Ada\"}", record.value());
        }
        assertEquals(null, players.deliveries.poll(2, TimeUnit.SECONDS), "the application consumed the topic");
    }
}
