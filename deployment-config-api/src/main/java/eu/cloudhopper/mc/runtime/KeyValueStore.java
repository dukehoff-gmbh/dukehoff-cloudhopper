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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes the items of a table; a function receives it as a parameter annotated with
 * {@link eu.cloudhopper.mc.annotations.Table}.
 *
 * <p>An item has a {@link Key} of a partition and a sort key, and attributes. Items of one
 * partition are read together with {@link #query(String, String, int, String)}, in the order of
 * their sort keys; there are no queries across partitions and no secondary indexes. An
 * application that needs to list items of several partitions keeps an index partition of its
 * own.</p>
 *
 * <p>Attribute names start with a letter, followed by up to 63 letters, digits and
 * underscores. Attribute values are {@link String}, {@link Long}, {@link Double},
 * {@link Boolean}, {@link List} and {@link Map} with {@link String} keys, nested in any depth;
 * {@link Integer}, {@link Short}, {@link Byte} and {@link Float} are accepted and stored as
 * {@code Long} and {@code Double}. Numbers are read back as {@code Long} when they are whole and
 * as {@code Double} otherwise, on every target. {@code null} is not stored: leave the attribute
 * out.</p>
 *
 * <p>Reads are strongly consistent: {@code get} and {@code query} see every write that has
 * completed. An item may have at most 400 KB, the limit of the most restrictive target.</p>
 *
 * <p>Every method checks the access the parameter declares and throws
 * {@link KeyValueAccessDeniedException} for an operation it does not allow; implementations
 * throw it as well when the platform refuses an operation.</p>
 */
public interface KeyValueStore {

    /**
     * The key of an item. Both parts are non-empty strings of at most 512 characters without
     * {@code /}, {@code \}, {@code #}, {@code ?} and control characters, which not every target
     * allows in keys; use e.g. {@code :} to compose keys such as {@code account:42}.
     */
    final class Key {

        private final String partition;
        private final String sort;

        /**
         * @param partition the partition key
         * @param sort      the sort key within the partition
         */
        public Key(String partition, String sort) {
            this.partition = partition;
            this.sort = sort;
        }

        /**
         * @return the partition key
         */
        public String partition() {
            return partition;
        }

        /**
         * @return the sort key within the partition
         */
        public String sort() {
            return sort;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && Objects.equals(partition, ((Key) o).partition)
                    && Objects.equals(sort, ((Key) o).sort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partition, sort);
        }

        @Override
        public String toString() {
            return "Key[" + partition + ", " + sort + "]";
        }
    }

    /**
     * An item: its key and attributes.
     */
    final class Item {

        private final Key key;
        private final Map<String, Object> attributes;

        /**
         * @param key        the key
         * @param attributes the attributes, without the key
         */
        public Item(Key key, Map<String, Object> attributes) {
            this.key = key;
            this.attributes = attributes == null ? Map.of() : attributes;
        }

        /**
         * @return the key
         */
        public Key key() {
            return key;
        }

        /**
         * @return the attributes, without the key
         */
        public Map<String, Object> attributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item && Objects.equals(key, ((Item) o).key)
                    && Objects.equals(attributes, ((Item) o).attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, attributes);
        }

        @Override
        public String toString() {
            return "Item[" + key + ", " + attributes + "]";
        }
    }

    /**
     * A page of a query.
     */
    final class Page {

        private final List<Item> items;
        private final String nextPageToken;

        /**
         * @param items         the items, in the order of their sort keys
         * @param nextPageToken the token of the next page, or {@code null} if this is the last page
         */
        public Page(List<Item> items, String nextPageToken) {
            this.items = items == null ? List.of() : items;
            this.nextPageToken = nextPageToken;
        }

        /**
         * @return the items, in the order of their sort keys
         */
        public List<Item> items() {
            return items;
        }

        /**
         * @return the token of the next page, or {@code null} if this is the last page
         */
        public String nextPageToken() {
            return nextPageToken;
        }

        @Override
        public String toString() {
            return "Page[" + items + ", " + nextPageToken + "]";
        }
    }

    /**
     * Reads an item.
     *
     * @param key the key
     * @return the item, or empty if there is none
     * @throws KeyValueStoreException if the table could not be read
     */
    Optional<Item> get(Key key);

    /**
     * Creates or replaces an item.
     *
     * @param item the item
     * @throws KeyValueStoreException if the item could not be written
     */
    void put(Item item);

    /**
     * Creates an item if there is none with its key.
     *
     * @param item the item
     * @return {@code true} if it was created, {@code false} if an item with its key exists
     * @throws KeyValueStoreException if the item could not be written
     */
    boolean putIfAbsent(Item item);

    /**
     * Changes attributes of an existing item if its attributes have the expected values
     * (optimistic concurrency). Attributes not named in {@code set} stay as they are.
     *
     * @param key      the key
     * @param set      the attributes to set; a {@code null} value removes the attribute
     * @param expected the values the attributes must have; a {@code null} value means the
     *                 attribute must be absent. Empty: the item must only exist
     * @return {@code true} if the item was changed, {@code false} if there is no such item or an
     *         attribute does not have the expected value
     * @throws KeyValueStoreException if the item could not be written
     */
    boolean update(Key key, Map<String, Object> set, Map<String, Object> expected);

    /**
     * Deletes an item; deleting a missing item is not an error.
     *
     * @param key the key
     * @throws KeyValueStoreException if the item could not be deleted
     */
    void delete(Key key);

    /**
     * Reads a page of the items of a partition whose sort keys start with a prefix, in the order
     * of their sort keys.
     *
     * @param partition     the partition key
     * @param sortKeyPrefix the prefix of the sort keys, {@code ""} for all items of the partition
     * @param limit         the maximum number of items of the page, 1 to 1000
     * @param pageToken     the {@link Page#nextPageToken()} of the previous page, or {@code null}
     *                      for the first page
     * @return the page; it may have fewer items than the limit even if more follow
     * @throws KeyValueStoreException if the table could not be read
     */
    Page query(String partition, String sortKeyPrefix, int limit, String pageToken);

    /**
     * Reads all items of a partition whose sort keys start with a prefix, page by page.
     *
     * @param partition     the partition key
     * @param sortKeyPrefix the prefix of the sort keys, {@code ""} for all items of the partition
     * @return the items, in the order of their sort keys
     * @throws KeyValueStoreException if the table could not be read
     */
    default List<Item> query(String partition, String sortKeyPrefix) {
        List<Item> items = new ArrayList<>();
        String token = null;
        do {
            Page page = query(partition, sortKeyPrefix, 1000, token);
            items.addAll(page.items());
            token = page.nextPageToken();
        } while (token != null);
        return items;
    }

    /**
     * Creates or replaces items, split into the batches the target allows. The batches are not
     * atomic: if one fails, the items of earlier batches stay written.
     *
     * @param items the items
     * @throws KeyValueStoreException if items could not be written
     */
    void batchPut(Collection<Item> items);

    /**
     * Deletes items, split into the batches the target allows; not atomic, like
     * {@link #batchPut(Collection)}.
     *
     * @param keys the keys
     * @throws KeyValueStoreException if items could not be deleted
     */
    void batchDelete(Collection<Key> keys);
}
