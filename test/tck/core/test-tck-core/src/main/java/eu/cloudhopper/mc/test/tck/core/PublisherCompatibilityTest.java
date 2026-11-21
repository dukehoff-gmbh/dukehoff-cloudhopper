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

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.annotations.EventTrigger;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.Queue;
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Assert;

/**
 * {@code @Queue}: the function "publishplayer" sends a player to the queue that the function
 * "queuemessage" consumes, which logs it.
 */
public class PublisherCompatibilityTest implements FeatureAwareTest {

    private static final String PUBLISHER = "publishplayer";
    private static final String CONSUMER = "queuemessage";
    private static final int MAX_ATTEMPTS = 10;

    @Override
    public void run(TestContext context) throws Exception {
        String name = "tck-published-" + UUID.randomUUID();
        Object result = context.invokeFunctionDirect(PUBLISHER, Map.of("id", 2, "name", name, "ranking", 0));
        System.out.println("📤 " + PUBLISHER + " answered: " + result);
        Assert.assertEquals("sent", result);

        // delivery and log ingestion take a while on every platform
        boolean received = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !received; attempt++) {
            Thread.sleep(30_000);
            List<String> logs = context.fetchLogs(CONSUMER);
            received = logs.stream().anyMatch(line -> line.contains("Queue message received: " + name));
            System.out.println("📜 Attempt " + attempt + ": " + (received ? "received" : "not yet received"));
        }
        Assert.assertTrue("The consumer did not log the published message " + name, received);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(
                new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(Queue.class.getName(), List.of(Queue.QueueAttribute.NAME)),
                new RequiredFeature(EventTrigger.class.getName(),
                        List.of(EventTrigger.EventTriggerAttribute.SOURCE, EventTrigger.EventTriggerAttribute.NAME),
                        Map.of(EventTrigger.EventTriggerAttribute.SOURCE, List.of(EventSource.QUEUE.name()))));
    }
}
