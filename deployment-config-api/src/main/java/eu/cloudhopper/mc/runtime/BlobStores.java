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

import eu.cloudhopper.mc.annotations.Bucket.Access;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@code @Bucket} parameters of functions; called by the generated handlers.
 */
public final class BlobStores {

    private static final Map<String, BlobStore> STORES = new ConcurrentHashMap<>();
    private static volatile BlobStoreFactory factory;

    private BlobStores() {
    }

    /**
     * The store for a bucket, created once per bucket, access and instance.
     *
     * @param name     the bucket name
     * @param access   what the parameter may do
     * @param existing whether the bucket exists outside the project
     * @return the store
     */
    public static BlobStore bucket(String name, Access access, boolean existing) {
        return STORES.computeIfAbsent(name + "|" + access + "|" + existing,
                key -> new Checked(name, access, factory().bucket(name, existing)));
    }

    /**
     * Replaces the factory found through {@link ServiceLoader}, e.g. by one that a framework
     * creates with its own dependency injection; {@code null} returns to the discovered one.
     *
     * @param replacement the factory to use
     */
    public static void setFactory(BlobStoreFactory replacement) {
        factory = replacement;
        STORES.clear();
    }

    private static BlobStoreFactory factory() {
        BlobStoreFactory current = factory;
        if (current == null) {
            synchronized (BlobStores.class) {
                if (factory == null) {
                    Iterator<BlobStoreFactory> found = ServiceLoader.load(BlobStoreFactory.class,
                            BlobStores.class.getClassLoader()).iterator();
                    factory = found.hasNext() ? found.next() : (name, existing) -> new Unsupported(name);
                }
                current = factory;
            }
        }
        return current;
    }

    /** Checks the declared access and the key before calling the target's store. */
    private record Checked(String name, Access access, BlobStore store) implements BlobStore {

        @Override
        public Optional<byte[]> get(String key) {
            requireRead("get");
            return store.get(checkKey(key));
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            requireWrite("put");
            if (content == null) {
                throw new BlobStoreException("put needs content, not null");
            }
            store.put(checkKey(key), content, contentType == null ? "application/octet-stream" : contentType);
        }

        @Override
        public void delete(String key) {
            requireWrite("delete");
            store.delete(checkKey(key));
        }

        @Override
        public List<String> list(String prefix) {
            requireRead("list");
            return store.list(prefix == null ? "" : prefix);
        }

        private void requireRead(String operation) {
            if (!access.canRead()) {
                throw denied(operation, "READ or READ_WRITE");
            }
        }

        private void requireWrite(String operation) {
            if (!access.canWrite()) {
                throw denied(operation, "WRITE or READ_WRITE");
            }
        }

        private BlobStoreException denied(String operation, String needed) {
            return new BlobAccessDeniedException(operation + " on bucket " + name + " needs @Bucket(access = "
                    + needed + "), the parameter declares " + access);
        }

        private String checkKey(String key) {
            if (key == null || key.isEmpty()) {
                throw new BlobStoreException("The key of an object in bucket " + name + " must not be empty");
            }
            return key;
        }
    }

    /** No runtime module on the classpath: the build already warned about it. */
    private record Unsupported(String name) implements BlobStore {

        @Override
        public Optional<byte[]> get(String key) {
            throw fail();
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            throw fail();
        }

        @Override
        public void delete(String key) {
            throw fail();
        }

        @Override
        public List<String> list(String prefix) {
            throw fail();
        }

        private BlobStoreException fail() {
            return new BlobStoreException("No BlobStore implementation for bucket " + name
                    + " on this target: add the runtime module of the target, or check the build warnings");
        }
    }
}
