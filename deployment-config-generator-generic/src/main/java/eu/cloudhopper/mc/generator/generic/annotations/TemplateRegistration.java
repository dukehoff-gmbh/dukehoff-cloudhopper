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
package eu.cloudhopper.mc.generator.generic.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a set of templates to be used by
 * {@link eu.cloudhopper.mc.generator.generic.GenericDeploymentConfigGenerator}.
 * <p>
 * Each generator module must declare this annotation to register its
 * templates, the generator ID it is associated with, and metadata for each
 * generation phase.
 */
@Retention(value = RetentionPolicy.SOURCE)
@Target(value = ElementType.TYPE)
public @interface TemplateRegistration {

    /**
     * The unique ID of the generator. This must match the generatorId
     * passed to the annotation processor.
     *
     * @return the generator ID
     */
    String generatorId();

    /**
     * The list of templates used by this generator, grouped by generation
     * phase.
     *
     * @return the template configuration
     */
    Template[] templates();

    /**
     * The ID of a generator this generator builds on, or empty. A generator
     * based on another one runs the other generator's templates as well as its
     * own; one of its own templates with the same name replaces the other
     * generator's template. Templates are looked up in this generator's
     * directory first, then in the other generator's directory. Example: the
     * {@code knative} generator builds on {@code springboot-http}, which
     * generates the application, and adds the Knative manifests.
     *
     * @return the ID of the generator this one builds on, or an empty string
     */
    String basedOn() default "";
    
}
