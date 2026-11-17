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
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;

/**
 * {@code @EventTrigger(create = false)}: the functions "existingqueue" and "existingupload"
 * attach to a queue and a bucket that exist before the deployment (the binding creates them)
 * and receive their events.
 */
public class EventExistingResourceCompatibilityTest implements FeatureAwareTest {

    private static final String NAME = "dukehoff-cloudhopper-tck-existing";
    private static final int MAX_ATTEMPTS = 10;

    @Override
    public void run(TestContext context) throws Exception {
        String player = "tck-existing-" + UUID.randomUUID();
        context.sendQueueMessage(NAME, "{\"id\":2,\"name\":\"" + player + "\",\"ranking\":0}");
        String key = "tck/" + UUID.randomUUID() + ".txt";
        context.uploadObject(NAME, key, "uploaded by the Cloudhopper TCK");
        try {
            await(context, "existingqueue", "Existing queue message received: " + player);
            await(context, "existingupload", "Existing bucket object: " + NAME + "/" + key);
        } finally {
            context.deleteObject(NAME, key);
        }
    }

    private static void await(TestContext context, String function, String expected) throws InterruptedException {
        // delivery and log ingestion take a while on every platform
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Thread.sleep(30_000);
            List<String> logs = context.fetchLogs(function);
            if (logs.stream().anyMatch(line -> line.contains(expected))) {
                System.out.println("✅ " + function + " logged: " + expected);
                return;
            }
            System.out.println("⏳ " + function + " has not logged it yet (attempt " + attempt + ")");
        }
        Assert.fail(function + " did not log: " + expected);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(EventTrigger.class.getName(),
                List.of(EventTrigger.EventTriggerAttribute.SOURCE, EventTrigger.EventTriggerAttribute.NAME,
                        EventTrigger.EventTriggerAttribute.CREATE),
                Map.of(EventTrigger.EventTriggerAttribute.SOURCE, List.of("QUEUE", "STORAGE"))));
    }
}
