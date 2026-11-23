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

import eu.cloudhopper.mc.annotations.Bucket;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;

/**
 * {@code @Bucket}: the function "blobwriter" writes, lists and deletes an object of a bucket that
 * the function "blobreader" may only read; the reader's {@code put} is refused.
 */
public class BlobStoreCompatibilityTest implements FeatureAwareTest {

    static final String WRITER = "blobwriter";
    static final String READER = "blobreader";

    @Override
    public void run(TestContext context) throws Exception {
        String id = UUID.randomUUID().toString();
        String key = "tck/" + id + ".txt";
        String content = "tck-blob-" + id;
        try {
            Assert.assertEquals("stored", call(context, WRITER, "put", key, content));
            Assert.assertEquals("the reader reads what the writer stored", content, call(context, READER, "get", key, null));
            Assert.assertEquals(key, call(context, WRITER, "list", "tck/" + id, null));
            Assert.assertEquals("missing", call(context, READER, "get", "tck/" + id + ".none", null));
            Assert.assertEquals("a READ parameter refuses put", "denied", call(context, READER, "put", key, "overwritten"));
            Assert.assertEquals(content, call(context, READER, "get", key, null));
        } finally {
            Assert.assertEquals("deleted", call(context, WRITER, "delete", key, null));
        }
        Assert.assertEquals("missing", call(context, READER, "get", key, null));
        Assert.assertEquals("", call(context, WRITER, "list", "tck/" + id, null));
    }

    static Object call(TestContext context, String function, String op, String key, String content) {
        Map<String, Object> request = content == null
                ? Map.of("op", op, "key", key)
                : Map.of("op", op, "key", key, "content", content);
        Object result = context.invokeFunctionDirect(function, request);
        System.out.println("🪣 " + function + " " + op + " " + key + ": " + result);
        return result;
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(
                new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(Bucket.class.getName(),
                        List.of(Bucket.BucketAttribute.NAME, Bucket.BucketAttribute.ACCESS)));
    }
}
