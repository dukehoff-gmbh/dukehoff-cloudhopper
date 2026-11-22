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


import eu.cloudhopper.mc.provider.springboot.publisher.KafkaPublisherFactory;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;

/**
 * {@code cloudhopper.publisher=kafka}: {@code @Queue} publishers send to the Kafka topic of the
 * queue, without the Kafka listeners of {@code cloudhopper.events.broker=kafka}. For platforms
 * that deliver the topics to {@code /events/<function>} themselves, such as Knative with a
 * {@code KafkaSource}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "eu.cloudhopper.mc.provider.springboot.kafka.KafkaEventDeliveryAutoConfiguration"})
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaOperations")
@ConditionalOnProperty(name = "cloudhopper.publisher", havingValue = "kafka")
public class KafkaPublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PublisherFactory.class)
    PublisherFactory cloudhopperKafkaOnlyPublisherFactory(KafkaOperations<?, ?> kafka) {
        return new KafkaPublisherFactory(kafka);
    }
}
