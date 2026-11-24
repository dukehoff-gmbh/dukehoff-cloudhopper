package eu.cloudhopper.mc.test.functions;


/*-
 * #%L
 * demo - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.Table;
import eu.cloudhopper.mc.annotations.Table.Access;
import eu.cloudhopper.mc.runtime.KeyValueAccessDeniedException;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStore.Item;
import eu.cloudhopper.mc.runtime.KeyValueStore.Key;
import eu.cloudhopper.mc.runtime.KeyValueStore.Page;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Two functions on one table: "kvwriter" may read and write it, "kvreader" only read. The
 * reader's {@code put} goes through its {@code @Table} parameter, which refuses it;
 * {@code putUnchecked} bypasses that check and calls the target's store directly, so that only
 * the platform's permissions of the reader's identity can refuse it. Targets without
 * {@code @Table} warn at build time and fail the calls.
 *
 * <p>The functions build the items themselves, so that the attribute types do not depend on the
 * JSON mapping of the target. An item has the attributes {@code name} (the request's value),
 * {@code count} (1), {@code ratio} (0.5), {@code active} (true), {@code tags} ([a, b]) and
 * {@code meta} ({k=v, n=2}); {@code get} returns them with sorted names.</p>
 */
public class KeyValueFunctions {

    static final String TABLE = "cloudhoppertck";
    /** The sort keys of the batch operations. */
    private static final List<String> BATCH = List.of("c1", "c2", "c3");

    @Function(name = "kvwriter", memory = 256, identity = "tck-shared")
    public String write(KeyValueRequest request, @Table(name = TABLE, access = Access.READ_WRITE) KeyValueStore table) {
        return run(request, table);
    }

    @Function(name = "kvreader", memory = 256, identity = "tck-reader")
    public String read(KeyValueRequest request, @Table(name = TABLE) KeyValueStore table) {
        if ("putUnchecked".equals(request.getOp())) {
            KeyValueStoreFactory factory = ServiceLoader.load(KeyValueStoreFactory.class, KeyValueFunctions.class.getClassLoader())
                    .findFirst().orElseThrow();
            request.setOp("put");
            return run(request, factory.table(TABLE, false));
        }
        return run(request, table);
    }

    private static String run(KeyValueRequest request, KeyValueStore table) {
        String partition = request.getPartition();
        Key key = new Key(partition, request.getSort());
        try {
            switch (request.getOp()) {
                case "put":
                    table.put(item(key, request.getValue()));
                    return "stored";
                case "get":
                    return table.get(key).map(item -> render(item.attributes())).orElse("missing");
                case "putIfAbsent":
                    return table.putIfAbsent(item(key, request.getValue())) ? "created" : "exists";
                case "update":
                    // count 1 -> 2, only if it is still 1
                    return table.update(key, Map.of("count", 2L), Map.of("count", 1L)) ? "updated" : "conflict";
                case "remove": {
                    Map<String, Object> remove = new HashMap<>();
                    remove.put("name", null);
                    return table.update(key, remove, Map.of()) ? "updated" : "conflict";
                }
                case "delete":
                    table.delete(key);
                    return "deleted";
                case "query":
                    return sortKeys(table.query(partition, request.getPrefix()));
                case "queryPaged": {
                    // pages of at most 1 item, following the tokens
                    List<Item> items = new ArrayList<>();
                    String token = null;
                    int pages = 0;
                    do {
                        Page page = table.query(partition, request.getPrefix(), 1, token);
                        if (page.items().size() > 1) {
                            return "page with " + page.items().size() + " items, limit 1";
                        }
                        items.addAll(page.items());
                        token = page.nextPageToken();
                    } while (token != null && ++pages < 100);
                    return sortKeys(items);
                }
                case "batchPut":
                    table.batchPut(BATCH.stream().map(sort -> item(new Key(partition, sort), sort)).collect(Collectors.toList()));
                    return "stored " + BATCH.size();
                case "batchDelete":
                    table.batchDelete(BATCH.stream().map(sort -> new Key(partition, sort)).collect(Collectors.toList()));
                    return "deleted " + BATCH.size();
                default:
                    return "unknown operation " + request.getOp();
            }
        } catch (KeyValueAccessDeniedException e) {
            System.out.println("Table operation refused: " + e.getMessage());
            return "denied";
        } catch (KeyValueStoreException e) {
            System.out.println("Table operation failed: " + e.getMessage());
            return "failed: " + e.getMessage();
        }
    }

    private static Item item(Key key, String name) {
        return new Item(key, Map.of(
                "name", name,
                "count", 1L,
                "ratio", 0.5,
                "active", true,
                "tags", List.of("a", "b"),
                "meta", Map.of("k", "v", "n", 2L)));
    }

    private static String sortKeys(List<Item> items) {
        return items.stream().map(item -> item.key().sort()).collect(Collectors.joining(","));
    }

    /** The attributes with sorted names, also in nested maps. */
    private static String render(Object value) {
        if (value instanceof Map) {
            Map<String, String> sorted = new TreeMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> sorted.put(String.valueOf(k), render(v)));
            return sorted.toString();
        }
        if (value instanceof List) {
            return ((List<?>) value).stream().map(KeyValueFunctions::render).collect(Collectors.toList()).toString();
        }
        return String.valueOf(value);
    }
}
