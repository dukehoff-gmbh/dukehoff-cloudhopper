package eu.cloudhopper.mc.test.tck.api;

/*-
 * #%L
 * test-tck-api - a library from the "Cloudhopper" project.
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The configuration values the TCK deploys the test functions with: each binding passes them the
 * way its target does (the Terraform variable {@code cloudhopper_config}, container environment
 * variables, Helm values).
 */
public final class TckConfiguration {

    /** Read by the function {@code config} with {@code @ConfigProperty(name = "tck.greeting")}. */
    public static final String GREETING_PROPERTY = "tck.greeting";

    /** The value the TCK sets for {@link #GREETING_PROPERTY}. */
    public static final String GREETING = "hello from the deployment";

    /** The properties and their values. */
    public static final Map<String, String> VALUES = Map.of(GREETING_PROPERTY, GREETING);

    private TckConfiguration() {
    }

    /**
     * @return the values as environment variables, named as MicroProfile Config looks them up
     *         ({@code tck.greeting} → {@code TCK_GREETING})
     */
    public static Map<String, String> environment() {
        return VALUES.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT), Map.Entry::getValue));
    }

    /**
     * @return the value of the Terraform variable {@code cloudhopper_config}, as HCL/JSON
     */
    public static String terraformVariable() {
        return VALUES.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\" = \"" + e.getValue().replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * @return the environment for Terraform commands: {@code TF_VAR_cloudhopper_config}
     */
    public static Map<String, String> terraformEnvironment() {
        return Map.of("TF_VAR_cloudhopper_config", terraformVariable());
    }
}
