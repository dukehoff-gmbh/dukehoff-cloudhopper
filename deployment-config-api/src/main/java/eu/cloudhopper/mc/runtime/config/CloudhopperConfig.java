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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * The configuration: sources ordered by ordinal (highest first), values converted on access.
 * An empty value counts as missing, as MicroProfile Config requires.
 */
class CloudhopperConfig implements Config {

    private final List<ConfigSource> sources;
    private final Converters converters;

    CloudhopperConfig(List<ConfigSource> sources, Converters converters) {
        List<ConfigSource> sorted = new ArrayList<>(sources);
        sorted.sort(Comparator.comparingInt(ConfigSource::getOrdinal).reversed()
                .thenComparing(ConfigSource::getName));
        this.sources = Collections.unmodifiableList(sorted);
        this.converters = converters;
    }

    @Override
    public <T> T getValue(String name, Class<T> type) {
        return getOptionalValue(name, type).orElseThrow(() -> new NoSuchElementException(
                "No value for the configuration property " + name));
    }

    @Override
    public ConfigValue getConfigValue(String name) {
        for (ConfigSource source : sources) {
            String value = source.getValue(name);
            if (value != null) {
                return new Value(name, value, source.getName(), source.getOrdinal());
            }
        }
        return new Value(name, null, null, 0);
    }

    @Override
    public <T> Optional<T> getOptionalValue(String name, Class<T> type) {
        String raw = getConfigValue(name).getValue();
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(convert(raw, type));
    }

    @Override
    public <T> Optional<List<T>> getOptionalValues(String name, Class<T> type) {
        String raw = getConfigValue(name).getValue();
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        List<T> values = new ArrayList<>();
        for (String element : Converters.splitList(raw)) {
            values.add(convert(element, type));
        }
        return values.isEmpty() ? Optional.empty() : Optional.of(values);
    }

    @Override
    public <T> List<T> getValues(String name, Class<T> type) {
        return getOptionalValues(name, type).orElseThrow(() -> new NoSuchElementException(
                "No value for the configuration property " + name));
    }

    private <T> T convert(String raw, Class<T> type) {
        Converter<T> converter = converters.get(type).orElseThrow(() -> new IllegalArgumentException(
                "No converter for " + type.getName()));
        return converter.convert(raw);
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> names = new LinkedHashSet<>();
        sources.forEach(source -> names.addAll(source.getPropertyNames()));
        return names;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return sources;
    }

    @Override
    public <T> Optional<Converter<T>> getConverter(Class<T> type) {
        return converters.get(type);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap the configuration to " + type.getName());
    }

    private record Value(String name, String value, String sourceName, int sourceOrdinal) implements ConfigValue {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String getRawValue() {
            return value;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }

        @Override
        public int getSourceOrdinal() {
            return sourceOrdinal;
        }
    }
}
