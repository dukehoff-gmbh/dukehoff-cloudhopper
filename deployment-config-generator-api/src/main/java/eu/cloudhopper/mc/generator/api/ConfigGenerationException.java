package eu.cloudhopper.mc.generator.api;

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
/**
 *
 * @author antonepple
 */
/**
 * Exception thrown when an error occurs during deployment configuration generation.
 * <p>
 * This exception is typically thrown by implementations of
 * {@link eu.cloudhopper.mc.generator.api.spi.DeploymentConfigGenerator}
 * to indicate a failure during code or config generation.
 *
 * <p>
 * It may wrap a lower-level exception or contain a descriptive message
 * indicating the cause of the failure (e.g. invalid template data, missing attributes).
 */
public class ConfigGenerationException extends Exception {

    /**
     * Creates a new exception with a message and an underlying cause.
     *
     * @param message a description of the error
     * @param cause the original exception that caused this failure
     */
    public ConfigGenerationException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with a message.
     *
     * @param message a description of the error
     */
    ConfigGenerationException(String message) {
        super(message);
    }
}
