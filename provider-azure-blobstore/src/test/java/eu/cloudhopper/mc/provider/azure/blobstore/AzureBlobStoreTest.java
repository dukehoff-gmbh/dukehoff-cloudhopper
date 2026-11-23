package eu.cloudhopper.mc.provider.azure.blobstore;


/*-
 * #%L
 * provider-azure-blobstore - a library from the "Cloudhopper" project.
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.BlobAccessDeniedException;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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

/**
 * Against a small emulation of the Blob service: container "data", two blobs per list page; every
 * request must carry the SharedKey signature of its method, path, query and headers.
 */
public class AzureBlobStoreTest {

    private static final StorageAccount ACCOUNT = new StorageAccount("acct", "c2VjcmV0", "http", "local");

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
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(method + " " + rawPath);
            int status = 200;
            String code = null;
            byte[] body = new byte[0];
            if (!signed(exchange, method, rawPath, query, requestBody.length)) {
                status = 403;
                code = "AuthenticationFailed";
            } else if (unavailable.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                status = 503;
                code = "ServerBusy";
            } else if (!rawPath.equals("/data") && !rawPath.startsWith("/data/")) {
                status = 404;
                code = "ContainerNotFound";
            } else if (rawPath.equals("/data") && "list".equals(query.get("comp"))) {
                body = list(query).getBytes(StandardCharsets.UTF_8);
            } else {
                String name = URLDecoder.decode(rawPath.substring("/data/".length()).replace("+", "%2B"), StandardCharsets.UTF_8);
                if (name.startsWith("secret/")) {
                    status = 403;
                    code = "AuthorizationPermissionMismatch";
                } else if ("PUT".equals(method)) {
                    if (!"BlockBlob".equals(exchange.getRequestHeaders().getFirst("x-ms-blob-type"))) {
                        status = 400;
                    } else {
                        objects.put(name, requestBody);
                        types.put(name, exchange.getRequestHeaders().getFirst("Content-Type"));
                        status = 201;
                    }
                } else if (!objects.containsKey(name)) {
                    status = 404;
                    code = "BlobNotFound";
                } else if ("GET".equals(method)) {
                    body = objects.get(name);
                } else if ("DELETE".equals(method)) {
                    objects.remove(name);
                    status = 202;
                }
            }
            if (code != null) {
                exchange.getResponseHeaders().add("x-ms-error-code", code);
            }
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    /** The signature the service computes from the received request. */
    private static boolean signed(HttpExchange exchange, String method, String rawPath, Map<String, String> query, int length) {
        Map<String, String> msHeaders = new TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (name.toLowerCase().startsWith("x-ms-")) {
                msHeaders.put(name.toLowerCase(), values.get(0));
            }
        });
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String expected = ACCOUNT.sharedKey(method, rawPath, query, length, contentType == null ? "" : contentType, msHeaders);
        return expected.equals(exchange.getRequestHeaders().getFirst("Authorization"));
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

    /** Two blobs per page; the marker is the last name returned. */
    private String list(Map<String, String> query) {
        String prefix = query.getOrDefault("prefix", "");
        String after = query.getOrDefault("marker", "");
        List<String> matching = new ArrayList<>();
        synchronized (objects) {
            objects.keySet().stream().filter(k -> k.startsWith(prefix) && k.compareTo(after) > 0).forEach(matching::add);
        }
        List<String> page = matching.subList(0, Math.min(2, matching.size()));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<EnumerationResults ContainerName=\"data\"><Blobs>");
        page.forEach(k -> xml.append("<Blob><Name>").append(k.replace("&", "&amp;")).append("</Name><Properties/></Blob>"));
        xml.append("</Blobs><NextMarker>").append(matching.size() > 2 ? page.get(1) : "").append("</NextMarker>");
        return xml.append("</EnumerationResults>").toString();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private AzureBlobStore store(String container) {
        return new AzureBlobStore(HttpClient.newHttpClient(), ACCOUNT, URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                container, Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Test
    public void putGetAndDeleteABlob() {
        AzureBlobStore store = store("data");
        store.put("reports/a b+c.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), store.get("reports/a b+c.txt").orElseThrow());
        assertEquals("text/plain", types.get("reports/a b+c.txt"));
        assertTrue(requests.contains("GET /data/reports/a%20b%2Bc.txt"), requests.toString());
        store.delete("reports/a b+c.txt");
        assertTrue(store.get("reports/a b+c.txt").isEmpty());
    }

    @Test
    public void anEmptyBlobCanBeWritten() {
        store("data").put("empty", new byte[0], "application/octet-stream");
        assertArrayEquals(new byte[0], objects.get("empty"));
    }

    @Test
    public void aMissingBlobIsEmptyAndDeletingItIsNoError() {
        AzureBlobStore store = store("data");
        assertTrue(store.get("nothing").isEmpty());
        store.delete("nothing");
    }

    @Test
    public void listFollowsThePages() {
        AzureBlobStore store = store("data");
        for (String key : List.of("a/1", "a/2", "a/3", "a/4", "a/5", "b/1")) {
            objects.put(key, new byte[]{1});
        }
        assertEquals(List.of("a/1", "a/2", "a/3", "a/4", "a/5"), store.list("a/"));
        assertEquals(List.of("a/1", "a/2", "a/3", "a/4", "a/5", "b/1"), store.list(""));
    }

    @Test
    public void aRefusedRequestIsAccessDenied() {
        assertThrows(BlobAccessDeniedException.class, () -> store("data").get("secret/x"));
    }

    @Test
    public void serverErrorsAreRetried() {
        unavailable.set(2);
        store("data").put("k", new byte[]{1}, "application/octet-stream");
        assertArrayEquals(new byte[]{1}, objects.get("k"));
    }

    @Test
    public void aMissingContainerIsAnError() {
        BlobStoreException e = assertThrows(BlobStoreException.class, () -> store("other").get("k"));
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
    }
}
