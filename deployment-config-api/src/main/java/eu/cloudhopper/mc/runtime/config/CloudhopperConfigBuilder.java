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
import java.util.List;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Builds a {@link CloudhopperConfig}.
 */
class CloudhopperConfigBuilder implements ConfigBuilder {

    private final List<ConfigSource> sources = new ArrayList<>();
    private final Converters converters = new Converters();
    private ClassLoader loader = CloudhopperConfigBuilder.class.getClassLoader();
    private boolean defaultSources;
    private boolean discoveredSources;
    private boolean discoveredConverters;

    @Override
    public ConfigBuilder addDefaultSources() {
        defaultSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        discoveredSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        discoveredConverters = true;
        return this;
    }

    @Override
    public ConfigBuilder forClassLoader(ClassLoader loader) {
        if (loader != null) {
            this.loader = loader;
        }
        return this;
    }

    @Override
    public ConfigBuilder withSources(ConfigSource... sources) {
        this.sources.addAll(List.of(sources));
        return this;
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        for (Converter<?> converter : converters) {
            this.converters.add(converter);
        }
        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> type, int priority, Converter<T> converter) {
        converters.add(type, priority, converter);
        return this;
    }

    @Override
    public Config build() {
        List<ConfigSource> all = new ArrayList<>(sources);
        if (defaultSources) {
            all.add(new SystemPropertiesConfigSource());
            all.add(new EnvironmentConfigSource());
            all.addAll(PropertiesConfigSource.load(loader, "META-INF/microprofile-config.properties"));
        }
        if (discoveredSources) {
            ServiceLoader.load(ConfigSource.class, loader).forEach(all::add);
            ServiceLoader.load(ConfigSourceProvider.class, loader)
                    .forEach(provider -> provider.getConfigSources(loader).forEach(all::add));
        }
        if (discoveredConverters) {
            ServiceLoader.load(Converter.class, loader).forEach(converters::add);
        }
        return new CloudhopperConfig(all, converters);
    }
}
