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
 * Sends a player with a unique name to the queue of {@code QueueMessageFunction} and expects the
 * function to log that name.
 */
public class EventQueueCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "queuemessage";
    private static final String QUEUE_NAME = "cloudhopper-tck-players";
    private static final int MAX_ATTEMPTS = 10;

    @Override
    public void run(TestContext context) throws Exception {
        String name = "tck-" + UUID.randomUUID();
        context.sendQueueMessage(QUEUE_NAME, "{\"id\":1,\"name\":\"" + name + "\",\"ranking\":0}");

        // delivery and log ingestion take a while on every platform
        boolean received = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !received; attempt++) {
            Thread.sleep(30_000);
            List<String> logs = context.fetchLogs(FUNCTION_NAME);
            System.out.println("📜 Fetched logs (attempt " + attempt + "):");
            logs.forEach(System.out::println);
            received = logs.stream().anyMatch(line -> line.contains("Queue message received: " + name));
        }
        Assert.assertTrue("Queue-triggered function did not log the message " + name, received);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(EventTrigger.class.getName(),
                List.of(EventTrigger.EventTriggerAttribute.SOURCE, EventTrigger.EventTriggerAttribute.NAME)));
    }
}
