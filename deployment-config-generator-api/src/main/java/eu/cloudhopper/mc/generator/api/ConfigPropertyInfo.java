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
 * A configuration property a function reads with {@code @ConfigProperty}: the deployment passes
 * its value as the environment variable {@link #getEnvironmentName()}.
 */
public class ConfigPropertyInfo {

    private final String name;
    private final boolean required;

    /**
     * @param name the property name, e.g. {@code report.bucket}
     * @param required {@code true} if the function has no default and the type is not {@code Optional}
     */
    public ConfigPropertyInfo(String name, boolean required) {
        this.name = name;
        this.required = required;
    }

    /**
     * @return the property name, e.g. {@code report.bucket}
     */
    public String getName() {
        return name;
    }

    /**
     * @return the environment variable MicroProfile Config reads it from, e.g. {@code REPORT_BUCKET}
     */
    public String getEnvironmentName() {
        return name.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }

    /**
     * @return {@code true} if the deployment must provide a value
     */
    public boolean isRequired() {
        return required;
    }
}
