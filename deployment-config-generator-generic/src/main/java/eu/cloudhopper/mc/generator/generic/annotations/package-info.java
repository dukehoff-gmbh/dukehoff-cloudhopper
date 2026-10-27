/**
 * Contains annotations for registering templates with the {@link eu.cloudhopper.mc.generator.generic.GenericDeploymentConfigGenerator}.
 * <p>
 * These annotations are used by integrators to declare:
 * <ul>
 *   <li>The generator ID they are registering</li>
 *   <li>Which templates to use during each {@link eu.cloudhopper.mc.generator.api.GenerationPhase}</li>
 *   <li>Where the rendered files should be placed</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * @TemplateRegistration(
 *     generatorId = "aws-terraform",
 *     templates = {
 *         @Template(
 *             phase = GenerationPhase.FUNCTION,
 *             templateName = "function",
 *             outputFileExtension = "tf",
 *             outputSubDirectory = "deployment/aws"
 *         )
 *     }
 * )
 * public class AwsTerraformJava21TemplateRegistration { ... }
 * }</pre>
 */
package eu.cloudhopper.mc.generator.generic.annotations;
/*-
 * #%L
 * deployment-config-generator-generic - a library from the "Cloudhopper" project.
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
