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

import java.util.Objects;

/**
 * An object was created in the bucket an {@code @EventTrigger(source = EventSource.STORAGE)}
 * function listens to.
 *
 * <p>Declare a parameter of this type as the function's body parameter to receive it. The event
 * describes the object; the function reads the content itself if it needs it, with the storage
 * client of the platform.</p>
 */
public final class StorageEvent {

    /**
     * What happened to the object.
     */
    public enum Kind {
        /**
         * The object was created or overwritten.
         */
        CREATED
    }

    private final String bucket;
    private final String key;
    private final long size;
    private final Kind kind;

    /**
     * Creates a storage event.
     *
     * @param bucket the bucket (on Azure: the container) the object is in
     * @param key    the object key (on Azure: the blob name), including any "folders"
     * @param size   the object size in bytes; -1 if unknown
     * @param kind   what happened to the object
     */
    public StorageEvent(String bucket, String key, long size, Kind kind) {
        this.bucket = bucket;
        this.key = key;
        this.size = size;
        this.kind = kind;
    }

    /**
     * @return the bucket (on Azure: the container) the object is in, without any name prefix
     *         the deployment added
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * @return the object key (on Azure: the blob name), for example {@code invoices/2026/42.pdf}
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the object size in bytes; -1 if the platform does not say
     */
    public long getSize() {
        return size;
    }

    /**
     * @return what happened to the object
     */
    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StorageEvent)) {
            return false;
        }
        StorageEvent other = (StorageEvent) o;
        return size == other.size
                && Objects.equals(bucket, other.bucket)
                && Objects.equals(key, other.key)
                && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucket, key, size, kind);
    }

    @Override
    public String toString() {
        return "StorageEvent{bucket=" + bucket + ", key=" + key + ", size=" + size + ", kind=" + kind + "}";
    }
}
