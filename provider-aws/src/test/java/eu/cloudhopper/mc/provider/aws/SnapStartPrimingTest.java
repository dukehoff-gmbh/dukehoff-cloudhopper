package eu.cloudhopper.mc.provider.aws;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SnapStartPrimingTest {

    public record Order(String customer, List<String> items) {
    }

    private static final AtomicInteger CALLS = new AtomicInteger();

    /** Counts every call of the function: the priming must not run it. */
    private static <I> CloudRequestHandler<I, String> function() {
        return new CloudRequestHandler<>() {
            @Override
            public String handleRequest(I input, Map<String, String> pathParams, Map<String, String> queryParams,
                    eu.cloudhopper.mc.runtime.HandlerContext context) {
                CALLS.incrementAndGet();
                return "called";
            }
        };
    }

    private static <I> ApiGatewayEventRouter<I, String> router(Class<I> type) {
        RequestHandler<I, String> plain = (input, context) -> {
            CALLS.incrementAndGet();
            return "called";
        };
        ApiGatewayV2HttpEventHandler<I, String> http = new ApiGatewayV2HttpEventHandler<>(function(), type) { };
        return new ApiGatewayEventRouter<>(plain, null, http, null, null, type);
    }

    @Test
    public void primesTheRequestPathWithoutCallingTheFunction() {
        CALLS.set(0);
        int primed = SnapStartPriming.prime(List.of(router(Order.class), router(String.class), router(Void.class),
                router(Integer.class), router(Map.class)));
        assertEquals(5, primed);
        assertEquals(0, CALLS.get());
    }

    @Test
    public void aRouterWithoutHttpHandlerIsPrimedToo() {
        CALLS.set(0);
        RequestHandler<String, String> plain = (input, context) -> {
            CALLS.incrementAndGet();
            return "called";
        };
        assertEquals(1, SnapStartPriming.prime(List.of(new ApiGatewayEventRouter<>(plain, null, null))));
        assertEquals(0, CALLS.get());
    }

    @Test
    public void callsTheOnPrimeMethodsOfTheFunction() {
        AtomicInteger primed = new AtomicInteger();
        CloudRequestHandler<String, String> function = new CloudRequestHandler<>() {
            @Override
            public String handleRequest(String input, Map<String, String> pathParams, Map<String, String> queryParams,
                    eu.cloudhopper.mc.runtime.HandlerContext context) {
                CALLS.incrementAndGet();
                return "called";
            }

            @Override
            public void prime() {
                primed.incrementAndGet();
            }
        };
        CALLS.set(0);
        AwsLambdaRequestHandler<String, String> plain = new AwsLambdaRequestHandler<>(function) { };
        assertEquals(1, SnapStartPriming.prime(List.of(new ApiGatewayEventRouter<>(plain, null, null))));
        assertEquals(1, primed.get());
        assertEquals(0, CALLS.get());
    }
}
