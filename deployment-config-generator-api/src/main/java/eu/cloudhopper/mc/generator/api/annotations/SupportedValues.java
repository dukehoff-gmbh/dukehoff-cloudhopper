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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a supported attribute to some of its values, e.g. the event sources a generator
 * can wire up:
 *
 * <pre>{@code
 * @SupportedValues(attribute = EventTriggerAttribute.SOURCE, values = {"QUEUE", "STORAGE"})
 * }</pre>
 *
 * An attribute without {@code @SupportedValues} supports every value. For an enum attribute
 * the values are the names of the enum constants.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({})
public @interface SupportedValues {

    /**
     * The attribute, which must also be listed in {@link GeneratorFeature#supportedAttributes()}.
     *
     * @return the attribute name
     */
    String attribute();

    /**
     * The supported values of the attribute.
     *
     * @return the values, for enums the constant names
     */
    String[] values();
}
