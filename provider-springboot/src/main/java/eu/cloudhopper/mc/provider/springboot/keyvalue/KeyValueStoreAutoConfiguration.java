package eu.cloudhopper.mc.provider.springboot.keyvalue;


/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.KeyValueStores;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Makes {@code @Table} stores work in the Spring Boot runtime. A {@link KeyValueStoreFactory}
 * of another runtime module found through {@link ServiceLoader} is used. Without one, every
 * table is kept in memory ({@link InMemoryKeyValueStore}), one per deployed name
 * ({@code cloudhopper.table.<name>.name}).
 */
@AutoConfiguration
public class KeyValueStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KeyValueStoreFactory.class)
    KeyValueStoreFactory cloudhopperDefaultKeyValueStoreFactory() {
        KeyValueStoreFactory discovered = discovered();
        if (discovered != null) {
            return discovered;
        }
        Map<String, InMemoryKeyValueStore> tables = new ConcurrentHashMap<>();
        return (name, existing) -> tables.computeIfAbsent(ConfigProvider.getConfig()
                .getOptionalValue("cloudhopper.table." + name + ".name", String.class).orElse(name),
                table -> new InMemoryKeyValueStore());
    }

    /** The factory of another runtime module on the classpath, or {@code null}. */
    static KeyValueStoreFactory discovered() {
        for (KeyValueStoreFactory factory : ServiceLoader.load(KeyValueStoreFactory.class,
                KeyValueStoreAutoConfiguration.class.getClassLoader())) {
            if (!(factory instanceof SpringKeyValueStoreFactory)) {
                return factory;
            }
        }
        return null;
    }

    @Bean
    SmartInitializingSingleton cloudhopperKeyValueStoreRegistration(KeyValueStoreFactory factory) {
        return () -> KeyValueStores.setFactory(factory);
    }
}
