package eu.cloudhopper.mc.test.tck.api;

/*-
 * #%L
 * test-tck-api - a library from the "Cloudhopper" project.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class HttpClientHelper {

    public static HttpResponse get(URI uri) throws Exception {
        return request("GET", uri, null);
    }

    public static HttpResponse post(URI uri, String jsonBody) throws Exception {
        return request("POST", uri, jsonBody);
    }

    public static HttpResponse put(URI uri, String jsonBody) throws Exception {
        return request("PUT", uri, jsonBody);
    }

    public static HttpResponse delete(URI uri) throws Exception {
        return request("DELETE", uri, null);
    }

    public static HttpResponse patch(URI uri, String jsonBody) throws Exception {
        return request("PATCH", uri, jsonBody);
    }

    private static HttpResponse request(String method, URI uri, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        int responseCode = conn.getResponseCode();
        InputStream responseStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();

        if (responseStream == null) {
            throw new IOException("No response stream available for " + method + " " + uri + " (HTTP " + responseCode + ")");
        }

        String responseBody = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);

        return new HttpResponse(responseCode, responseBody);
    }

    public static class HttpResponse {
        private final int statusCode;
        private final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }
}