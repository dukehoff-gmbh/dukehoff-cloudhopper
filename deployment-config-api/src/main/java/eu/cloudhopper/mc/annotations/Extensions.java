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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeating {@link Extension} annotations.
 *
 * <p>This annotation is used internally by the Java compiler to support
 * multiple {@link Extension} declarations on a single element. It is not
 * intended for direct use in application code.</p>
 *
 * <p>Instead of using this annotation directly, developers should declare
 * multiple {@code @Extension} annotations using Java's {@link java.lang.annotation.Repeatable}
 * syntax. The compiler automatically wraps them in this container.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Function(
 *     name = "myFunc",
 *     extensions = {
 *         @Extension(
 *             name = ExtensionKeys.Aws.Architecture.KEY,
 *             value = ExtensionKeys.Aws.Architecture.Values.ARM64)
 *     }
 * )
 * }</pre>
 *
 * @see Extension
 * @see ExtensionKeys
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Extensions {

    /**
     * The array of {@link Extension} annotations grouped together.
     *
     * @return the extension annotations
     */
    Extension[] value();
}
