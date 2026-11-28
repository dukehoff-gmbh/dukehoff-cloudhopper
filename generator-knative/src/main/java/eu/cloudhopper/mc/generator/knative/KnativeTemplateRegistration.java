package eu.cloudhopper.mc.generator.knative;

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
import eu.cloudhopper.mc.generator.api.annotations.SupportedValues;
import eu.cloudhopper.mc.generator.generic.annotations.Template;
import eu.cloudhopper.mc.generator.generic.annotations.TemplateRegistration;

/**
 * The {@code knative} generator: the {@code springboot-http} application plus a Helm chart
 * that runs it on Knative. The chart contains a Knative {@code Service}, a {@code PingSource}
 * per scheduled function and a {@code KafkaSource} per queue function, which deliver to the
 * application's {@code /schedules/<function>} and {@code /events/<function>} endpoints.
 * {@code @Queue} publishers send to the Kafka topics with {@code spring-boot-starter-kafka}.
 * Install it with {@code helm upgrade --install <release> target/deployment/knative/chart}.
 */
@GeneratorFeatures(
    generatorId = "knative",
    supportedFeatures = {
        @GeneratorFeature(
            supportedAnnotation = eu.cloudhopper.mc.annotations.Bucket.class,
            supportedAttributes = {
                eu.cloudhopper.mc.annotations.Bucket.BucketAttribute.NAME,
                eu.cloudhopper.mc.annotations.Bucket.BucketAttribute.ACCESS,
                eu.cloudhopper.mc.annotations.Bucket.BucketAttribute.EXISTING
            },
            // the S3 client of provider-aws-blobstore, for S3 and S3-compatible storage such as MinIO
            runtimeClass = "eu.cloudhopper.mc.provider.aws.blobstore.S3BlobStoreFactory",
            runtimeModule = "eu.cloudhopper.mc:provider-aws-blobstore"
        ),
        @GeneratorFeature(
            supportedAnnotation = eu.cloudhopper.mc.annotations.Table.class,
            supportedAttributes = {
                eu.cloudhopper.mc.annotations.Table.TableAttribute.NAME,
                eu.cloudhopper.mc.annotations.Table.TableAttribute.ACCESS,
                eu.cloudhopper.mc.annotations.Table.TableAttribute.EXISTING
            },
            // tables of a PostgreSQL database the chart passes to the application
            runtimeClass = "eu.cloudhopper.mc.provider.jdbc.keyvalue.JdbcKeyValueStoreFactory",
            runtimeModule = "eu.cloudhopper.mc:provider-jdbc-keyvalue"
        ),
        @GeneratorFeature(
            supportedAnnotation = eu.cloudhopper.mc.annotations.Queue.class,
            supportedAttributes = {
                eu.cloudhopper.mc.annotations.Queue.QueueAttribute.NAME,
                eu.cloudhopper.mc.annotations.Queue.QueueAttribute.EXISTING
            },
            // publishers send to the Kafka topic of the queue (cloudhopper.publisher=kafka)
            runtimeClass = "org.springframework.kafka.core.KafkaOperations",
            runtimeModule = "org.springframework.boot:spring-boot-starter-kafka"
        ),
        @GeneratorFeature(
            supportedAnnotation = Function.class,
            supportedAttributes = {
                FunctionAttribute.NAME,
                // one deployment unit per identity; the functions without one share the default unit
                FunctionAttribute.IDENTITY
            }
        ),
        @GeneratorFeature(
            supportedAnnotation = EventTrigger.class,
            supportedAttributes = {
                EventTriggerAttribute.SOURCE,
                EventTriggerAttribute.NAME
            },
            supportedValues = @SupportedValues(
                attribute = EventTriggerAttribute.SOURCE,
                values = {"QUEUE", "STORAGE"}
            )
        ),
        @GeneratorFeature(
            supportedAnnotation = HttpTrigger.class,
            supportedAttributes = {
                HttpTriggerAttribute.OPERATION_ID,
                HttpTriggerAttribute.PATH,
                HttpTriggerAttribute.METHOD,
                HttpTriggerAttribute.AUTHENTICATED,
                HttpTriggerAttribute.SCOPES,
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
    generatorId = "knative",
    basedOn = "springboot-http",
    templates = {
        @Template(
            phase = GenerationPhase.SHARED,
            templateName = "chart.ftl",
            description = "Generates the Helm chart's Chart.yaml",
            outputFileExtension = "yaml",
            outputSubDirectory = "chart",
            outputFileName = "Chart.yaml"
        ),
        @Template(
            phase = GenerationPhase.SHARED,
            templateName = "values.ftl",
            description = "Generates the Helm chart's values.yaml: image, Kafka, delivery, scaling",
            outputFileExtension = "yaml",
            outputSubDirectory = "chart",
            outputFileName = "values.yaml"
        ),
        @Template(
            phase = GenerationPhase.FINALIZE,
            templateName = "service.ftl",
            description = "Generates the Knative Service that runs the application",
            outputFileExtension = "yaml",
            outputSubDirectory = "chart/templates",
            outputFileName = "service.yaml"
        ),
        @Template(
            phase = GenerationPhase.SCHEDULE,
            templateName = "pingSource.ftl",
            description = "Generates a PingSource that calls POST /schedules/<function>",
            outputFileExtension = "yaml",
            outputSubDirectory = "chart/templates"
        ),
        @Template(
            phase = GenerationPhase.EVENT,
            templateName = "kafkaSource.ftl",
            description = "Generates a KafkaSource for a queue function that delivers to POST /events/<function>, "
                    + "and a KafkaSink for its dead-letter topic <topic>.DLT",
            outputFileExtension = "yaml",
            outputSubDirectory = "chart/templates"
        )
    }
)
public class KnativeTemplateRegistration {
}
