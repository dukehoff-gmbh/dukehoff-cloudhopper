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

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Declares a vendor-specific or custom extension attribute for a {@link Function}.
 *
 * <p>This annotation can be used to specify additional deployment metadata that
 * is not covered by the standard {@link Function} attributes. Predefined keys
 * are available as constants in {@link ExtensionKeys}, for example
 * {@link ExtensionKeys.Aws.Architecture#KEY} ({@code "x-aws-architecture"}).</p>
 *
 * <p>This annotation is {@linkplain java.lang.annotation.Repeatable repeatable}
 * via the {@link Extensions} container annotation and is only retained in source
 * code for processing during code generation.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Function(
 *     name = "hello",
 *     extensions = {
 *         @Extension(
 *             name = ExtensionKeys.Aws.Architecture.KEY,
 *             value = ExtensionKeys.Aws.Architecture.Values.ARM64)
 *     }
 * )
 * }</pre>
 *
 * @see Extensions
 * @see ExtensionKeys
 * @see Function#extensions()
 */
@Repeatable(Extensions.class)
@Retention(RetentionPolicy.SOURCE)
public @interface Extension {
    /**
     * The name of the extension attribute.
     * <p>Vendor-specific keys follow the OpenAPI {@code x-} prefix convention,
     * e.g. {@code "x-aws-architecture"}. Prefer the constants in
     * {@link ExtensionKeys} over string literals where one exists.</p>
     *
     * @return the attribute name
     */
    String name();

    /**
     * The value associated with the extension attribute.
     * <p>Interpretation depends on the attribute name, e.g. {@code "arm64"} for
     * {@link ExtensionKeys.Aws.Architecture#KEY}.</p>
     *
     * @return the attribute value
     */
    String value();
}
