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

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entry point of a function built as a native image for the Lambda custom runtime
 * ({@code provided.al2023}): the {@code bootstrap} executable runs this loop. It creates the
 * generated handler that the function's handler setting names ({@code _HANDLER}, e.g.
 * {@code com.example.AwsLambdaHello_Handler$Auto::handleRequest}, as for the Java runtime), then
 * fetches each invocation from the
 * <a href="https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html">Lambda runtime API</a>,
 * passes the event to the handler as JSON objects and posts the result.
 *
 * <p>One executable serves all functions of a project, like the jar. An exception of the handler
 * is reported as the invocation's error; HTTP errors of the function are ordinary results
 * (status codes in the response).
 */
public final class NativeRuntime {

    private static final String VERSION = "/2018-06-01/runtime/";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final String api;

    NativeRuntime(String runtimeApi) {
        this.api = "http://" + runtimeApi + VERSION;
    }

    /**
     * Runs the loop until the execution environment ends.
     *
     * @param args not used
     * @throws IOException if the runtime API cannot be reached
     */
    public static void main(String[] args) throws IOException {
        NativeRuntime runtime = new NativeRuntime(System.getenv("AWS_LAMBDA_RUNTIME_API"));
        RequestHandler<Object, Object> handler;
        try {
            handler = handler(System.getenv("_HANDLER"));
        } catch (Exception | LinkageError e) {
            runtime.post("init/error", error(e), "Runtime.InitError");
            throw new IllegalStateException("Cannot create the handler " + System.getenv("_HANDLER"), e);
        }
        while (true) {
            runtime.next(handler);
        }
    }

    /**
     * @param setting the handler setting, {@code <class>::<method>} or {@code <class>}
     * @return a new instance of the class, a generated handler
     * @throws ReflectiveOperationException if the class cannot be created
     */
    @SuppressWarnings("unchecked")
    static RequestHandler<Object, Object> handler(String setting) throws ReflectiveOperationException {
        if (setting == null || setting.isBlank()) {
            throw new IllegalArgumentException("No handler set (_HANDLER)");
        }
        int separator = setting.indexOf("::");
        String className = separator < 0 ? setting : setting.substring(0, separator);
        return (RequestHandler<Object, Object>) Class.forName(className).getDeclaredConstructor().newInstance();
    }

    /**
     * Fetches the next invocation, runs the handler and posts its result or error.
     *
     * @param handler the handler
     * @throws IOException if the runtime API cannot be reached
     */
    void next(RequestHandler<Object, Object> handler) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(api + "invocation/next").toURL().openConnection();
        byte[] event;
        try (InputStream in = connection.getInputStream()) {
            event = in.readAllBytes();
        }
        String requestId = connection.getHeaderField("Lambda-Runtime-Aws-Request-Id");
        String traceId = connection.getHeaderField("Lambda-Runtime-Trace-Id");
        if (traceId != null) {
            System.setProperty("com.amazonaws.xray.traceHeader", traceId);
        }
        Context context = new InvocationContext(requestId,
                connection.getHeaderField("Lambda-Runtime-Invoked-Function-Arn"),
                parseLong(connection.getHeaderField("Lambda-Runtime-Deadline-Ms")));
        byte[] result;
        try {
            result = MAPPER.writeValueAsBytes(handler.handleRequest(MAPPER.readValue(event, Object.class), context));
        } catch (Exception | LinkageError e) {
            String hint = eu.cloudhopper.mc.runtime.HttpReply.nativeImageHint(e);
            System.err.println((hint != null ? hint + "\n" : "") + "Invocation " + requestId + " failed: " + stackTrace(e));
            post("invocation/" + requestId + "/error", error(e), "Runtime.UnhandledException");
            return;
        }
        post("invocation/" + requestId + "/response", result, null);
    }

    private void post(String path, byte[] body, String errorType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(api + path).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        if (errorType != null) {
            connection.setRequestProperty("Lambda-Runtime-Function-Error-Type", errorType);
        }
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        try (InputStream in = connection.getInputStream()) {
            in.readAllBytes();
        }
    }

    static byte[] error(Throwable e) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("errorMessage", String.valueOf(e.getMessage()));
        error.put("errorType", e.getClass().getName());
        error.put("stackTrace", List.of(stackTrace(e).split("\n")));
        return MAPPER.writeValueAsBytes(error);
    }

    private static String stackTrace(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The context of one invocation, from the headers of the runtime API and the environment. */
    private record InvocationContext(String requestId, String functionArn, long deadline) implements Context {

        @Override
        public String getAwsRequestId() {
            return requestId;
        }

        @Override
        public String getLogGroupName() {
            return System.getenv("AWS_LAMBDA_LOG_GROUP_NAME");
        }

        @Override
        public String getLogStreamName() {
            return System.getenv("AWS_LAMBDA_LOG_STREAM_NAME");
        }

        @Override
        public String getFunctionName() {
            return System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        }

        @Override
        public String getFunctionVersion() {
            return System.getenv("AWS_LAMBDA_FUNCTION_VERSION");
        }

        @Override
        public String getInvokedFunctionArn() {
            return functionArn;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return deadline == 0 ? 0 : (int) Math.max(0, deadline - System.currentTimeMillis());
        }

        @Override
        public int getMemoryLimitInMB() {
            try {
                return Integer.parseInt(System.getenv().getOrDefault("AWS_LAMBDA_FUNCTION_MEMORY_SIZE", "0"));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println(message);
                }

                @Override
                public void log(byte[] message) {
                    System.out.println(new String(message, StandardCharsets.UTF_8));
                }
            };
        }
    }
}
