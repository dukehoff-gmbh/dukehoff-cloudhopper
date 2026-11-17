package eu.cloudhopper.mc.generator.scaleway.terraform;

/*-
 * #%L
 * generator-scaleway-terraform - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.annotations.EventTrigger.EventTriggerAttribute;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.Function.FunctionAttribute;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.HttpTrigger.HttpTriggerAttribute;
import eu.cloudhopper.mc.annotations.ScheduledTrigger;
import eu.cloudhopper.mc.annotations.ScheduledTrigger.ScheduleAttribute;
import eu.cloudhopper.mc.generator.api.GenerationPhase;
import eu.cloudhopper.mc.generator.api.annotations.GeneratorFeature;
import eu.cloudhopper.mc.generator.api.annotations.GeneratorFeatures;
import eu.cloudhopper.mc.generator.generic.annotations.Template;
import eu.cloudhopper.mc.generator.generic.annotations.TemplateRegistration;

/**
 * The {@code scaleway-terraform} generator: the {@code springboot-http} application plus
 * Terraform that runs it as one Scaleway Serverless Container. A cron trigger per scheduled
 * function calls {@code POST /schedules/<function>}; a Scaleway Queue (SQS), a dead-letter queue
 * and a queue trigger per queue function deliver to {@code POST /events/<function>}.
 * Build and push the image, then {@code terraform apply -var image=<image>} in
 * {@code target/deployment/scaleway-terraform}.
 */
@GeneratorFeatures(
    generatorId = "scaleway-terraform",
    supportedFeatures = {
        @GeneratorFeature(
            supportedAnnotation = Function.class,
            supportedAttributes = {
                FunctionAttribute.NAME
            }
        ),
        @GeneratorFeature(
            supportedAnnotation = EventTrigger.class,
            supportedAttributes = {
                EventTriggerAttribute.SOURCE,
                EventTriggerAttribute.NAME
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
        ),
        @GeneratorFeature(
            supportedAnnotation = ScheduledTrigger.class,
            supportedAttributes = {
                ScheduleAttribute.CRON,
                ScheduleAttribute.TIMEZONE
            }
        )
    }
)
@TemplateRegistration(
    generatorId = "scaleway-terraform",
    basedOn = "springboot-http",
    templates = {
        @Template(
            phase = GenerationPhase.SHARED,
            templateName = "container.ftl",
            description = "Generates the container namespace and the Serverless Container that runs the application",
            outputFileExtension = "tf",
            outputSubDirectory = "",
            outputFileName = "container.tf"
        ),
        @Template(
            phase = GenerationPhase.SCHEDULE,
            templateName = "cronTrigger.ftl",
            description = "Generates a cron trigger that calls POST /schedules/<function>",
            outputFileExtension = "tf",
            outputSubDirectory = ""
        ),
        @Template(
            phase = GenerationPhase.EVENT,
            templateName = "sqs.ftl",
            description = "Activates Scaleway Queues (SQS) in the project and creates the credentials of the queue triggers; "
                    + "every event-triggered function writes the same file",
            outputFileExtension = "tf",
            outputSubDirectory = "",
            outputFileName = "sqs.tf"
        ),
        @Template(
            phase = GenerationPhase.EVENT,
            templateName = "queueTrigger.ftl",
            description = "Generates a queue, its dead-letter queue and a queue trigger that delivers to POST /events/<function>",
            outputFileExtension = "tf",
            outputSubDirectory = ""
        )
    }
)
public class ScalewayTerraformTemplateRegistration {
}
