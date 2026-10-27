package eu.cloudhopper.mc.runtime;

import java.util.Map;

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
 * Provides runtime information about the current function invocation.
 * <p>
 * This context is passed to every {@link CloudRequestHandler} and includes
 * platform-specific metadata such as request ID, function name, and resource limits.
 * Implementations of this interface are provided by the platform adapter
 * (e.g., AWS Lambda or Azure Functions).
 */
public interface HandlerContext {

    /**
     * @return the unique ID of the current request
     */
    String getRequestId();

    /**
     * @return the name of the deployed function
     */
    String getFunctionName();

    /**
     * @return the version of the function
     */
    String getFunctionVersion();

    /**
     * @return the fully qualified ARN (or platform-specific equivalent) of the invoked function
     */
    String getInvokedFunctionArn();

    /**
     * @return the name of the log group for this function
     */
    String getLogGroupName();

    /**
     * @return the name of the log stream associated with this invocation
     */
    String getLogStreamName();

    /**
     * @return the remaining time available to the function in milliseconds
     */
    long getRemainingTimeInMillis();

    /**
     * @return the memory limit for the function in megabytes
     */
    int getMemoryLimitInMB();
}