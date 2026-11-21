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
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * A small MicroProfile Config implementation for function runtimes: it reads environment
 * variables, system properties and {@code META-INF/microprofile-config.properties}, plus the
 * {@code ConfigSource}s and {@code Converter}s registered through {@link java.util.ServiceLoader}.
 * It keeps the runtime small and fast to start; it is not a complete, certified implementation
 * (no profiles, no property expressions, no config mapping). A project that needs those adds a
 * full implementation such as SmallRye Config and removes this resolver from the service file.
 */
public class CloudhopperConfigProviderResolver extends ConfigProviderResolver {

    private final Map<ClassLoader, Config> configs = new ConcurrentHashMap<>();

    @Override
    public Config getConfig() {
        return getConfig(null);
    }

    @Override
    public Config getConfig(ClassLoader loader) {
        ClassLoader key = loader != null ? loader : defaultLoader();
        return configs.computeIfAbsent(key, l -> getBuilder().forClassLoader(l)
                .addDefaultSources().addDiscoveredSources().addDiscoveredConverters().build());
    }

    @Override
    public ConfigBuilder getBuilder() {
        return new CloudhopperConfigBuilder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader loader) {
        ClassLoader key = loader != null ? loader : defaultLoader();
        if (configs.putIfAbsent(key, config) != null) {
            throw new IllegalStateException("A configuration is already registered for " + key);
        }
    }

    @Override
    public void releaseConfig(Config config) {
        configs.values().removeIf(c -> c == config);
    }

    private static ClassLoader defaultLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : CloudhopperConfigProviderResolver.class.getClassLoader();
    }
}
