package eu.cloudhopper.mc.provider.springboot.publisher;

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
import eu.cloudhopper.mc.runtime.Publishers;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Makes {@code @Queue} publishers work in the Spring Boot runtime: with Kafka
 * ({@code cloudhopper.events.broker=kafka}) they send to the topic of the queue, otherwise they
 * deliver to the consuming functions of the same service.
 */
@AutoConfiguration(afterName = "eu.cloudhopper.mc.provider.springboot.kafka.KafkaEventDeliveryAutoConfiguration")
public class PublisherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PublisherFactory.class)
    PublisherFactory cloudhopperInProcessPublisherFactory(List<EventEndpoint> endpoints, Environment environment) {
        return new InProcessPublisherFactory(endpoints,
                environment.getProperty("cloudhopper.events.in-process.max-attempts", Integer.class, 3),
                environment.getProperty("cloudhopper.events.in-process.backoff", Duration.class, Duration.ofSeconds(1)));
    }

    @Bean
    SmartInitializingSingleton cloudhopperPublisherRegistration(PublisherFactory factory) {
        return () -> Publishers.setFactory(factory);
    }
}
