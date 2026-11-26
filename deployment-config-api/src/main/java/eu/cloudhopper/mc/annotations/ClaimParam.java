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
 * Binds a method parameter to a claim of the caller's token on a route with
 * {@code @HttpTrigger(authenticated = true)}. Parameters of type {@code String}, {@code Long},
 * {@code Integer}, {@code Double}, {@code Boolean} and {@code List<String>} are supported; a
 * claim the token does not have is {@code null}.
 *
 * <p>Usage:
 * <pre>{@code
 * @HttpTrigger(method = "GET", path = "/items", authenticated = true)
 * public List<Item> list(@ClaimParam("sub") String userId) { ... }
 * }</pre>
 *
 * @see HttpTrigger#authenticated()
 * @see eu.cloudhopper.mc.runtime.Claims
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
public @interface ClaimParam {

    /**
     * @return the name of the claim, e.g. {@code "sub"}, {@code "email"} or
     *         {@code "custom:tenant"}
     */
    String value();
}
