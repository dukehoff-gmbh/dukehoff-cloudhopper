package eu.cloudhopper.mc.runtime.spi;

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

import eu.cloudhopper.mc.runtime.BlobStore;

/**
 * Creates the {@link BlobStore}s of a target. A provider runtime module registers an
 * implementation in {@code META-INF/services/eu.cloudhopper.mc.runtime.spi.BlobStoreFactory};
 * the first one found is used. The deployment passes the bucket's deployed name as the
 * configuration property {@code cloudhopper.bucket.<name>.name} (environment variable
 * {@code CLOUDHOPPER_BUCKET_<NAME>_NAME}); without it, the name from {@code @Bucket} is used.
 *
 * <p>The stores a factory returns need not check the declared access:
 * {@link eu.cloudhopper.mc.runtime.BlobStores} does that before calling them. They throw
 * {@link eu.cloudhopper.mc.runtime.BlobAccessDeniedException} when the platform refuses an
 * operation, and {@link eu.cloudhopper.mc.runtime.BlobStoreException} for other failures.</p>
 */
public interface BlobStoreFactory {

    /**
     * @param name     the bucket name from {@code @Bucket(name)}
     * @param existing {@code @Bucket(existing)}
     * @return a store for the bucket; it may be cached and shared by all calls
     */
    BlobStore bucket(String name, boolean existing);
}
