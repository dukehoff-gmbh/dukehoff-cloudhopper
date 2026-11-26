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
import java.util.TreeMap;

/**
 * Ends an HTTP function with a client or server error status instead of 500, on every target:
 *
 * <pre>{@code
 * Item item = items.find(id).orElseThrow(() -> new HttpStatusException(404, "no such item"));
 * }</pre>
 *
 * <p>The response has the status, the headers added with {@link #header(String, String)} and the
 * body {@code {"error": "<message>"}} as JSON. Use it for statuses 400 to 599; any other exception
 * an HTTP function throws answers {@code 500} with the body {@code {"error": "Internal Server
 * Error"}}, and the exception goes to the log only. Functions triggered by events or schedules
 * treat it like any other exception.</p>
 */
public class HttpStatusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * @param status  the HTTP status, 400 to 599
     * @param message the error message sent to the client
     * @throws IllegalArgumentException if the status is not between 400 and 599
     */
    public HttpStatusException(int status, String message) {
        this(status, message, null);
    }

    /**
     * @param status  the HTTP status, 400 to 599
     * @param message the error message sent to the client
     * @param cause   the cause, logged but not sent to the client
     * @throws IllegalArgumentException if the status is not between 400 and 599
     */
    public HttpStatusException(int status, String message, Throwable cause) {
        super(message, cause);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("HttpStatusException needs a status from 400 to 599, not " + status);
        }
        this.status = status;
    }

    /**
     * Adds a response header, e.g. {@code WWW-Authenticate} for 401 or {@code Retry-After} for 503.
     *
     * @param name  the header name
     * @param value the header value
     * @return this exception
     */
    public HttpStatusException header(String name, String value) {
        HttpResponse.checkHeader(name, value);
        headers.put(name, value);
        return this;
    }

    /**
     * @return the HTTP status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the response headers, keyed case-insensitively
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
