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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NativeRuntimeTest {

    /** A handler as the generated ones: a public class with a no-argument constructor. */
    public static class Echo implements RequestHandler<Object, Object> {
        @Override
        public Object handleRequest(Object input, Context context) {
            if (input instanceof Map<?, ?> map && map.containsKey("fail")) {
                throw new IllegalStateException("failed on purpose");
            }
            return Map.of("input", input, "requestId", context.getAwsRequestId(),
                    "remaining", context.getRemainingTimeInMillis() > 0);
        }
    }

    private HttpServer server;
    private final List<String> posted = new CopyOnWriteArrayList<>();
    private volatile String event;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/2018-06-01/runtime/invocation/next", exchange -> {
            byte[] body = event.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Lambda-Runtime-Aws-Request-Id", "req-1");
            exchange.getResponseHeaders().add("Lambda-Runtime-Deadline-Ms", String.valueOf(System.currentTimeMillis() + 30_000));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/2018-06-01/runtime/invocation/req-1/", exchange -> {
            posted.add(exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("Lambda-Runtime-Function-Error-Type") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private NativeRuntime runtime() {
        return new NativeRuntime("127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void postsTheResultOfTheHandler() throws Exception {
        event = "{\"customer\":\"acme\",\"items\":[1,2]}";
        runtime().next(NativeRuntime.handler(Echo.class.getName() + "::handleRequest"));
        assertEquals(1, posted.size());
        String post = posted.get(0);
        assertTrue(post.startsWith("/2018-06-01/runtime/invocation/req-1/response null "), post);
        assertTrue(post.contains("\"customer\":\"acme\""), post);
        assertTrue(post.contains("\"requestId\":\"req-1\""), post);
        assertTrue(post.contains("\"remaining\":true"), post);
    }

    @Test
    void reportsAnExceptionAsTheInvocationsError() throws Exception {
        event = "{\"fail\":true}";
        runtime().next(NativeRuntime.handler(Echo.class.getName()));
        String post = posted.get(0);
        assertTrue(post.startsWith("/2018-06-01/runtime/invocation/req-1/error Runtime.UnhandledException "), post);
        assertTrue(post.contains("\"errorMessage\":\"failed on purpose\""), post);
        assertTrue(post.contains("\"errorType\":\"java.lang.IllegalStateException\""), post);
    }

    @Test
    void needsAHandlerSetting() {
        assertThrows(IllegalArgumentException.class, () -> NativeRuntime.handler(" "));
        assertThrows(ClassNotFoundException.class, () -> NativeRuntime.handler("com.example.Missing::handleRequest"));
    }
}
