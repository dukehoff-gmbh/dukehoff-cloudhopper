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

import java.util.Collection;

/**
 * Sends messages to a queue; a function receives it as a parameter annotated with
 * {@link eu.cloudhopper.mc.annotations.Queue}. A {@code String} is sent as it is, any other
 * message as JSON, the way queue triggers read their input: a function with
 * {@code @EventTrigger(source = QUEUE)} on the same queue receives the message as its input type.
 *
 * @param <T> the message type
 */
public interface Publisher<T> {

    /**
     * Sends one message.
     *
     * @param message the message
     * @throws PublishException if the queue did not accept the message after retries
     */
    void send(T message);

    /**
     * Sends several messages, in as few requests as the queue's batch limits allow.
     *
     * @param messages the messages
     * @throws PublishException if the queue did not accept some of them after retries; the
     *                          others have been sent
     */
    void sendAll(Collection<? extends T> messages);
}
