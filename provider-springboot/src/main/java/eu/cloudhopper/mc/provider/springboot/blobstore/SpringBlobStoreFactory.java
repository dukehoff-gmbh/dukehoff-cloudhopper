package eu.cloudhopper.mc.provider.springboot.blobstore;

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

import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;

/**
 * The {@code ServiceLoader} entry of the Spring Boot runtime: the stores come from the Spring
 * context, which registers its factory with {@code BlobStores.setFactory} when it starts. This
 * class only answers if a function uses a bucket before that.
 */
public class SpringBlobStoreFactory implements BlobStoreFactory {

    @Override
    public BlobStore bucket(String name, boolean existing) {
        throw new BlobStoreException("The Spring Boot application has not started yet; bucket " + name
                + " is available once it has");
    }
}
