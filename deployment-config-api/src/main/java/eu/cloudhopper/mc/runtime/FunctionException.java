package eu.cloudhopper.mc.runtime;

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
 * Carries a checked exception thrown by a function method.
 *
 * <p>A function method may declare checked exceptions ({@code throws IOException}). The generated
 * handler implements {@link CloudRequestHandler}, which declares none, so it wraps them in this
 * exception; runtime exceptions and errors pass through unchanged. Every target treats it like any
 * other exception: an HTTP call fails with status 500, an event is redelivered.</p>
 */
public class FunctionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param cause the checked exception the function method threw
     */
    public FunctionException(Exception cause) {
        super(cause.getMessage(), cause);
    }
}
