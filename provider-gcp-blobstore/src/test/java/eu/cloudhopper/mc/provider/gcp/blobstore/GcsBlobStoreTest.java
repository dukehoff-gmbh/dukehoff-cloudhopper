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

import com.sun.net.httpserver.HttpServer;
import eu.cloudhopper.mc.runtime.BlobAccessDeniedException;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Against a small emulation of the Cloud Storage JSON API: bucket "data", two names per list page. */
public class GcsBlobStoreTest {

    private HttpServer server;
    private final Map<String, byte[]> objects = Collections.synchronizedMap(new TreeMap<>());
    private final Map<String, String> types = Collections.synchronizedMap(new TreeMap<>());
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger unavailable = new AtomicInteger();

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String rawPath = exchange.getRequestURI().getRawPath();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            requests.add(method + " " + rawPath);
            int status = 200;
            String body = "";
            byte[] content = null;
            String objectPath = "/storage/v1/b/data/o/";
            if (!"Bearer token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                status = 401;
                body = error("Unauthorized");
            } else if (unavailable.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                status = 503;
                body = error("Backend Error");
            } else if (!rawPath.contains("/b/data/")) {
                status = 404;
                body = error("The specified bucket does not exist.");
            } else if (rawPath.equals("/upload/storage/v1/b/data/o") && "POST".equals(method)) {
                String name = query.get("name");
                if (name.startsWith("secret/")) {
                    status = 403;
                    body = error("caller does not have storage.objects.create access");
                } else {
                    objects.put(name, exchange.getRequestBody().readAllBytes());
                    types.put(name, exchange.getRequestHeaders().getFirst("Content-Type"));
                    body = "{\"name\":\"" + name + "\"}";
                }
            } else if (rawPath.equals("/storage/v1/b/data/o") && "GET".equals(method)) {
                body = list(query);
            } else if (rawPath.startsWith(objectPath)) {
                String name = URLDecoder.decode(rawPath.substring(objectPath.length()).replace("+", "%2B"), StandardCharsets.UTF_8);
                if (!objects.containsKey(name)) {
                    status = 404;
                    body = error("No such object: data/" + name);
                } else if ("GET".equals(method) && "media".equals(query.get("alt"))) {
                    content = objects.get(name);
                } else if ("DELETE".equals(method)) {
                    objects.remove(name);
                    status = 204;
                }
            }
            byte[] bytes = content != null ? content : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> params = new TreeMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                params.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static String error(String message) {
        return "{\"error\":{\"message\":\"" + message + "\"}}";
    }

    /** Two names per page; the page token is the last name returned. */
    private String list(Map<String, String> query) {
        String prefix = query.getOrDefault("prefix", "");
        String after = query.getOrDefault("pageToken", "");
        List<String> matching = new ArrayList<>();
        synchronized (objects) {
            objects.keySet().stream().filter(k -> k.startsWith(prefix) && k.compareTo(after) > 0).forEach(matching::add);
        }
        List<String> page = matching.subList(0, Math.min(2, matching.size()));
        StringBuilder json = new StringBuilder("{");
        if (!page.isEmpty()) {
            json.append("\"items\":[");
            for (int i = 0; i < page.size(); i++) {
                json.append(i > 0 ? "," : "").append("{\"name\":\"").append(page.get(i)).append("\"}");
            }
            json.append("]");
        }
        if (matching.size() > 2) {
            json.append(",\"nextPageToken\":\"").append(page.get(1)).append("\"");
        }
        return json.append("}").toString();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private GcsBlobStore store(String bucket) {
        return new GcsBlobStore(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                bucket, () -> "token", Duration.ofMillis(1));
    }

    @Test
    public void putGetAndDeleteAnObject() {
        GcsBlobStore store = store("data");
        store.put("reports/a b+c.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), store.get("reports/a b+c.txt").orElseThrow());
        assertEquals("text/plain", types.get("reports/a b+c.txt"));
        assertTrue(requests.contains("GET /storage/v1/b/data/o/reports%2Fa%20b%2Bc.txt"), requests.toString());
        store.delete("reports/a b+c.txt");
        assertTrue(store.get("reports/a b+c.txt").isEmpty());
    }

    @Test
    public void aMissingObjectIsEmptyAndDeletingItIsNoError() {
        GcsBlobStore store = store("data");
        assertTrue(store.get("nothing").isEmpty());
        store.delete("nothing");
    }

    @Test
    public void listFollowsThePages() {
        GcsBlobStore store = store("data");
        for (String key : List.of("a/1", "a/2", "a/3", "a/4", "a/5", "b/1")) {
            objects.put(key, new byte[]{1});
        }
        assertEquals(List.of("a/1", "a/2", "a/3", "a/4", "a/5"), store.list("a/"));
        assertEquals(List.of(), store.list("c/"));
    }

    @Test
    public void aRefusedRequestIsAccessDenied() {
        assertThrows(BlobAccessDeniedException.class, () -> store("data").put("secret/x", new byte[]{1}, "application/octet-stream"));
    }

    @Test
    public void serverErrorsAreRetried() {
        unavailable.set(2);
        store("data").put("k", new byte[]{1}, "application/octet-stream");
        assertArrayEquals(new byte[]{1}, objects.get("k"));
    }

    @Test
    public void aMissingBucketIsAnError() {
        BlobStoreException e = assertThrows(BlobStoreException.class, () -> store("other").get("k"));
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
    }
}
