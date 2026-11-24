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

import eu.cloudhopper.mc.annotations.Table.Access;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves {@code @Table} parameters of functions; called by the generated handlers.
 */
public final class KeyValueStores {

    /** Attribute names: a letter, then up to 63 letters, digits and underscores. */
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    /** Characters not allowed in keys on every target. */
    private static final Pattern KEY_FORBIDDEN = Pattern.compile("[/\\\\#?\\p{Cntrl}]");
    private static final int KEY_LENGTH = 512;

    private static final Map<String, KeyValueStore> STORES = new ConcurrentHashMap<>();
    private static volatile KeyValueStoreFactory factory;

    private KeyValueStores() {
    }

    /**
     * The store for a table, created once per table, access and instance.
     *
     * @param name     the table name
     * @param access   what the parameter may do
     * @param existing whether the table exists outside the project
     * @return the store
     */
    public static KeyValueStore table(String name, Access access, boolean existing) {
        return STORES.computeIfAbsent(name + "|" + access + "|" + existing,
                key -> new Checked(name, access, factory().table(name, existing)));
    }

    /**
     * Replaces the factory found through {@link ServiceLoader}, e.g. by one that a framework
     * creates with its own dependency injection; {@code null} returns to the discovered one.
     *
     * @param replacement the factory to use
     */
    public static void setFactory(KeyValueStoreFactory replacement) {
        factory = replacement;
        STORES.clear();
    }

    private static KeyValueStoreFactory factory() {
        KeyValueStoreFactory current = factory;
        if (current == null) {
            synchronized (KeyValueStores.class) {
                if (factory == null) {
                    Iterator<KeyValueStoreFactory> found = ServiceLoader.load(KeyValueStoreFactory.class,
                            KeyValueStores.class.getClassLoader()).iterator();
                    factory = found.hasNext() ? found.next() : (name, existing) -> new Unsupported(name);
                }
                current = factory;
            }
        }
        return current;
    }

