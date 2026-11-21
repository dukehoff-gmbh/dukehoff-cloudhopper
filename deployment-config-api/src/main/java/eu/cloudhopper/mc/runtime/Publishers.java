package eu.cloudhopper.mc.runtime;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@code @Queue} parameters of functions; called by the generated handlers.
 */
public final class Publishers {

    private static final Map<String, Publisher<?>> PUBLISHERS = new ConcurrentHashMap<>();
    private static volatile PublisherFactory factory;

    private Publishers() {
    }

    /**
     * The publisher for a queue, created once per queue and instance.
     *
     * @param <T>      the message type
     * @param name     the queue name
     * @param existing whether the queue exists outside the project
     * @return the publisher
     */
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> queue(String name, boolean existing) {
        return (Publisher<T>) PUBLISHERS.computeIfAbsent(name + "|" + existing,
                key -> factory().queue(name, existing));
    }

    /**
     * Replaces the factory found through {@link ServiceLoader}, e.g. by one that a framework
     * creates with its own dependency injection; {@code null} returns to the discovered one.
     *
     * @param replacement the factory to use
     */
    public static void setFactory(PublisherFactory replacement) {
        factory = replacement;
        PUBLISHERS.clear();
    }

    private static PublisherFactory factory() {
        PublisherFactory current = factory;
        if (current == null) {
            synchronized (Publishers.class) {
                if (factory == null) {
                    Iterator<PublisherFactory> found = ServiceLoader.load(PublisherFactory.class,
                            Publishers.class.getClassLoader()).iterator();
                    factory = found.hasNext() ? found.next() : new Unsupported();
                }
                current = factory;
            }
        }
        return current;
    }

    /** No runtime module on the classpath: the build already warned about it. */
    private static final class Unsupported implements PublisherFactory {

        @Override
        public <T> Publisher<T> queue(String name, boolean existing) {
            return new Publisher<>() {
                @Override
                public void send(T message) {
                    fail();
                }

                @Override
                public void sendAll(Collection<? extends T> messages) {
                    fail();
                }

                private void fail() {
                    throw new PublishException("No Publisher implementation for queue " + name
                            + " on this target: add the runtime module of the target, or check the build warnings");
                }
            };
        }
    }
}
