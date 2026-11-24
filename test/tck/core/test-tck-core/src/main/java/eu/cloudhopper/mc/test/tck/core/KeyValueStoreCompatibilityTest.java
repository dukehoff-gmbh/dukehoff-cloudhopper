package eu.cloudhopper.mc.test.tck.core;


/*-
 * #%L
 * test-tck-core - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;

/**
 * {@code @Table}: the function "kvwriter" writes, conditionally creates and updates, queries and
 * deletes items of a table that the function "kvreader" may only read; the reader's
 * {@code put} is refused. Each run uses a partition of its own.
 */
public class KeyValueStoreCompatibilityTest implements FeatureAwareTest {

    static final String WRITER = "kvwriter";
    static final String READER = "kvreader";

    /** The attributes the functions give an item named "Ada", with sorted names. */
    private static final String ADA = "{active=true, count=1, meta={k=v, n=2}, name=Ada, ratio=0.5, tags=[a, b]}";

    @Override
    public void run(TestContext context) throws Exception {
        String partition = "tck:" + UUID.randomUUID();
        try {
            Assert.assertEquals("stored", call(context, WRITER, "put", partition, "a", "Ada"));
            Assert.assertEquals("the reader reads the types the writer stored",
                    ADA, call(context, READER, "get", partition, "a", null));
            Assert.assertEquals("missing", call(context, READER, "get", partition, "none", null));

            Assert.assertEquals("exists", call(context, WRITER, "putIfAbsent", partition, "a", "Bob"));
            Assert.assertEquals(ADA, call(context, READER, "get", partition, "a", null));
            Assert.assertEquals("created", call(context, WRITER, "putIfAbsent", partition, "b", "Bob"));

            Assert.assertEquals("updated", call(context, WRITER, "update", partition, "a", null));
            Assert.assertEquals("count is no longer 1", "conflict", call(context, WRITER, "update", partition, "a", null));
            Assert.assertEquals("no such item", "conflict", call(context, WRITER, "update", partition, "none", null));
            Assert.assertEquals("missing", call(context, READER, "get", partition, "none", null));
            Assert.assertEquals("updated", call(context, WRITER, "remove", partition, "a", null));
            Assert.assertEquals("{active=true, count=2, meta={k=v, n=2}, ratio=0.5, tags=[a, b]}",
                    call(context, READER, "get", partition, "a", null));

            Assert.assertEquals("stored 3", call(context, WRITER, "batchPut", partition, null, null));
            Assert.assertEquals("c1,c2,c3", query(context, "query", partition, "c"));
            Assert.assertEquals("a,b,c1,c2,c3", query(context, "query", partition, ""));
            Assert.assertEquals("a,b,c1,c2,c3", query(context, "queryPaged", partition, ""));
            Assert.assertEquals("c2", query(context, "query", partition, "c2"));

            Assert.assertEquals("a READ parameter refuses put", "denied", call(context, READER, "put", partition, "a", "Eve"));
        } finally {
            Assert.assertEquals("deleted 3", call(context, WRITER, "batchDelete", partition, null, null));
            Assert.assertEquals("deleted", call(context, WRITER, "delete", partition, "a", null));
            Assert.assertEquals("deleted", call(context, WRITER, "delete", partition, "b", null));
        }
        Assert.assertEquals("", query(context, "query", partition, ""));
    }

    private static Object query(TestContext context, String op, String partition, String prefix) {
        Map<String, Object> request = new HashMap<>(Map.of("op", op, "partition", partition, "prefix", prefix));
        Object result = context.invokeFunctionDirect(READER, request);
        System.out.println("🗂️ " + READER + " " + op + " " + partition + " '" + prefix + "': " + result);
        return result;
    }

    static Object call(TestContext context, String function, String op, String partition, String sort, String value) {
        Map<String, Object> request = new HashMap<>(Map.of("op", op, "partition", partition));
        if (sort != null) {
            request.put("sort", sort);
        }
        if (value != null) {
            request.put("value", value);
        }
        Object result = context.invokeFunctionDirect(function, request);
        System.out.println("🗂️ " + function + " " + op + " " + partition + "/" + sort + ": " + result);
        return result;
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(
                new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(Table.class.getName(),
                        List.of(Table.TableAttribute.NAME, Table.TableAttribute.ACCESS)));
    }
}
