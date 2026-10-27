package eu.cloudhopper.mc.deployment.config.processor.test.support;
    
/*-
 * #%L
 * deployment-config-processor - a library from the "Cloudhopper" project.
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
// src/test/java/eu/cloudhopper/mc/deployment/config/generator/TestNoOpGenerator.java


import com.google.auto.service.AutoService;
import eu.cloudhopper.mc.generator.api.spi.DeploymentConfigGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.ScheduledTrigger;
import eu.cloudhopper.mc.generator.api.HandlerInfo;

@AutoService(DeploymentConfigGenerator.class)
public class TestNoOpGenerator implements DeploymentConfigGenerator {
    @Override
    public String getGeneratorID() {
        return "";         // match the default "" generatorId
    }
    @Override
    public boolean supportsGenerator(String id) {
        return true;       // always claim support
    }

    @Override
    public void generateServerlessFunctionConfiguration(
        String generatorId,
        String outputDir,
        HandlerInfo info,
        ProcessingEnvironment env
    ) {
        // no-op
    }
    @Override
    public void generateApiResourceAndIntegration(
        String generatorId,
        String outputDir,
        HandlerInfo info,
        HttpTrigger trigger,
        ProcessingEnvironment env
    ) {
        // no-op
    }
    @Override
    public void generateScheduledTrigger(
        String generatorId,
        String outputDir,
        HandlerInfo info,
        ScheduledTrigger trigger,
        ProcessingEnvironment env
    ) {
        // no-op
    }
    @Override
    public void finalizeConfig(String generatorId, String outputDir) {
        // no-op
    }
}
