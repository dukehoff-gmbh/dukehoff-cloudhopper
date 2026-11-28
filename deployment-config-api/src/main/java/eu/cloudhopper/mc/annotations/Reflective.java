package eu.cloudhopper.mc.annotations;

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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type that the application reads or writes as JSON itself, e.g. a record it stores in
 * a table or returns inside a {@code Map}: for a native image, Cloudhopper registers it for
 * reflection, with the types it contains (fields, record components, getters, type arguments)
 * and their array types. The functions' body, result and queue message types are registered
 * without it.
 *
 * <pre>{@code
 * @Reflective
 * public record Measurement(String detected, int beforeDays, List<Day> days) { }
 * }</pre>
 *
 * <p>Like Spring's {@code @RegisterReflectionForBinding} or Quarkus' {@code @RegisterForReflection};
 * needs no GraalVM dependency.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Reflective {

    /**
     * @return further types to register in the same way, e.g. types of a library the
     *         application serializes itself; by default only the annotated type
     */
    Class<?>[] types() default {};
}
