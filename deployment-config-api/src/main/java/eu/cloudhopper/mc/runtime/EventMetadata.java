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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Describes the event that invoked a function with an
 * {@link eu.cloudhopper.mc.annotations.EventTrigger}.
 *
 * <p>Declare a parameter of this type to receive it. The field names follow CloudEvents.
 * Values a platform does not provide are {@code null} ({@link #getTime()}) or {@code 0}
 * ({@link #getDeliveryAttempt()}).</p>
 */
public final class EventMetadata {

    private final String id;
    private final String source;
    private final Instant time;
    private final int deliveryAttempt;
    private final Map<String, String> attributes;

    /**
     * Creates the metadata of one event.
     *
     * @param id              the id the platform assigned to the message
     * @param source          the queue or topic the event came from
     * @param time            when the message was sent, or {@code null} if unknown
     * @param deliveryAttempt how often the message has been delivered, starting at 1; 0 if unknown
     * @param attributes      the message attributes; {@code null} for none
     */
    public EventMetadata(String id, String source, Instant time, int deliveryAttempt, Map<String, String> attributes) {
        this.id = id;
        this.source = source;
        this.time = time;
        this.deliveryAttempt = deliveryAttempt;
        this.attributes = attributes == null || attributes.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(attributes));
    }

    /**
     * @return the id the platform assigned to the message
     */
    public String getId() {
        return id;
    }

    /**
     * @return the name or platform identifier of the queue or topic the event came from
     */
    public String getSource() {
        return source;
    }

    /**
     * @return when the message was sent, or {@code null} if the platform does not say
     */
    public Instant getTime() {
        return time;
    }

    /**
     * Delivery is at least once: a message is delivered again when the function throws.
     *
     * @return how often this message has been delivered, starting at 1; 0 if unknown
     */
    public int getDeliveryAttempt() {
        return deliveryAttempt;
    }

    /**
     * @return the message attributes (application properties), never {@code null}
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventMetadata)) {
            return false;
        }
        EventMetadata other = (EventMetadata) o;
        return deliveryAttempt == other.deliveryAttempt
                && Objects.equals(id, other.id)
                && Objects.equals(source, other.source)
                && Objects.equals(time, other.time)
                && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, time, deliveryAttempt, attributes);
    }

    @Override
    public String toString() {
        return "EventMetadata{id=" + id + ", source=" + source + ", time=" + time
                + ", deliveryAttempt=" + deliveryAttempt + ", attributes=" + attributes + "}";
    }
}