    /**
     * Converts an attribute value to the types every target stores: whole numbers to
     * {@link Long}, other numbers to {@link Double}, lists and maps recursively.
     *
     * @param value the value
     * @param path  where the value is, for the message
     * @return the converted value
     * @throws KeyValueStoreException for {@code null} and other types
     */
    static Object normalize(Object value, String path) {
        if (value instanceof String || value instanceof Boolean || value instanceof Long) {
            return value;
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new KeyValueStoreException(path + " is " + d + ", which no target stores");
            }
            return d == Math.rint(d) && Math.abs(d) < 0x1p53 ? (Object) (long) d : (Object) d;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                result.add(normalize(list.get(i), path + "[" + i + "]"));
            }
            return Collections.unmodifiableList(result);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String k)) {
                    throw new KeyValueStoreException(path + " has a key that is not a String: " + e.getKey());
                }
                result.put(k, normalize(e.getValue(), path + "." + k));
            }
            return Collections.unmodifiableMap(result);
        }
        throw new KeyValueStoreException(path + (value == null ? " is null; leave the attribute out instead"
                : " has the type " + value.getClass().getName()
                + "; use String, Long, Double, Boolean, List or Map"));
    }

    /** Checks the declared access, keys and attributes before calling the target's store. */
    private record Checked(String name, Access access, KeyValueStore store) implements KeyValueStore {

        @Override
        public Optional<Item> get(Key key) {
            requireRead("get");
            return store.get(checkKey(key)).map(this::read);
        }

        @Override
        public void put(Item item) {
            requireWrite("put");
            store.put(checkItem(item));
        }

        @Override
        public boolean putIfAbsent(Item item) {
            requireWrite("putIfAbsent");
            return store.putIfAbsent(checkItem(item));
        }

        @Override
        public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
            requireWrite("update");
            if (set == null || set.isEmpty()) {
                throw new KeyValueStoreException("update on table " + name + " needs attributes to set");
            }
            return store.update(checkKey(key), checkAttributes(set, true, "set"),
                    checkAttributes(expected == null ? Map.of() : expected, true, "expected"));
        }

        @Override
        public void delete(Key key) {
            requireWrite("delete");
            store.delete(checkKey(key));
        }

        @Override
        public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
            requireRead("query");
            checkKeyPart(partition, "partition");
            String prefix = sortKeyPrefix == null ? "" : sortKeyPrefix;
            if (!prefix.isEmpty()) {
                checkKeyPart(prefix, "sort key prefix");
            }
            if (limit < 1 || limit > 1000) {
                throw new KeyValueStoreException("The limit of a query on table " + name + " is 1 to 1000, not " + limit);
            }
            Page page = store.query(partition, prefix, limit, pageToken);
            return new Page(page.items().stream().map(this::read).toList(), page.nextPageToken());
        }

        @Override
        public void batchPut(Collection<Item> items) {
            requireWrite("batchPut");
            List<Item> checked = new ArrayList<>(items.size());
            for (Item item : items) {
                checked.add(checkItem(item));
            }
            if (!checked.isEmpty()) {
                store.batchPut(checked);
            }
        }

        @Override
        public void batchDelete(Collection<Key> keys) {
            requireWrite("batchDelete");
            List<Key> checked = new ArrayList<>(keys.size());
            for (Key key : keys) {
                checked.add(checkKey(key));
            }
            if (!checked.isEmpty()) {
                store.batchDelete(checked);
            }
        }

        /** The numbers of the target as Long or Double, as on every target. */
        @SuppressWarnings("unchecked")
        private Item read(Item item) {
            return new Item(item.key(), (Map<String, Object>) normalize(item.attributes(), "item"));
        }

        private Item checkItem(Item item) {
            if (item == null) {
                throw new KeyValueStoreException("An item of table " + name + " must not be null");
            }
            return new Item(checkKey(item.key()), checkAttributes(item.attributes(), false, "attributes"));
        }

        private Map<String, Object> checkAttributes(Map<String, Object> attributes, boolean nullAllowed, String what) {
            Map<String, Object> checked = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : attributes.entrySet()) {
                String attribute = e.getKey();
                if (attribute == null || !ATTRIBUTE_NAME.matcher(attribute).matches()) {
                    throw new KeyValueStoreException("The attribute name '" + attribute + "' in " + what + " for table "
                            + name + " is not allowed: use a letter, then up to 63 letters, digits and underscores");
                }
                checked.put(attribute, e.getValue() == null && nullAllowed ? null
                        : normalize(e.getValue(), what + "." + attribute));
            }
            return Collections.unmodifiableMap(checked);
        }

        private Key checkKey(Key key) {
            if (key == null) {
                throw new KeyValueStoreException("A key of table " + name + " must not be null");
            }
            checkKeyPart(key.partition(), "partition key");
            checkKeyPart(key.sort(), "sort key");
            return key;
        }

        private void checkKeyPart(String part, String what) {
            if (part == null || part.isEmpty() || part.length() > KEY_LENGTH || KEY_FORBIDDEN.matcher(part).find()) {
                throw new KeyValueStoreException("The " + what + " '" + part + "' of table " + name
                        + " is not allowed: use 1 to " + KEY_LENGTH
                        + " characters without /, \\, #, ? and control characters");
            }
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

        private KeyValueStoreException denied(String operation, String needed) {
            return new KeyValueAccessDeniedException(operation + " on table " + name + " needs @Table(access = "
                    + needed + "), the parameter declares " + access);
        }
    }

    /** No runtime module on the classpath: the build already warned about it. */
    private record Unsupported(String name) implements KeyValueStore {

        @Override
        public Optional<Item> get(Key key) {
            throw fail();
        }

        @Override
        public void put(Item item) {
            throw fail();
        }

        @Override
        public boolean putIfAbsent(Item item) {
            throw fail();
        }

        @Override
        public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
            throw fail();
        }

        @Override
        public void delete(Key key) {
            throw fail();
        }

        @Override
        public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
            throw fail();
        }

        @Override
        public void batchPut(Collection<Item> items) {
            throw fail();
        }

        @Override
        public void batchDelete(Collection<Key> keys) {
            throw fail();
        }

        private KeyValueStoreException fail() {
            return new KeyValueStoreException("No KeyValueStore implementation for table " + name
                    + " on this target: add the runtime module of the target, or check the build warnings");
        }
    }
}
