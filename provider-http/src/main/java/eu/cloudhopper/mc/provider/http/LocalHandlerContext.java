package eu.cloudhopper.mc.provider.http;

/*-
 * #%L
 * provider-http - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.HandlerContext;
import java.util.UUID;

public class LocalHandlerContext implements HandlerContext {

    private final String requestId;

    public LocalHandlerContext() {
        this(UUID.randomUUID().toString());
    }

    public LocalHandlerContext(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String getFunctionName() {
        return "local-function";
    }

    @Override
    public String getFunctionVersion() {
        return "1.0-local";
    }

    @Override
    public String getInvokedFunctionArn() {
        return null;
    }

    @Override
    public String getLogGroupName() {
        return null;
    }

    @Override
    public String getLogStreamName() {
        return null;
    }

    @Override
    public long getRemainingTimeInMillis() {
        return 0;
    }

    @Override
    public int getMemoryLimitInMB() {
        return 256;
    }
}
