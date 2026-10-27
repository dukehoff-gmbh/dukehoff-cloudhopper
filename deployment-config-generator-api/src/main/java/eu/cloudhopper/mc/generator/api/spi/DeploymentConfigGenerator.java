package eu.cloudhopper.mc.generator.api.spi;

/*-
 * #%L
 * deployment-config-spi - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.generator.api.ConfigGenerationException;
import eu.cloudhopper.mc.generator.api.HandlerInfo;
import javax.annotation.processing.ProcessingEnvironment;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.ScheduledTrigger;
/**
 * Service Provider Interface (SPI) for custom deployment configuration generators.
 * <p>
 * Implementations of this interface participate in the Cloudhopper annotation processing
 * pipeline by generating provider-specific configuration files (e.g. Terraform, SAM, etc.)
 * for annotated function classes.
 *
 * <p>
 * This interface allows full control over the transformation of annotated Java classes
 * into deployment artifacts, such as:
 * <ul>
 *   <li>Function configuration (memory, timeout, runtime)</li>
 *   <li>API gateway integration metadata</li>
 *   <li>Scheduled trigger definitions</li>
 *   <li>Shared resources</li>
 * </ul>
 *
 * <p>
 * Implementations should be declared in {@code META-INF/services} using standard Java SPI.
 * Supported features should be declared using {@link eu.cloudhopper.mc.generator.api.annotations.GeneratorFeatures}.
 */
public interface DeploymentConfigGenerator {

    /**
     * Checks whether this generator supports the given provider or target ID.
     *
     * @param provider the name of the target generator (e.g. "aws-terraform")
     * @return true if this generator can handle the specified provider
     */
    boolean supportsGenerator(String provider);

    /**
     * Generates the deployment configuration for a single serverless function.
     * <p>
     * Called once per function discovered with a {@link eu.cloudhopper.mc.annotations.Function} annotation.
     *
     * @param generatorId the generator ID (as declared in {@link eu.cloudhopper.mc.generator.api.annotations.GeneratorFeatures})
     * @param outputDir the directory where generated files should be written
     * @param handlerInfo metadata about the discovered function class
     * @param env the annotation processing environment
     * @throws ConfigGenerationException if generation fails
     */
    void generateServerlessFunctionConfiguration(String generatorId, String outputDir, HandlerInfo handlerInfo, ProcessingEnvironment env)
        throws ConfigGenerationException;

    /**
     * Generates API resource definitions and integration logic (e.g., API Gateway routes).
     * <p>
     * Called if the function is annotated with {@link eu.cloudhopper.mc.annotations.HttpTrigger}.
     *
     * @param generatorId the generator ID
     * @param outputDir the output directory for generated files
     * @param handlerInfo metadata about the function
     * @param httpTrigger API metadata annotation
     * @param env the annotation processing environment
     * @throws ConfigGenerationException if generation fails
     */
    void generateApiResourceAndIntegration(String generatorId, String outputDir, HandlerInfo handlerInfo, HttpTrigger httpTrigger, ProcessingEnvironment env)
        throws ConfigGenerationException;

    /**
     * Generates configuration for scheduled triggers.
     * <p>
     * Called if the function is annotated with {@link eu.cloudhopper.mc.annotations.ScheduledTrigger}.
     *
     * @param generatorID the generator ID
     * @param configOutputDir output directory for the config
     * @param handlerInfo function metadata
     * @param schedule the schedule annotation
     * @param processingEnv annotation processing context
     */
    void generateScheduledTrigger(String generatorID, String configOutputDir, HandlerInfo handlerInfo, ScheduledTrigger schedule, ProcessingEnvironment processingEnv);

    /**
     * Performs any final configuration generation steps after all handlers are processed.
     * <p>
     * This may include writing shared files (e.g. backend definitions, resource groups, etc.)
     *
     * @param providerName the name of the generator (e.g. "aws-terraform")
     * @param configOutputDir the output directory for shared files
     * @throws ConfigGenerationException if generation fails
     */
    void finalizeConfig(String providerName, String configOutputDir) throws ConfigGenerationException;

    /**
     * Returns the unique ID of this generator.
     * <p>
     * Must match the {@code generatorId} declared via {@link eu.cloudhopper.mc.generator.api.annotations.GeneratorFeatures}.
     *
     * @return the generator ID
     */
    String getGeneratorID();
}
