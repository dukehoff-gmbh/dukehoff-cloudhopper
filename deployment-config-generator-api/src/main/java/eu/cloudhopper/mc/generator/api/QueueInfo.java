package eu.cloudhopper.mc.generator.api;

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

import java.util.Locale;

/**
 * A queue a function sends to through a {@code @Queue} parameter.
 */
public class QueueInfo {

    private final String name;
    private final boolean existing;

    /**
     * @param name     the queue name
     * @param existing {@code true} for a queue outside the project, used without name prefix
     */
    public QueueInfo(String name, boolean existing) {
        this.name = name;
        this.existing = existing;
    }

    /**
     * @return the queue name
     */
    public String getName() {
        return name;
    }

    /**
     * @return {@code true} for a queue outside the project, used without name prefix
     */
    public boolean isExisting() {
        return existing;
    }

    /**
     * @return a name usable in Terraform resource names: lowercase letters, digits and underscores
     */
    public String getId() {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    /**
     * @return the environment variable the runtime reads the queue's address from, e.g.
     *         {@code CLOUDHOPPER_QUEUE_COLLECT_URL} (configuration property {@code cloudhopper.queue.collect.url})
     */
    public String getUrlEnvironmentName() {
        return ("cloudhopper.queue." + name + ".url").replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }
}
