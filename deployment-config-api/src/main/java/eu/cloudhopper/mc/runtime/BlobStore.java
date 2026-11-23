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
import java.util.Optional;

/**
 * Reads and writes the objects of a storage bucket; a function receives it as a parameter
 * annotated with {@link eu.cloudhopper.mc.annotations.Bucket}. Keys are the object names, such
 * as {@code reports/2026/11.json}; a {@code /} has no special meaning except for
 * {@link #list(String)} prefixes.
 *
 * <p>Every method checks the access the parameter declares and throws
 * {@link BlobAccessDeniedException} for an operation it does not allow; implementations throw it
 * as well when the platform refuses an operation.</p>
 */
public interface BlobStore {

    /**
     * Reads an object.
     *
     * @param key the object key
     * @return its content, or empty if there is no such object
     * @throws BlobStoreException if the bucket could not be read
     */
    Optional<byte[]> get(String key);

    /**
     * Creates or replaces an object.
     *
     * @param key         the object key
     * @param content     the content
     * @param contentType the media type, e.g. {@code application/json}
     * @throws BlobStoreException if the object could not be written
     */
    void put(String key, byte[] content, String contentType);

    /**
     * Creates or replaces an object of type {@code application/octet-stream}.
     *
     * @param key     the object key
     * @param content the content
     * @throws BlobStoreException if the object could not be written
     */
    default void put(String key, byte[] content) {
        put(key, content, "application/octet-stream");
    }

    /**
     * Deletes an object; deleting a missing object is not an error.
     *
     * @param key the object key
     * @throws BlobStoreException if the object could not be deleted
     */
    void delete(String key);

    /**
     * Lists the keys that start with a prefix, in lexicographic order; the implementation reads
     * all pages.
     *
     * @param prefix the prefix, {@code ""} for all keys
     * @return the keys
     * @throws BlobStoreException if the bucket could not be listed
     */
    List<String> list(String prefix);
}
