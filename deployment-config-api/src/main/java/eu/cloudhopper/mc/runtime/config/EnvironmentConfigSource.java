package eu.cloudhopper.mc.runtime.config;

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

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Environment variables, ordinal 300. A property {@code report.bucket} is looked up as
 * {@code report.bucket}, {@code report_bucket} and {@code REPORT_BUCKET}, as MicroProfile Config
 * specifies: every character that is not a letter or digit becomes {@code _}.
 */
class EnvironmentConfigSource implements ConfigSource {

    private final Map<String, String> environment;

    EnvironmentConfigSource() {
        this(System.getenv());
    }

    EnvironmentConfigSource(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * The environment variable name of a property: {@code report.bucket} → {@code REPORT_BUCKET}.
     *
     * @param property the property name
     * @return the environment variable name
     */
    static String environmentName(String property) {
        return property.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public Set<String> getPropertyNames() {
        return environment.keySet();
    }

    @Override
    public String getValue(String name) {
        String value = environment.get(name);
        if (value != null) {
            return value;
        }
        String sanitized = name.replaceAll("[^A-Za-z0-9]", "_");
        value = environment.get(sanitized);
        return value != null ? value : environment.get(sanitized.toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public int getOrdinal() {
        return 300;
    }

    @Override
    public String getName() {
        return "EnvironmentVariables";
    }
}
