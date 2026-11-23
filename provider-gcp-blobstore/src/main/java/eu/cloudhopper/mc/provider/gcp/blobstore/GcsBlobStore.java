package eu.cloudhopper.mc.provider.gcp.blobstore;


/*-
 * #%L
 * provider-gcp-blobstore - a library from the "Cloudhopper" project.
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.cloudhopper.mc.runtime.BlobAccessDeniedException;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Cloud Storage bucket, through the JSON API with an access token of the function's service
 * account. Throttling, server errors and I/O errors are retried with backoff; HTTP 403 becomes a
 * {@link BlobAccessDeniedException}.
 */
class GcsBlobStore implements BlobStore {

    private static final int ATTEMPTS = 4;
    private static final byte[] NONE = new byte[0];

    interface TokenSource {
        String token() throws IOException;
    }

    private final HttpClient http;
    private final String api;
    private final String bucket;
    private final TokenSource tokens;
    private final Duration backoff;

    /**
     * @param api the API's base URL, ending with "/", e.g. {@code https://storage.googleapis.com/}
     */
    GcsBlobStore(HttpClient http, URI api, String bucket, TokenSource tokens, Duration backoff) {
        this.http = http;
        this.api = api.toString();
        this.bucket = bucket;
        this.tokens = tokens;
        this.backoff = backoff;
    }

    @Override
    public Optional<byte[]> get(String key) {
        HttpResponse<byte[]> response = call("GET", object(key) + "?alt=media", null, NONE, "get " + key);
        return response.statusCode() == 404 ? Optional.empty() : Optional.of(response.body());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call("POST", api + "upload/storage/v1/b/" + encode(bucket) + "/o?uploadType=media&name=" + encode(key),
                contentType, content, "put " + key);
    }

    @Override
    public void delete(String key) {
        call("DELETE", object(key), null, NONE, "delete " + key);
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            StringBuilder uri = new StringBuilder(api).append("storage/v1/b/").append(encode(bucket))
                    .append("/o?fields=items(name),nextPageToken&prefix=").append(encode(prefix));
            if (token != null) {
                uri.append("&pageToken=").append(encode(token));
            }
            HttpResponse<byte[]> response = call("GET", uri.toString(), null, NONE, "list " + prefix);
            JsonObject page = json(response.body());
            if (page.has("items")) {
                for (JsonElement item : page.getAsJsonArray("items")) {
                    keys.add(item.getAsJsonObject().get("name").getAsString());
                }
            }
            token = page.has("nextPageToken") ? page.get("nextPageToken").getAsString() : null;
        } while (token != null && !token.isEmpty());
        return keys;
    }

    private String object(String key) {
        return api + "storage/v1/b/" + encode(bucket) + "/o/" + encode(key);
    }

    /**
     * One request, retried on throttling, server errors and I/O errors. Returns the response of a
     * success or of a missing object (GET and DELETE); anything else is an exception.
     */
    private HttpResponse<byte[]> call(String method, String uri, String contentType, byte[] body, String what) {
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + tokens.token())
                        .method(method, body.length == 0 && !"POST".equals(method) ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(body));
                if (contentType != null) {
                    builder.header("Content-Type", contentType);
                }
                HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response;
                }
                String message = errorMessage(response.body());
                lastError = status + " " + message;
                if (status == 404) {
                    if (message.contains("bucket does not exist")) {
                        throw new BlobStoreException("Bucket " + bucket + " does not exist");
                    }
                    return response; // no such object
                }
                if (status == 403) {
                    throw new BlobAccessDeniedException("Cloud Storage refused " + what + " in bucket " + bucket
                            + ": " + lastError);
                }
                if (status < 500 && status != 408 && status != 429) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BlobStoreException("Interrupted during " + what + " in bucket " + bucket, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, what);
            }
        }
        throw new BlobStoreException("Cloud Storage " + what + " in bucket " + bucket + " failed: " + lastError);
    }

    /** RFC 3986 encoding of everything but unreserved characters; object names encode "/" too. */
    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
            }
        }
        return out.toString();
    }

    private static JsonObject json(byte[] body) {
        try {
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new BlobStoreException("Cannot read the Cloud Storage response", e);
        }
    }

    /**
     * The message of an error response: JSON ({@code {"error": {"message": ...}}}) from the
     * metadata endpoints, plain text from media downloads ({@code No such object: ...}).
     */
    private static String errorMessage(byte[] body) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8).trim();
        try {
            JsonObject error = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("error");
            return error != null && error.has("message") ? error.get("message").getAsString() : text;
        } catch (RuntimeException e) {
            return text;
        }
    }

    private void pause(int attempt, String what) {
        try {
            Thread.sleep(backoff.toMillis() << (2 * (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlobStoreException("Interrupted during " + what + " in bucket " + bucket, e);
        }
    }
}
