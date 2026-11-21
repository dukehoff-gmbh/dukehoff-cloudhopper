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

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.provider.springboot.EventEndpoint;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishers that deliver to the queue-triggered functions of the same service, without a broker:
 * each message is handed to every function consuming the queue on a background thread, retried a
 * few times if the function fails, and logged and dropped after that. Messages do not survive a
 * restart; use Kafka ({@code cloudhopper.events.broker=kafka}) for durable queues.
 */
public class InProcessPublisherFactory implements PublisherFactory {

    private static final Logger LOG = Logger.getLogger(InProcessPublisherFactory.class.getName());

    private final List<EventEndpoint> endpoints;
    private final int maxAttempts;
    private final Duration backoff;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param endpoints   the event endpoints of the service
     * @param maxAttempts how often a message is delivered before it is dropped
     * @param backoff     the pause before the second delivery, doubled for each further one
     */
    public InProcessPublisherFactory(List<EventEndpoint> endpoints, int maxAttempts, Duration backoff) {
        this.endpoints = endpoints;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    @Override
    public <T> Publisher<T> queue(String name, boolean existing) {
        List<EventEndpoint> consumers = endpoints.stream()
                .filter(e -> e.source() == EventSource.QUEUE && e.name().equals(name)).toList();
        return new Publisher<>() {
            @Override
            public void send(T message) {
                deliver(name, consumers, MessageBodies.of(message, name));
            }

            @Override
            public void sendAll(Collection<? extends T> messages) {
                messages.forEach(this::send);
            }
        };
    }

    private void deliver(String queue, List<EventEndpoint> consumers, String body) {
        if (consumers.isEmpty()) {
            throw new PublishException("No function of this service consumes queue " + queue
                    + "; to send to another service, use a broker (cloudhopper.events.broker=kafka)");
        }
        String id = UUID.randomUUID().toString();
        Map<String, String> headers = Map.of("ce-id", id, "ce-source", queue, "ce-time", Instant.now().toString());
        for (EventEndpoint consumer : consumers) {
            executor.execute(() -> {
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    try {
                        consumer.deliver(body, headers, attempt);
                        return;
                    } catch (Exception e) {
                        if (attempt == maxAttempts) {
                            LOG.log(Level.SEVERE, "Giving up on message " + id + " of queue " + queue + " for function "
                                    + consumer.functionId() + " after " + attempt + " attempts", e);
                            return;
                        }
                        LOG.log(Level.WARNING, "Function " + consumer.functionId() + " failed on message " + id
                                + " (attempt " + attempt + "), delivering it again", e);
                        try {
                            Thread.sleep(backoff.toMillis() << (attempt - 1));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        }
    }
}
