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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * A table in the memory of the application, for local runs and tests: the items are lost when it
 * stops, and every instance has its own. Conditional writes are atomic within the instance.
 */
public class InMemoryKeyValueStore implements KeyValueStore {

    /** The items by partition, then by sort key. */
    private final Map<String, NavigableMap<String, Map<String, Object>>> partitions = new ConcurrentHashMap<>();

    @Override
    public Optional<Item> get(Key key) {
        NavigableMap<String, Map<String, Object>> partition = partitions.get(key.partition());
        Map<String, Object> attributes = partition == null ? null : partition.get(key.sort());
        return Optional.ofNullable(attributes).map(a -> new Item(key, a));
    }

    @Override
    public synchronized void put(Item item) {
        partition(item.key()).put(item.key().sort(), Map.copyOf(item.attributes()));
    }

    @Override
    public synchronized boolean putIfAbsent(Item item) {
        return partition(item.key()).putIfAbsent(item.key().sort(), Map.copyOf(item.attributes())) == null;
    }

    @Override
    public synchronized boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
        NavigableMap<String, Map<String, Object>> partition = partitions.get(key.partition());
        Map<String, Object> current = partition == null ? null : partition.get(key.sort());
        if (current == null) {
            return false;
        }
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            if (!Objects.equals(current.get(e.getKey()), e.getValue())) {
                return false;
            }
        }
        Map<String, Object> changed = new LinkedHashMap<>(current);
        set.forEach((name, value) -> {
            if (value == null) {
                changed.remove(name);
            } else {
                changed.put(name, value);
            }
        });
        partition.put(key.sort(), Map.copyOf(changed));
        return true;
    }

    @Override
    public synchronized void delete(Key key) {
        NavigableMap<String, Map<String, Object>> partition = partitions.get(key.partition());
        if (partition != null) {
            partition.remove(key.sort());
        }
    }

    @Override
    public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
        NavigableMap<String, Map<String, Object>> items = partitions.get(partition);
        if (items == null) {
            return new Page(List.of(), null);
        }
        NavigableMap<String, Map<String, Object>> from = pageToken == null ? items.tailMap(sortKeyPrefix, true)
                : items.tailMap(decode(pageToken), false);
        List<Item> page = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : from.entrySet()) {
            if (!e.getKey().startsWith(sortKeyPrefix)) {
                break;
            }
            if (page.size() == limit) {
                return new Page(page, encode(page.get(page.size() - 1).key().sort()));
            }
            page.add(new Item(new Key(partition, e.getKey()), e.getValue()));
        }
        return new Page(page, null);
    }

    @Override
    public void batchPut(Collection<Item> items) {
        items.forEach(this::put);
    }

    @Override
    public void batchDelete(Collection<Key> keys) {
        keys.forEach(this::delete);
    }

    private NavigableMap<String, Map<String, Object>> partition(Key key) {
        return partitions.computeIfAbsent(key.partition(), p -> new ConcurrentSkipListMap<>());
    }

    private static String encode(String sort) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sort.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String token) {
        try {
            return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new KeyValueStoreException("Invalid page token: " + token, e);
        }
    }
}
