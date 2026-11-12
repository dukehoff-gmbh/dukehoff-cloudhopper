package eu.cloudhopper.mc.generator.generic;

/*-
 * #%L
 * deployment-config-generator - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.generator.generic.internal.TemplateDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericDeploymentConfigGeneratorTest {

    private static TemplateDescriptor template(String name) {
        return new TemplateDescriptor(name, "", "txt", "", false);
    }

    private static List<String> names(List<TemplateDescriptor> templates) {
        return templates.stream().map(TemplateDescriptor::getTemplateName).toList();
    }

    @Test
    void aGeneratorBasedOnAnotherRunsBothTemplateSetsBaseFirst() {
        var merged = GenericDeploymentConfigGenerator.mergePhases(
                Map.of("shared", List.of(template("dockerfile.ftl")), "schedule", List.of(template("schedule.ftl"))),
                Map.of("shared", List.of(template("chart.ftl")), "event", List.of(template("kafkaSource.ftl"))));

        assertEquals(List.of("dockerfile.ftl", "chart.ftl"), names(merged.get("shared")));
        assertEquals(List.of("schedule.ftl"), names(merged.get("schedule")));
        assertEquals(List.of("kafkaSource.ftl"), names(merged.get("event")));
    }

    @Test
    void anOwnTemplateReplacesTheBaseTemplateWithTheSameName() {
        var merged = GenericDeploymentConfigGenerator.mergePhases(
                Map.of("schedule", List.of(template("schedule.ftl"), template("scheduleJob.ftl"))),
                Map.of("schedule", List.of(template("scheduleJob.ftl"))));

        assertEquals(List.of("schedule.ftl", "scheduleJob.ftl"), names(merged.get("schedule")));
    }

    @Test
    void aGeneratorWithoutOwnTemplatesRunsTheBaseTemplates() {
        var merged = GenericDeploymentConfigGenerator.mergePhases(
                Map.of("shared", List.of(template("dockerfile.ftl"))), null);

        assertEquals(List.of("dockerfile.ftl"), names(merged.get("shared")));
    }
}
