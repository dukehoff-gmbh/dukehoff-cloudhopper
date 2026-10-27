package eu.cloudhopper.mc.generator.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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

/**
 * Declares the features supported by a code generator module.
 * <p>
 * This annotation is placed on the class that implements a generator (e.g., {@code TemplateRegistration})
 * and is used by the Cloudhopper annotation processor to understand:
 * <ul>
 *   <li>Which annotations the generator supports</li>
 *   <li>Which attributes of those annotations are recognized</li>
 * </ul>
 *
 * <p>
 * This enables validation during annotation processing and allows tools to provide early feedback
 * when unsupported annotations or attributes are used.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @GeneratorFeatures(
 *     generatorId = "aws-terraform",
 *     supportedFeatures = {
 *         @GeneratorFeature(
 *             supportedAnnotation = Function.class,
 *             supportedAttributes = {
 *                 FunctionAttribute.NAME,
 *                 FunctionAttribute.TIMEOUT,
 *                 FunctionAttribute.MEMORY
 *             }
 *         ),
 *         @GeneratorFeature(
 *             supportedAnnotation = Schedule.class,
 *             supportedAttributes = {
 *                 ScheduleAttribute.CRON,
 *                 ScheduleAttribute.TIMEZONE
 *             }
 *         )
 *     }
 * )
 * public class AwsTemplateRegistration implements TemplateRegistration {
 *     ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GeneratorFeatures {

    /**
     * The unique ID of the generator implementation.
     * <p>
     * This is used to associate the feature declarations with the correct generator.
     *
     * @return the generator identifier
     */
    String generatorId();

    /**
     * A list of features (annotations and their attributes) that this generator supports.
     *
     * @return array of supported annotation features
     */
    GeneratorFeature[] supportedFeatures() default {};
}
