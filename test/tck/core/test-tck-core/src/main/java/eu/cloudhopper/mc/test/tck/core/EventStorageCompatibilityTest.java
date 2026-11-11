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

import eu.cloudhopper.mc.annotations.EventTrigger;
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.List;
import java.util.UUID;
import org.junit.Assert;

/**
 * Uploads an object with a unique key to the bucket of {@code StorageUploadFunction} and expects
 * the function to log that key; deletes the object afterwards.
 */
public class EventStorageCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "storageupload";
    private static final String BUCKET_NAME = "dukehoff-cloudhopper-tck-uploads";
    private static final int MAX_ATTEMPTS = 10;

    @Override
    public void run(TestContext context) throws Exception {
        String key = "tck/" + UUID.randomUUID() + ".txt";
        String content = "uploaded by the Cloudhopper TCK";
        context.uploadObject(BUCKET_NAME, key, content);
        try {
            // the bucket name in the event has no deployment prefix
            String expected = "Object uploaded: " + BUCKET_NAME + "/" + key + " (" + content.length() + " bytes)";
            boolean received = false;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS && !received; attempt++) {
                Thread.sleep(30_000);
                List<String> logs = context.fetchLogs(FUNCTION_NAME);
                System.out.println("📜 Fetched logs (attempt " + attempt + "):");
                logs.forEach(System.out::println);
                received = logs.stream().anyMatch(line -> line.contains(expected));
            }
            Assert.assertTrue("Storage-triggered function did not log: " + expected, received);
        } finally {
            context.deleteObject(BUCKET_NAME, key);
        }
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(EventTrigger.class.getName(),
                List.of(EventTrigger.EventTriggerAttribute.SOURCE, EventTrigger.EventTriggerAttribute.NAME)));
    }
}
