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
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The result of an HTTP function with a status and headers of its own. Return it instead of the
 * body:
 *
 * <pre>{@code
 * public HttpResponse<Item> get(@PathParam("id") String id) {
 *     return HttpResponse.ok(items.get(id)).header("Cache-Control", "max-age=60");
 * }
 *
 * return HttpResponse.status(201, created).header("Location", "/items/" + created.id());
 * return HttpResponse.noContent();
 * return HttpResponse.ok(csv).header("Content-Type", "text/csv");
 * }</pre>
 *
 * <p>The body is sent as JSON, like the result of any HTTP function, unless the
 * {@code Content-Type} header names another media type and the body is a {@code String}: then it
 * is sent as it is. A response without a body has no content. Instances are immutable;
 * {@link #header(String, String)} returns a copy.</p>
 *
 * @param <T> the type of the body
 */
public final class HttpResponse<T> {

    private final int status;
    private final Map<String, String> headers;
    private final T body;

    private HttpResponse(int status, Map<String, String> headers, T body) {
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("HttpResponse needs a status from 200 to 599, not " + status);
        }
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    /**
     * @param body the body, sent as JSON
     * @param <T>  the type of the body
     * @return a response with status 200
     */
    public static <T> HttpResponse<T> ok(T body) {
        return status(200, body);
    }

    /**
     * @param status the HTTP status, 200 to 599
     * @param body   the body, or {@code null} for none
     * @param <T>    the type of the body
     * @return a response with the status
     */
    public static <T> HttpResponse<T> status(int status, T body) {
        return new HttpResponse<>(status, Collections.unmodifiableMap(emptyHeaders()), body);
    }

    /**
     * @param status the HTTP status, 200 to 599
     * @param <T>    the type of the body
     * @return a response with the status and without a body
     */
    public static <T> HttpResponse<T> status(int status) {
        return status(status, null);
    }

    /**
     * @param <T> the type of the body
     * @return a response with status 204 and without a body
     */
    public static <T> HttpResponse<T> noContent() {
        return status(204);
    }

    /**
     * Returns a copy with a header added or replaced; names are case-insensitive.
     *
     * @param name  the header name
     * @param value the header value
     * @return the copy
     */
    public HttpResponse<T> header(String name, String value) {
        checkHeader(name, value);
        TreeMap<String, String> copy = emptyHeaders();
        copy.putAll(headers);
        copy.put(name, value);
        return new HttpResponse<>(status, Collections.unmodifiableMap(copy), body);
    }

    /**
     * @return the HTTP status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the headers, keyed case-insensitively
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * @return the body, or {@code null} for none
     */
    public T getBody() {
        return body;
    }

    private static TreeMap<String, String> emptyHeaders() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    static void checkHeader(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank() || name.chars().anyMatch(c -> c <= ' ' || c == ':' || c > '~')) {
            throw new IllegalArgumentException("Invalid header name: " + name);
        }
        if (value.chars().anyMatch(c -> c == '\r' || c == '\n')) {
            throw new IllegalArgumentException("Header values must not contain line breaks: " + name);
        }
    }

    @Override
    public String toString() {
        return "HttpResponse[" + status + ", " + headers + ", " + body + "]";
    }
}
