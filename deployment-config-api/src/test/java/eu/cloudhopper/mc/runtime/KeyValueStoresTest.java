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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeyValueStoresTest {

    /** What the target's store received and returns. */
    private final Map<Key, Map<String, Object>> stored = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        KeyValueStores.setFactory((name, existing) -> new KeyValueStore() {
            @Override
            public Optional<Item> get(Key key) {
                calls.add("get");
                return Optional.ofNullable(stored.get(key)).map(a -> new Item(key, a));
            }

            @Override
            public void put(Item item) {
                calls.add("put");
                stored.put(item.key(), item.attributes());
            }

            @Override
            public boolean putIfAbsent(Item item) {
                calls.add("putIfAbsent");
                return stored.putIfAbsent(item.key(), item.attributes()) == null;
            }

            @Override
            public boolean update(Key key, Map<String, Object> set, Map<String, Object> expected) {
                calls.add("update " + set + " " + expected);
                return true;
            }

            @Override
            public void delete(Key key) {
                calls.add("delete");
            }

            @Override
            public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
                calls.add("query " + partition + " '" + sortKeyPrefix + "' " + limit + " " + pageToken);
                return new Page(List.of(new Item(new Key(partition, "a"), Map.of("n", 2.0))), null);
            }

            @Override
            public void batchPut(Collection<Item> items) {
                calls.add("batchPut " + items.size());
            }

            @Override
            public void batchDelete(Collection<Key> keys) {
                calls.add("batchDelete " + keys.size());
            }
        });
    }

    @AfterEach
    public void tearDown() {
        KeyValueStores.setFactory(null);
    }

    @Test
    public void numbersAreStoredAndReadAsLongOrDouble() {
        KeyValueStore table = KeyValueStores.table("accounts", Access.READ_WRITE, false);
        Key key = new Key("account:1", "profile");
        table.put(new Item(key, Map.of("count", 3, "ratio", 0.5f, "tags", List.of(1, "a"),
                "nested", Map.of("small", (short) 7))));
        Map<String, Object> attributes = stored.get(key);
        assertEquals(3L, attributes.get("count"));
        assertEquals(0.5, attributes.get("ratio"));
        assertEquals(List.of(1L, "a"), attributes.get("tags"));
        assertEquals(Map.of("small", 7L), attributes.get("nested"));

        stored.put(key, Map.of("whole", 2.0, "fraction", 2.5, "list", List.of(4.0)));
        Map<String, Object> read = table.get(key).orElseThrow().attributes();
        assertEquals(2L, read.get("whole"));
        assertEquals(2.5, read.get("fraction"));
        assertEquals(List.of(4L), read.get("list"));
        assertEquals(2L, table.query("account:1", "").get(0).attributes().get("n"));
    }

    @Test
    public void theDeclaredAccessIsChecked() {
        KeyValueStore reader = KeyValueStores.table("accounts", Access.READ, false);
        Item item = new Item(new Key("p", "s"), Map.of("a", "b"));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.put(item));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.putIfAbsent(item));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.update(item.key(), Map.of("a", "c"), Map.of()));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.delete(item.key()));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.batchPut(List.of(item)));
        assertThrows(KeyValueAccessDeniedException.class, () -> reader.batchDelete(List.of(item.key())));
        reader.get(item.key());

        KeyValueStore writer = KeyValueStores.table("accounts", Access.WRITE, false);
        assertThrows(KeyValueAccessDeniedException.class, () -> writer.get(item.key()));
        assertThrows(KeyValueAccessDeniedException.class, () -> writer.query("p", "", 10, null));
        writer.put(item);
        assertEquals(List.of("get", "put"), calls);
    }

    @Test
    public void keysAttributesAndValuesAreChecked() {
        KeyValueStore table = KeyValueStores.table("accounts", Access.READ_WRITE, false);
        for (Key key : List.of(new Key("", "s"), new Key("p", ""), new Key("a/b", "s"), new Key("p", "a#b"),
                new Key("p", "a?"), new Key("p\n", "s"), new Key("p", "x".repeat(513)), new Key(null, "s"))) {
            assertThrows(KeyValueStoreException.class, () -> table.get(key), key.toString());
        }
        Key key = new Key("p", "s");
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("a", null);
        for (Map<String, Object> attributes : List.of(Map.<String, Object>of("_internal", 1), Map.<String, Object>of("1st", 1),
                Map.<String, Object>of("a-b", 1), Map.<String, Object>of("date", new java.util.Date()),
                Map.<String, Object>of("nan", Double.NaN), withNull)) {
            assertThrows(KeyValueStoreException.class, () -> table.put(new Item(key, attributes)), attributes.toString());
        }
        assertThrows(KeyValueStoreException.class, () -> table.query("p", "", 0, null));
        assertThrows(KeyValueStoreException.class, () -> table.query("p", "", 1001, null));
        assertThrows(KeyValueStoreException.class, () -> table.update(key, Map.of(), Map.of()));
        assertTrue(calls.isEmpty());
    }

    @Test
    public void updateRemovesAndExpectsAbsentAttributesWithNull() {
        KeyValueStore table = KeyValueStores.table("accounts", Access.READ_WRITE, false);
        Map<String, Object> set = new java.util.LinkedHashMap<>();
        set.put("gone", null);
        set.put("count", 2);
        Map<String, Object> expected = new HashMap<>();
        expected.put("lock", null);
        assertTrue(table.update(new Key("p", "s"), set, expected));
        assertEquals("update {gone=null, count=2} {lock=null}", calls.get(0));
    }

    @Test
    public void emptyBatchesDoNotCallTheTarget() {
        KeyValueStore table = KeyValueStores.table("accounts", Access.READ_WRITE, false);
        table.batchPut(List.of());
        table.batchDelete(List.of());
        assertFalse(calls.contains("batchPut 0"));
        assertTrue(calls.isEmpty());
    }

    @Test
    public void withoutRuntimeModuleTheOperationsFail() {
        KeyValueStores.setFactory(null);
        KeyValueStore table = KeyValueStores.table("orphan", Access.READ, false);
        KeyValueStoreException e = assertThrows(KeyValueStoreException.class, () -> table.get(new Key("p", "s")));
        assertTrue(e.getMessage().contains("No KeyValueStore implementation for table orphan"));
    }
}
