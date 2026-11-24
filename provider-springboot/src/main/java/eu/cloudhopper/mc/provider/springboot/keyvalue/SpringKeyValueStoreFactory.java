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

import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;

/**
 * The {@code ServiceLoader} entry of the Spring Boot runtime: the stores come from the Spring
 * context, which registers its factory with {@code KeyValueStores.setFactory} when it starts.
 * This class only answers if a function uses a table before that.
 */
public class SpringKeyValueStoreFactory implements KeyValueStoreFactory {

    @Override
    public KeyValueStore table(String name, boolean existing) {
        throw new KeyValueStoreException("The Spring Boot application has not started yet; table " + name
                + " is available once it has");
    }
}
