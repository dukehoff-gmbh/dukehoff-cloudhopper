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

import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaOperations;

/**
 * Publishers that send to the Kafka topic named like the queue, which the queue-triggered
 * functions consume when {@code cloudhopper.events.broker=kafka}. A send waits until Kafka has
 * acknowledged the message.
 */
public class KafkaPublisherFactory implements PublisherFactory {

    private final KafkaOperations<Object, Object> kafka;

    /**
     * @param kafka the Kafka template of the application
     */
    @SuppressWarnings("unchecked")
    public KafkaPublisherFactory(KafkaOperations<?, ?> kafka) {
        this.kafka = (KafkaOperations<Object, Object>) kafka;
    }

    @Override
    public <T> Publisher<T> queue(String name, boolean existing) {
        return new Publisher<>() {
            @Override
            public void send(T message) {
                await(name, List.of(kafka.send(name, MessageBodies.of(message, name))));
            }

            @Override
            public void sendAll(Collection<? extends T> messages) {
                List<CompletableFuture<?>> sent = new ArrayList<>();
                messages.forEach(message -> sent.add(kafka.send(name, MessageBodies.of(message, name))));
                await(name, sent);
            }
        };
    }

    private static void await(String topic, List<? extends CompletableFuture<?>> sent) {
        try {
            CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new PublishException("Kafka did not accept a message for topic " + topic, e.getCause());
        } catch (TimeoutException e) {
            throw new PublishException("Kafka did not acknowledge the messages for topic " + topic + " in time", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishException("Interrupted while sending to topic " + topic, e);
        }
    }
}
