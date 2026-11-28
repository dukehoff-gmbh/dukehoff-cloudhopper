package eu.cloudhopper.mc.provider.springboot;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Which functions an instance of the application serves. Targets that deploy one unit per
 * identity (Knative, Scaleway: one service or container per {@code @Function(identity)}, the
 * functions without one in the default unit) run the same application several times and set
 * {@code CLOUDHOPPER_IDENTITY} for each: an instance then answers only the endpoints of the
 * functions of its identity, so that a function cannot be reached through an instance with
 * other permissions. Without the variable (e.g. {@code springboot-http}) it serves all functions.
 *
 * <p>The check runs on every request, not as a Spring condition: in a native image, Spring AOT
 * fixes the beans at build time, and one image serves all identities.</p>
 */
public final class Identities {

    /** The environment variable with the identity of the instance; empty for the default unit. */
    public static final String VARIABLE = "CLOUDHOPPER_IDENTITY";

    private static final String ACTIVE = System.getenv(VARIABLE);

    private Identities() {
    }

    /**
     * @param identity the unit of the function: its {@code @Function(identity)}, or empty for
     *                 a function without one
     * @return whether this instance serves the function
     */
    public static boolean serves(String identity) {
        return serves(ACTIVE, identity);
    }

    static boolean serves(String active, String identity) {
        return active == null || active.equals(identity == null ? "" : identity);
    }

    /**
     * @return the answer for a function this instance does not serve: 404, as if it did not exist
     */
    public static <T> ResponseEntity<T> notServed() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
