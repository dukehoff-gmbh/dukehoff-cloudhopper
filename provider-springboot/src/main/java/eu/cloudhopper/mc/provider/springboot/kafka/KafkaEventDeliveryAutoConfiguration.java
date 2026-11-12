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

import eu.cloudhopper.mc.provider.springboot.EventEndpoint;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;

/**
 * Delivers the messages of Kafka topics to the queue-triggered functions of a Spring Boot service.
 *
 * <p>Active when {@code spring-kafka} is on the classpath and the property
 * {@code cloudhopper.events.broker=kafka} is set. Every function with
 * {@code @EventTrigger(source = QUEUE, name = "<queue>")} consumes the topic {@code <queue>}; the
 * connection comes from the usual {@code spring.kafka.*} properties.</p>
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>{@code cloudhopper.events.kafka.group-id-prefix} (default {@code cloudhopper-}): every
 *       function consumes in the group {@code <prefix><function id>}, so the replicas of a service
 *       share the messages and each message reaches each function once</li>
 *   <li>{@code cloudhopper.events.kafka.max-attempts} (default 5): how often a message is
 *       delivered before it moves to the topic {@code <queue>.DLT}</li>
 *   <li>{@code cloudhopper.events.kafka.backoff} (default 1s): pause between two attempts</li>
 * </ul>
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.kafka.listener.ConcurrentMessageListenerContainer")
@ConditionalOnProperty(name = "cloudhopper.events.broker", havingValue = "kafka")
public class KafkaEventDeliveryAutoConfiguration {

    @Bean
    KafkaEventListeners cloudhopperKafkaEventListeners(List<EventEndpoint> endpoints,
            ConsumerFactory<?, ?> consumerFactory,
            ObjectProvider<KafkaOperations<?, ?>> kafkaOperations,
            Environment environment) {
        return new KafkaEventListeners(endpoints, consumerFactory, kafkaOperations.getIfAvailable(),
                environment.getProperty("cloudhopper.events.kafka.group-id-prefix", "cloudhopper-"),
                environment.getProperty("cloudhopper.events.kafka.max-attempts", Integer.class, 5),
                environment.getProperty("cloudhopper.events.kafka.backoff", Duration.class, Duration.ofSeconds(1)));
    }
}
