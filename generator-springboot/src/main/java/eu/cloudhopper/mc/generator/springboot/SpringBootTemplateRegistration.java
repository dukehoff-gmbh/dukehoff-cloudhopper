package eu.cloudhopper.mc.generator.springboot;

/*-
 * #%L
 * generator-springboot - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.annotations.HttpTrigger.HttpTriggerAttribute;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.Function.FunctionAttribute;
import eu.cloudhopper.mc.generator.api.GenerationPhase;
import eu.cloudhopper.mc.generator.api.annotations.GeneratorFeature;
import eu.cloudhopper.mc.generator.api.annotations.GeneratorFeatures;
import eu.cloudhopper.mc.generator.generic.annotations.Template;
import eu.cloudhopper.mc.generator.generic.annotations.TemplateRegistration;
import eu.cloudhopper.mc.annotations.HttpTrigger;

@GeneratorFeatures(
    generatorId = "springboot-http",
    supportedFeatures = {
        @GeneratorFeature(
            supportedAnnotation = Function.class,
            supportedAttributes = {
                FunctionAttribute.NAME
            }
        ),
        @GeneratorFeature(
            supportedAnnotation = HttpTrigger.class,
            supportedAttributes = {
                HttpTriggerAttribute.SUMMARY,
                HttpTriggerAttribute.DESCRIPTION,
                HttpTriggerAttribute.OPERATION_ID,
                HttpTriggerAttribute.PATH,
                HttpTriggerAttribute.METHOD,
            }
        )
    }
)
@TemplateRegistration(
    generatorId = "springboot-http",
    templates = {
        @Template(
            phase = GenerationPhase.API,
            templateName = "handler.ftl",
            description = "Generates Spring Boot REST controller for API functions",
            outputFileExtension = "java",
            outputSubDirectory = "generated",
            javaFile = true
        ),
        @Template(
            phase = GenerationPhase.SHARED,
            templateName = "dockerfile.ftl",
            description = "Generates a Dockerfile for the Spring Boot app",
            outputFileExtension = ".dockerfile",
            outputSubDirectory = "",
            javaFile = false
        )
    }
)
public class SpringBootTemplateRegistration {
}
