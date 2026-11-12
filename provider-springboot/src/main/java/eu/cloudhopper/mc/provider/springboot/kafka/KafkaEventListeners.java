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

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.provider.springboot.EventEndpoint;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.util.backoff.FixedBackOff;

/**
 * One Kafka listener container per queue-triggered function. A message is committed only after
 * the function returned; when it throws, the message is delivered again, up to the maximum number
 * of attempts, and then published to {@code <topic>.DLT} (or logged and skipped when the service
 * has no {@link KafkaOperations} to publish with).
 */
public class KafkaEventListeners implements SmartLifecycle {

    private static final Logger LOG = Logger.getLogger(KafkaEventListeners.class.getName());
    private static final Set<String> CORE_ATTRIBUTES = Set.of("id", "source", "time", "type", "specversion");

    private final List<EventEndpoint> endpoints;
    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaOperations<?, ?> kafkaOperations;
    private final String groupIdPrefix;
    private final int maxAttempts;
    private final Duration backoff;
    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private volatile boolean running;

    /**
     * @param endpoints       all event endpoints of the service; only the queue-triggered ones are used
     * @param consumerFactory the consumer factory of the service, whose settings are reused with
     *                        string deserializers
     * @param kafkaOperations publishes messages to the dead letter topic; {@code null} to log and skip them
     * @param groupIdPrefix   prefix of the consumer group of each function
     * @param maxAttempts     how often a message is delivered before it is given up
     * @param backoff         pause between two attempts
     */
    public KafkaEventListeners(List<EventEndpoint> endpoints, ConsumerFactory<?, ?> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations, String groupIdPrefix, int maxAttempts, Duration backoff) {
        this.endpoints = endpoints;
        this.consumerFactory = stringConsumerFactory(consumerFactory);
        this.kafkaOperations = kafkaOperations;
        this.groupIdPrefix = groupIdPrefix;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = backoff;
    }

    private static ConsumerFactory<String, String> stringConsumerFactory(ConsumerFactory<?, ?> base) {
        Map<String, Object> config = new HashMap<>(base.getConfigurationProperties());
        // a new function reads the messages already waiting in its queue, as on the cloud targets
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
    }

    @Override
    public synchronized void start() {
        for (EventEndpoint endpoint : endpoints) {
            if (endpoint.source() != EventSource.QUEUE || endpoint.name() == null) {
                continue;
            }
            ContainerProperties properties = new ContainerProperties(endpoint.name());
            properties.setGroupId(groupIdPrefix + endpoint.functionId());
            properties.setAckMode(ContainerProperties.AckMode.RECORD);
            properties.setDeliveryAttemptHeader(true);
            properties.setMessageListener((MessageListener<String, String>) record -> deliver(endpoint, record));

            ConcurrentMessageListenerContainer<String, String> container =
                    new ConcurrentMessageListenerContainer<>(consumerFactory, properties);
            container.setBeanName("cloudhopper-kafka-" + endpoint.functionId());
            container.setCommonErrorHandler(new DefaultErrorHandler(recoverer(),
                    new FixedBackOff(backoff.toMillis(), maxAttempts - 1L)));
            container.start();
            containers.add(container);
            LOG.info("Function " + endpoint.functionId() + " consumes the Kafka topic " + endpoint.name()
                    + " in group " + properties.getGroupId());
        }
        running = true;
    }

    private ConsumerRecordRecoverer recoverer() {
        if (kafkaOperations == null) {
            return (record, e) -> LOG.log(Level.SEVERE, "Giving up on message " + record.topic() + "-"
                    + record.partition() + "@" + record.offset() + " after " + maxAttempts + " attempts", e);
        }
        // let Kafka choose the partition: the dead letter topic may have fewer partitions
        return new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, e) -> new TopicPartition(record.topic() + ".DLT", -1));
    }

    static void deliver(EventEndpoint endpoint, ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        int attempt = 0;
        for (Header header : record.headers()) {
            if (KafkaHeaders.DELIVERY_ATTEMPT.equals(header.key()) && header.value() != null) {
                attempt = ByteBuffer.wrap(header.value()).getInt();
                continue;
            }
            if (header.key().startsWith("kafka_") || header.value() == null) {
                continue;
            }
            String value = new String(header.value(), StandardCharsets.UTF_8);
            String key = header.key().toLowerCase(Locale.ROOT);
            if (key.startsWith("ce_")) {
                // CloudEvents Kafka binding: ce_<attribute>
                headers.put("ce-" + key.substring(3), value);
            } else if (!CORE_ATTRIBUTES.contains(key)) {
                // any other header is an attribute of the message; it cannot pose as id, source or time
                headers.putIfAbsent("ce-" + key, value);
            }
        }
        // a stable id, so that a redelivered message keeps it
        headers.putIfAbsent("ce-id", record.topic() + "-" + record.partition() + "-" + record.offset());
        if (record.timestamp() >= 0) {
            headers.putIfAbsent("ce-time", Instant.ofEpochMilli(record.timestamp()).toString());
        }
        try {
            endpoint.deliver(record.value(), headers, attempt);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
