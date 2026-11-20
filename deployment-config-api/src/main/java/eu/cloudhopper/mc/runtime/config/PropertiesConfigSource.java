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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * A properties file such as {@code META-INF/microprofile-config.properties}; ordinal 100 unless
 * the file sets {@code config_ordinal}.
 */
class PropertiesConfigSource implements ConfigSource {

    private final Map<String, String> properties;
    private final String name;
    private final int ordinal;

    PropertiesConfigSource(Map<String, String> properties, String name) {
        this.properties = properties;
        this.name = name;
        String configured = properties.get(CONFIG_ORDINAL);
        this.ordinal = configured != null ? Integer.parseInt(configured.trim()) : DEFAULT_ORDINAL;
    }

    static List<ConfigSource> load(ClassLoader loader, String resource) {
        List<ConfigSource> sources = new ArrayList<>();
        try {
            Enumeration<URL> urls = loader.getResources(resource);
            for (URL url : Collections.list(urls)) {
                Properties properties = new Properties();
                try (InputStream in = url.openStream()) {
                    properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
                Map<String, String> map = new HashMap<>();
                properties.stringPropertyNames().forEach(key -> map.put(key, properties.getProperty(key)));
                sources.add(new PropertiesConfigSource(map, "PropertiesConfigSource[" + url + "]"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
        return sources;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String getValue(String key) {
        return properties.get(key);
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public String getName() {
        return name;
    }
}
