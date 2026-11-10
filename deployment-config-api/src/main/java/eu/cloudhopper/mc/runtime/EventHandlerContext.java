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

import java.util.Objects;

/**
 * A {@link HandlerContext} for an event invocation: it adds the {@link EventMetadata} to the
 * platform's context. The providers create it; function code does not need it.
 */
public final class EventHandlerContext implements HandlerContext {

    private final HandlerContext delegate;
    private final EventMetadata eventMetadata;

    /**
     * @param delegate      the platform's context
     * @param eventMetadata the metadata of the event being handled
     */
    public EventHandlerContext(HandlerContext delegate, EventMetadata eventMetadata) {
        this.delegate = Objects.requireNonNull(delegate);
        this.eventMetadata = eventMetadata;
    }

    @Override
    public EventMetadata getEventMetadata() {
        return eventMetadata;
    }

    @Override
    public String getRequestId() {
        return delegate.getRequestId();
    }

    @Override
    public String getFunctionName() {
        return delegate.getFunctionName();
    }

    @Override
    public String getFunctionVersion() {
        return delegate.getFunctionVersion();
    }

    @Override
    public String getInvokedFunctionArn() {
        return delegate.getInvokedFunctionArn();
    }

    @Override
    public String getLogGroupName() {
        return delegate.getLogGroupName();
    }

    @Override
    public String getLogStreamName() {
        return delegate.getLogStreamName();
    }

    @Override
    public long getRemainingTimeInMillis() {
        return delegate.getRemainingTimeInMillis();
    }

    @Override
    public int getMemoryLimitInMB() {
        return delegate.getMemoryLimitInMB();
    }
}
