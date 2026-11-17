package eu.cloudhopper.mc.test.system.tests.generator.scaleway.terraform;

/*-
 * #%L
 * system-tests-generator-scaleway-terraform - a library from the "Cloudhopper" project.
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


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScalewayGeneratedTerraformTest {

    private static final Path DEPLOYMENT = Path.of("target", "deployment", "scaleway-terraform");

    private static String read(String file) throws IOException {
        Path path = DEPLOYMENT.resolve(file);
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return Files.readString(path);
    }

    @Test
    void oneContainerRunsTheApplication() throws IOException {
        String container = read("container.tf");
        assertTrue(container.contains("resource \"scaleway_container\" \"app\""), container);
        assertTrue(container.contains("image                        = var.image"), container);
        assertTrue(container.contains("default     = \"system-tests-generator-scaleway-te\""), container);
        assertTrue(container.contains("--cloudhopper.schedules.in-process=false"), container);
        assertFalse(container.contains("<#"), "unprocessed FreeMarker in container.tf");
    }

    @Test
    void theApplicationComesFromTheSpringBootTemplates() throws IOException {
        String dockerfile = read("shared-resources_dockerfile..dockerfile");
        assertTrue(dockerfile.matches("(?s).*target/system-tests-generator-scaleway-terraform-[^ /]+-scaleway\\.jar app\\.jar.*"), dockerfile);
        assertTrue(read("queuemessage_event_eventRoute.properties").contains("path=/events/queuemessage"));
    }

    @Test
    void aScheduledFunctionGetsACronTrigger() throws IOException {
        String cron = read("scheduled_schedule_cronTrigger.tf");
        assertTrue(cron.contains("resource \"scaleway_container_trigger\" \"scheduled_schedule\""), cron);
        assertTrue(cron.contains("schedule = \"* * * * *\""), cron);
        assertTrue(cron.contains("timezone = \"UTC\""), cron);
        assertTrue(cron.contains("http_path   = \"/schedules/scheduled\""), cron);
    }

    @Test
    void aQueueFunctionGetsAQueueADeadLetterQueueAndATrigger() throws IOException {
        String queue = read("queuemessage_event_queueTrigger.tf");
        assertTrue(queue.contains("name                       = \"${var.cloudhopper_name_prefix}cloudhopper-tck-players\""), queue);
        assertTrue(queue.contains("name         = \"${var.cloudhopper_name_prefix}cloudhopper-tck-players-dlq\""), queue);
        assertTrue(queue.contains("max_receive_count = var.queue_max_receive_count"), queue);
        assertTrue(queue.contains("http_path   = \"/events/queuemessage\""), queue);
        assertTrue(read("sqs.tf").contains("resource \"scaleway_mnq_sqs\" \"cloudhopper\""));
    }

    @Test
    void aStorageFunctionGetsNoTrigger() throws IOException {
        String storage = read("storageupload_event_queueTrigger.tf");
        assertFalse(storage.contains("resource "), storage);
        assertTrue(storage.contains("POST /events/storageupload"), storage);
    }

    @Test
    void theDocumentationIsCopied() throws IOException {
        assertTrue(read("README.md").contains("Scaleway Serverless Containers"));
        assertTrue(read("main.tf.disabled").contains("provider \"scaleway\""));
    }
}
