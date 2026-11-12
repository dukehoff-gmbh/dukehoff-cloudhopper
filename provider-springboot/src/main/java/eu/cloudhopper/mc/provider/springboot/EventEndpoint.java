package eu.cloudhopper.mc.provider.springboot;

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
import java.io.IOException;
import java.util.Map;

/**
 * An event-triggered function as the Spring Boot target exposes it: the generated
 * {@code POST /events/<function>} controller, and the endpoint a broker binding delivers to.
 *
 * <p>Broker bindings, such as the Kafka binding enabled with
 * {@code cloudhopper.events.broker=kafka}, look up all beans of this type and deliver the
 * messages of the queue {@link #name()} to {@link #deliver(String, Map, int)}.</p>
 */
public interface EventEndpoint {

    /**
     * @return the function id, unique within the service
     */
    String functionId();

    /**
     * @return where the events come from
     */
    EventSource source();

    /**
     * @return the name of the queue or bucket
     */
    String name();

    /**
     * Invokes the function for one event. Delivery is at least once: when this method throws,
     * the caller delivers the event again.
     *
     * @param body            the event data, or {@code null}
     * @param headers         CloudEvents attributes as {@code ce-<name>} headers; {@code ce-id},
     *                        {@code ce-source} and {@code ce-time} fill the metadata, the others
     *                        become its attributes
     * @param deliveryAttempt how often the event has been delivered, starting at 1; 0 if unknown
     * @throws IOException if the body is not valid JSON for the input type
     */
    void deliver(String body, Map<String, String> headers, int deliveryAttempt) throws IOException;
}
