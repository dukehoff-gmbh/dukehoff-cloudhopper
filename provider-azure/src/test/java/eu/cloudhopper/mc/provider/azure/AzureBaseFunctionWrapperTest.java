package eu.cloudhopper.mc.provider.azure;

/*-
 * #%L
 * provider-azure - a library from the "Cloudhopper" project.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.microsoft.azure.functions.HttpRequestMessage;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzureBaseFunctionWrapperTest {

    // path matching needs no handler
    private final AzureBaseFunctionWrapper<Object, Object> wrapper =
            new AzureBaseFunctionWrapper<>(null, Object.class) {
            };

    private static HttpRequestMessage<?> request(String path) {
        return (HttpRequestMessage<?>) Proxy.newProxyInstance(AzureBaseFunctionWrapperTest.class.getClassLoader(),
                new Class<?>[]{HttpRequestMessage.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUri")) {
                        return URI.create("https://app.azurewebsites.net" + path);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void extractsPathParameters() {
        assertEquals(Map.of("id", "42"),
                wrapper.extractPathParams(request("/api/player/42"), "/player/{id}"));
    }

    @Test
    void matchesLiteralSegmentsRegardlessOfCase() {
        // Azure Functions routes /api/projectConfig_http_function_trigger to the route
        // projectconfig_http_function_trigger
        assertEquals(Map.of(), wrapper.extractPathParams(
                request("/api/projectConfig_http_function_trigger"), "/projectconfig_http_function_trigger"));
    }

    @Test
    void rejectsOtherSegments() {
        assertThrows(IllegalArgumentException.class,
                () -> wrapper.extractPathParams(request("/api/match/42"), "/player/{id}"));
    }
}
