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
 * Marks a method of a function class that warms the function's own dependencies before a
 * snapshot: with AWS Lambda SnapStart, Cloudhopper calls it before Lambda takes the snapshot, so
 * that restored instances start with, e.g., SDK clients created, a price list loaded or the
 * classes of a first database call initialized. Without SnapStart it is not called.
 *
 * <pre>{@code
 * public class Prices {
 *     private final PriceList prices = new PriceList();
 *
 *     @OnPrime
 *     void prime() {
 *         prices.load();
 *     }
 *
 *     @Function(name = "price") ...
 * }
 * }</pre>
 *
 * <p>The method has no parameters; it may throw exceptions, which are logged and do not stop the
 * snapshot. All instances restored from one snapshot share what it creates: no random values,
 * unique IDs or credentials that must differ per instance, and connections that may have been
 * closed by the time of the restore must be reopened in the function call.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface OnPrime {
}
