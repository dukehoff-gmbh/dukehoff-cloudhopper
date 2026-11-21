package eu.cloudhopper.mc.runtime.spi;

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

import eu.cloudhopper.mc.runtime.Publisher;

/**
 * Creates the {@link Publisher}s of a target. A provider runtime module registers an
 * implementation in {@code META-INF/services/eu.cloudhopper.mc.runtime.spi.PublisherFactory};
 * the first one found is used. The deployment passes the queue's address as the configuration
 * property {@code cloudhopper.queue.<name>.url} (environment variable
 * {@code CLOUDHOPPER_QUEUE_<NAME>_URL}) where the target needs one.
 */
public interface PublisherFactory {

    /**
     * @param <T>      the message type
     * @param name     the queue name from {@code @Queue(name)}
     * @param existing {@code @Queue(existing)}
     * @return a publisher for the queue; it may be cached and shared by all calls
     */
    <T> Publisher<T> queue(String name, boolean existing);
}
