package eu.cloudhopper.mc.test.system.tests.generator.knative;

/*-
 * #%L
 * system-tests-generator-aws-terraform - a library from the "Cloudhopper" project.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Checks the Helm chart the knative generator wrote for the shared test functions (#43). The TCK
 * installs it on a cluster; this test runs in every build and needs none.
 */
class KnativeGeneratedChartTest {

    private static final Path DEPLOYMENT = Path.of("target", "deployment", "knative");
    private static final Path CHART = DEPLOYMENT.resolve("chart");

    private static String read(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing " + file);
        return Files.readString(file);
    }

    @Test
    void theChartHasItsFixedFileNames() throws IOException {
        String chart = read(CHART.resolve("Chart.yaml"));
        assertTrue(chart.contains("apiVersion: v2"), chart);
        assertTrue(chart.contains("name: system-tests-generator-knative"), chart);
        assertTrue(read(CHART.resolve("values.yaml")).contains("bootstrapServers: []"));
        String service = read(CHART.resolve("templates").resolve("service.yaml"));
        assertTrue(service.contains("kind: Service"), service);
        assertTrue(service.contains("--cloudhopper.schedules.in-process=false"), service);
    }

    @Test
    void theApplicationComesFromTheSpringBootTemplates() throws IOException {
        String dockerfile = read(DEPLOYMENT.resolve("shared-resources_dockerfile..dockerfile"));
        assertTrue(dockerfile.contains("system-tests-generator-knative-1.0-SNAPSHOT-knative.jar"), dockerfile);
        assertTrue(read(DEPLOYMENT.resolve("queuemessage_event_eventRoute.properties")).contains("path=/events/queuemessage"));
    }

    @Test
    void aScheduledFunctionGetsAPingSource() throws IOException {
        String ping = read(CHART.resolve("templates").resolve("scheduled_schedule_pingSource.yaml"));
        assertTrue(ping.contains("kind: PingSource"), ping);
        assertTrue(ping.contains("schedule: \"* * * * *\""), ping);
        assertTrue(ping.contains("timezone: \"UTC\""), ping);
        assertTrue(ping.contains("uri: /schedules/scheduled"), ping);
    }

    @Test
    void aQueueFunctionGetsAKafkaSourceAndADeadLetterSink() throws IOException {
        String source = read(CHART.resolve("templates").resolve("queuemessage_event_kafkaSource.yaml"));
        assertTrue(source.contains("kind: KafkaSource"), source);
        assertTrue(source.contains("- \"cloudhopper-tck-players\""), source);
        assertTrue(source.contains("uri: /events/queuemessage"), source);
        assertTrue(source.contains("kind: KafkaSink"), source);
        assertTrue(source.contains("topic: \"cloudhopper-tck-players.DLT\""), source);
    }

    @Test
    void aStorageFunctionGetsNoSource() throws IOException {
        assertEquals("", read(CHART.resolve("templates").resolve("storageupload_event_kafkaSource.yaml")).strip());
    }
}
