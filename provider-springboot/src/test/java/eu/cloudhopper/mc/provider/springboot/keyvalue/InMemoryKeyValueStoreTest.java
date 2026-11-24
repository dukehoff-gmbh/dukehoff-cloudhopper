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

import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InMemoryKeyValueStoreTest {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();

    private List<String> sortKeys(List<Item> items) {
        return items.stream().map(item -> item.key().sort()).toList();
    }

    @Test
    public void putGetAndConditionalWrites() {
        Key key = new Key("account:1", "profile");
        assertTrue(store.get(key).isEmpty());
        store.put(new Item(key, Map.of("name", "Ada", "count", 1L)));
        assertEquals(Map.of("name", "Ada", "count", 1L), store.get(key).orElseThrow().attributes());

        assertFalse(store.putIfAbsent(new Item(key, Map.of("name", "Bob"))));
        assertTrue(store.putIfAbsent(new Item(new Key("account:1", "other"), Map.of())));

        assertTrue(store.update(key, Map.of("count", 2L), Map.of("count", 1L)));
        assertFalse(store.update(key, Map.of("count", 3L), Map.of("count", 1L)));
        assertFalse(store.update(new Key("account:1", "none"), Map.of("count", 3L), Map.of()));
        Map<String, Object> remove = new HashMap<>();
        remove.put("name", null);
        Map<String, Object> absent = new HashMap<>();
        absent.put("lock", null);
        assertTrue(store.update(key, remove, absent));
        assertEquals(Map.of("count", 2L), store.get(key).orElseThrow().attributes());

        store.delete(key);
        store.delete(key);
        assertTrue(store.get(key).isEmpty());
    }

    @Test
    public void queryByPrefixInPages() {
        List<Item> items = new ArrayList<>();
        for (String sort : List.of("b", "a1", "a3", "a2", "c")) {
            items.add(new Item(new Key("p", sort), Map.of()));
        }
        store.batchPut(items);
        store.put(new Item(new Key("q", "a1"), Map.of()));

        assertEquals(List.of("a1", "a2", "a3", "b", "c"), sortKeys(store.query("p", "", 10, null).items()));
        assertEquals(List.of("a1", "a2", "a3"), sortKeys(store.query("p", "a")));
        assertEquals(List.of(), sortKeys(store.query("none", "")));

        List<String> paged = new ArrayList<>();
        String token = null;
        int pages = 0;
        do {
            Page page = store.query("p", "a", 2, token);
            paged.addAll(sortKeys(page.items()));
            token = page.nextPageToken();
            pages++;
        } while (token != null);
        assertEquals(List.of("a1", "a2", "a3"), paged);
        assertEquals(2, pages);
        assertNull(store.query("p", "a", 3, null).nextPageToken());

        store.batchDelete(items.stream().map(Item::key).toList());
        assertEquals(List.of(), store.query("p", ""));
    }
}
