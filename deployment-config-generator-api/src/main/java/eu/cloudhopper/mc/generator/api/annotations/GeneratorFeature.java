package eu.cloudhopper.mc.generator.api.annotations;

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

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * Describes a feature supported by a code generator implementation.
 * <p>
 * This annotation is used within {@link GeneratorFeatures} to declare
 * which annotations and specific attributes a generator understands.
 * It allows the annotation processor to:
 * <ul>
 *   <li>Validate user annotations against generator capabilities</li>
 *   <li>Emit warnings or errors for unsupported attributes</li>
 *   <li>Enable conditional generation logic</li>
 * </ul>
 *
 * <p>
 * This annotation is intended for use by generator module authors, not application developers.
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
 *         )
 *     }
 * )
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({})
public @interface GeneratorFeature {

    /**
     * The annotation type supported by the generator.
     * This must be a class reference (e.g., {@code Function.class}).
     *
     * @return annotation class supported by the generator
     */
    Class<? extends java.lang.annotation.Annotation> supportedAnnotation() default Annotation.class;

    /**
     * The specific attributes of the annotation that the generator supports.
     * Attribute names should be declared using constants (e.g. {@code FunctionAttribute.MEMORY}).
     *
     * @return array of attribute names
     */
    String[] supportedAttributes() default {};
}