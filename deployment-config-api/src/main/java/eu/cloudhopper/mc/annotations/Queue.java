package eu.cloudhopper.mc.annotations;

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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a {@link eu.cloudhopper.mc.runtime.Publisher} parameter to a queue the function sends
 * messages to.
 *
 * <p>The queue is one that an {@code @EventTrigger(source = QUEUE, name = ...)} of the same
 * project creates; the generator uses its deployed name, grants the function's identity
 * permission to send to it and passes its address to the runtime. A queue owned by another
 * system is declared with {@code existing = true}. A queue that is neither is a build error.</p>
 *
 * <pre>{@code
 * @Function(name = "startCollection")
 * @ScheduledTrigger(cron = "0 4 * * *")
 * public void start(@Queue(name = "collect") Publisher<CollectRequest> queue) {
 *     accounts().forEach(account -> queue.send(new CollectRequest(account)));
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface Queue {

    /**
     * Constants for the attribute names of {@link Queue}, for generator feature declarations.
     */
    public static class QueueAttribute {

        public static final String NAME = "name";
        public static final String EXISTING = "existing";
    }

    /**
     * The name of the queue, as in {@code @EventTrigger(name = ...)}.
     *
     * @return the queue name
     */
    String name();

    /**
     * {@code true} for a queue another system owns: it is used under exactly this name, without
     * the deployment's name prefix, and no function of the project needs to consume it.
     *
     * @return whether the queue exists outside the project
     */
    boolean existing() default false;
}
