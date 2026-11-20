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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Resolves {@code @ConfigProperty} parameters of functions; called by the generated handlers.
 * Works with any MicroProfile Config implementation.
 */
public final class ConfigParams {

    private ConfigParams() {
    }

    /**
     * The value of a required property, or its default.
     *
     * @param <T> the type
     * @param name the property name
     * @param type the type to convert to
     * @param defaultValue the default from {@code @ConfigProperty(defaultValue)}, or
     *                     {@link ConfigProperty#UNCONFIGURED_VALUE}
     * @return the value
     * @throws NoSuchElementException if the property has neither a value nor a default
     */
    public static <T> T value(String name, Class<T> type, String defaultValue) {
        return optional(name, type, defaultValue).orElseThrow(() -> new NoSuchElementException(
                "No value for the configuration property " + name + " (environment variable "
                + environmentName(name) + ")"));
    }

    /**
     * The value of an optional property, or its default.
     *
     * @param <T> the type
     * @param name the property name
     * @param type the type to convert to
     * @param defaultValue the default, or {@link ConfigProperty#UNCONFIGURED_VALUE}
     * @return the value, empty if the property has neither a value nor a default
     */
    public static <T> Optional<T> optional(String name, Class<T> type, String defaultValue) {
        Config config = ConfigProvider.getConfig();
        Optional<T> value = config.getOptionalValue(name, type);
        if (value.isPresent() || !hasDefault(defaultValue)) {
            return value;
        }
        Converter<T> converter = config.getConverter(type).orElseThrow(() -> new IllegalArgumentException(
                "No converter for " + type.getName()));
        return Optional.ofNullable(converter.convert(defaultValue));
    }

    /**
     * The values of a list property (comma-separated), or its default.
     *
     * @param <T> the element type
     * @param name the property name
     * @param type the element type to convert to
     * @param defaultValue the default, or {@link ConfigProperty#UNCONFIGURED_VALUE}
     * @return the values
     * @throws NoSuchElementException if the property has neither a value nor a default
     */
    public static <T> List<T> values(String name, Class<T> type, String defaultValue) {
        Config config = ConfigProvider.getConfig();
        Optional<List<T>> values = config.getOptionalValues(name, type);
        if (values.isPresent()) {
            return values.get();
        }
        if (!hasDefault(defaultValue)) {
            throw new NoSuchElementException("No value for the configuration property " + name
                    + " (environment variable " + environmentName(name) + ")");
        }
        Converter<T> converter = config.getConverter(type).orElseThrow(() -> new IllegalArgumentException(
                "No converter for " + type.getName()));
        return java.util.Arrays.stream(defaultValue.split("(?<!\\\\),"))
                .map(element -> converter.convert(element.replace("\\,", ",")))
                .toList();
    }

    /**
     * The environment variable a property is read from: {@code report.bucket} →
     * {@code REPORT_BUCKET}.
     *
     * @param name the property name
     * @return the environment variable name
     */
    public static String environmentName(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean hasDefault(String defaultValue) {
        return defaultValue != null && !ConfigProperty.UNCONFIGURED_VALUE.equals(defaultValue);
    }
}
