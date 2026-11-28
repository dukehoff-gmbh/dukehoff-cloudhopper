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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The HTTP answer of a function, the same on every target: the providers turn the result of a
 * function, or the exception it threw, into status, headers and body with this class and only
 * write it in the form of their platform.
 *
 * <ul>
 *   <li>a result: status 200 with the result as JSON; an {@link HttpResponse} with its status
 *       and headers</li>
 *   <li>an {@link HttpStatusException}: its status and headers, body
 *       {@code {"error": "<message>"}}</li>
 *   <li>a request body that cannot be read as the input type: 400,
 *       {@code {"error": "Invalid request body"}}</li>
 *   <li>any other exception: 500, {@code {"error": "Internal Server Error"}}; the provider logs
 *       the exception ({@link #isUnexpected()})</li>
 * </ul>
 *
 * <p>Used by the provider modules; functions use {@link HttpResponse} and
 * {@link HttpStatusException}.</p>
 */
public final class HttpReply {

    /** The media type of JSON bodies. */
    public static final String JSON = "application/json";
    /** The name of the content type header. */
    public static final String CONTENT_TYPE = "Content-Type";

    private final int status;
    private final Map<String, String> headers;
    private final String body;
    private final boolean unexpected;

    private HttpReply(int status, Map<String, String> headers, String body, boolean unexpected) {
        this.status = status;
        this.headers = Collections.unmodifiableMap(headers);
        this.body = body;
        this.unexpected = unexpected;
    }

    /**
     * The answer for the result of a function.
     *
     * @param output the result, possibly an {@link HttpResponse}
     * @param json   serializes a body to JSON with the provider's mapper
     * @return the answer
     */
    public static HttpReply of(Object output, Function<Object, String> json) {
        if (!(output instanceof HttpResponse<?> response)) {
            return new HttpReply(200, jsonHeaders(), json.apply(output), false);
        }
        TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(response.getHeaders());
        Object body = response.getBody();
        if (body == null) {
            return new HttpReply(response.getStatus(), headers, null, false);
        }
        String contentType = headers.get(CONTENT_TYPE);
        if (contentType != null && !isJson(contentType) && body instanceof CharSequence text) {
            return new HttpReply(response.getStatus(), headers, text.toString(), false);
        }
        headers.putIfAbsent(CONTENT_TYPE, JSON);
        return new HttpReply(response.getStatus(), headers, json.apply(body), false);
    }

    /**
     * The answer for an exception a function threw.
     *
     * @param error the exception
     * @return the status and headers of an {@link HttpStatusException}, otherwise 500
     */
    public static HttpReply of(Throwable error) {
        if (error instanceof HttpStatusException e) {
            TreeMap<String, String> headers = jsonHeaders();
            headers.putAll(e.getHeaders());
            String message = e.getMessage() != null ? e.getMessage() : "Error";
            return new HttpReply(e.getStatus(), headers, errorBody(message), false);
        }
        return new HttpReply(500, jsonHeaders(), errorBody("Internal Server Error"), true);
    }

    /**
     * A hint for errors that exist only in a native image: a type, resource or proxy without
     * reachability metadata surfaces as an {@link Error} such as
     * {@code org.graalvm.nativeimage.MissingReflectionRegistrationError}, not as an exception.
     *
     * @param error an error of a function call
     * @return what to do about it, or {@code null} if the error is not one of these
     */
    public static String nativeImageHint(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (isNativeImageError(e.getClass().getName())) {
                return "Native image: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + " Mark the type with @Reflective (eu.cloudhopper.mc.annotations), or register it in"
                        + " META-INF/native-image/<groupId>/<artifactId>/reachability-metadata.json.";
            }
        }
        return null;
    }

    /**
     * @param type the class name of an error
     * @return whether it is one of the errors of missing reachability metadata in a native image
     */
    static boolean isNativeImageError(String type) {
        return type.startsWith("org.graalvm.nativeimage.Missing") || type.equals("com.oracle.svm.core.jdk.UnsupportedFeatureError");
    }

    /**
     * @return the answer for a request body that cannot be read as the input type
     */
    public static HttpReply invalidBody() {
        return new HttpReply(400, jsonHeaders(), errorBody("Invalid request body"), false);
    }

    /**
     * @return the HTTP status
     */
    public int status() {
        return status;
    }

    /**
     * @return the response headers, keyed case-insensitively
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * @return the body, or {@code null} for none
     */
    public String body() {
        return body;
    }

    /**
     * @return the content type, or {@code null} if the answer has none
     */
    public String contentType() {
        return headers.get(CONTENT_TYPE);
    }

    /**
     * @return {@code true} for the 500 of an unexpected exception, which the provider logs
     */
    public boolean isUnexpected() {
        return unexpected;
    }

    private static TreeMap<String, String> jsonHeaders() {
        TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put(CONTENT_TYPE, JSON);
        return headers;
    }

    /**
     * @param contentType a content type, possibly with parameters
     * @return {@code true} for {@code application/json} and the {@code +json} types
     */
    public static boolean isJson(String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters);
        }
        type = type.trim();
        return type.equals(JSON) || type.endsWith("+json");
    }

    /**
     * @param message the error message
     * @return {@code {"error": "<message>"}}
     */
    static String errorBody(String message) {
        StringBuilder json = new StringBuilder("{\"error\":\"");
        for (char c : message.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append("\"}").toString();
    }
}
