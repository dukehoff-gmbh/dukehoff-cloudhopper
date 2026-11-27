/*-
 * #%L
 * provider-aws - a library from the "Cloudhopper" project.
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
package eu.cloudhopper.mc.provider.aws;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

/**
 * Primes the generated handlers before Lambda takes the SnapStart snapshot: an {@code org.crac}
 * resource whose {@link #beforeCheckpoint} runs each handler's request path once without the
 * function ({@link ApiGatewayEventRouter#prime()}), so that new instances restore with the event
 * conversion, Jackson and the responses loaded and initialized. The functions' own code, and its
 * effects on tables, queues and buckets, is not run.
 *
 * <p>Active only when the function runs with SnapStart ({@code AWS_LAMBDA_INITIALIZATION_TYPE}
 * is {@code snap-start}). A failure of the priming is logged and does not stop the snapshot.
 */
final class SnapStartPriming implements Resource {

    /** Whether the execution environment initializes for a SnapStart snapshot. */
    static final boolean ACTIVE = "snap-start".equals(System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE"));

    // org.crac keeps only weak references to its resources
    private static final SnapStartPriming INSTANCE = new SnapStartPriming();
    private static volatile boolean registered;

    private final List<ApiGatewayEventRouter<?, ?>> routers = new CopyOnWriteArrayList<>();

    private SnapStartPriming() {
    }

    /**
     * @param router a handler to prime before the snapshot
     */
    static void register(ApiGatewayEventRouter<?, ?> router) {
        INSTANCE.routers.add(router);
        if (!registered) {
            synchronized (SnapStartPriming.class) {
                if (!registered) {
                    Core.getGlobalContext().register(INSTANCE);
                    registered = true;
                }
            }
        }
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        prime(routers);
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        // nothing to renew: the priming keeps no state
    }

    /**
     * @param routers the handlers to prime
     * @return the number of handlers primed without an error
     */
    static int prime(List<ApiGatewayEventRouter<?, ?>> routers) {
        int primed = 0;
        for (ApiGatewayEventRouter<?, ?> router : routers) {
            try {
                router.prime();
                primed++;
            } catch (RuntimeException | LinkageError e) {
                System.err.println("Cloudhopper: priming the handler for SnapStart failed: " + e);
            }
        }
        return primed;
    }
}
