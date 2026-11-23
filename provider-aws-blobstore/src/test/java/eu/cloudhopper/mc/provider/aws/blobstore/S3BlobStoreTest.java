package eu.cloudhopper.mc.provider.aws.blobstore;

/*-
 * #%L
 * provider-aws-blobstore - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
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

/** Against a small S3 emulation: path-style bucket "data", two keys per list page. */
public class S3BlobStoreTest {

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
            requests.add(method + " " + exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            int status = 200;
            byte[] body = new byte[0];
            if (authorization == null || !authorization.contains("/eu-central-1/s3/aws4_request")
                    || exchange.getRequestHeaders().getFirst("X-Amz-Content-Sha256") == null) {
                status = 400;
            } else if (unavailable.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                status = 503;
                body = "<Error><Code>SlowDown</Code></Error>".getBytes(StandardCharsets.UTF_8);
            } else if (!rawPath.startsWith("/data/")) {
                status = 404;
                body = "<Error><Code>NoSuchBucket</Code></Error>".getBytes(StandardCharsets.UTF_8);
            } else {
                String key = URLDecoder.decode(rawPath.substring("/data/".length()).replace("+", "%2B"), StandardCharsets.UTF_8);
                if (key.startsWith("secret/")) {
                    status = 403;
                    body = "<Error><Code>AccessDenied</Code></Error>".getBytes(StandardCharsets.UTF_8);
                } else if (key.isEmpty() && "GET".equals(method)) {
                    body = list(exchange.getRequestURI().getRawQuery()).getBytes(StandardCharsets.UTF_8);
                } else if ("GET".equals(method)) {
                    body = objects.get(key);
                    if (body == null) {
                        status = 404;
                        body = "<Error><Code>NoSuchKey</Code></Error>".getBytes(StandardCharsets.UTF_8);
                    }
                } else if ("PUT".equals(method)) {
                    objects.put(key, exchange.getRequestBody().readAllBytes());
                    types.put(key, exchange.getRequestHeaders().getFirst("Content-Type"));
                } else if ("DELETE".equals(method)) {
                    objects.remove(key);
                    status = 204;
                }
            }
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    /** ListObjectsV2 with two keys per page; the continuation token is the last key returned. */
    private String list(String query) {
        Map<String, String> params = new TreeMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            params.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        String prefix = params.getOrDefault("prefix", "");
        String after = params.getOrDefault("continuation-token", "");
        List<String> matching = new ArrayList<>();
        synchronized (objects) {
            objects.keySet().stream().filter(k -> k.startsWith(prefix) && k.compareTo(after) > 0).forEach(matching::add);
        }
        List<String> page = matching.subList(0, Math.min(2, matching.size()));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>data</Name>");
        page.forEach(k -> xml.append("<Contents><Key>").append(k.replace("&", "&amp;")).append("</Key><Size>1</Size></Contents>"));
        boolean truncated = matching.size() > 2;
        xml.append("<IsTruncated>").append(truncated).append("</IsTruncated>");
        if (truncated) {
            xml.append("<NextContinuationToken>").append(page.get(1).replace("&", "&amp;")).append("</NextContinuationToken>");
        }
        return xml.append("</ListBucketResult>").toString();
    }

    @AfterEach
    public void stop() {
        server.stop(0);
    }

    private S3BlobStore store(String bucket) {
        URI root = S3BlobStoreFactory.root(bucket, "eu-central-1", "http://127.0.0.1:" + server.getAddress().getPort());
        return new S3BlobStore(HttpClient.newHttpClient(), root, bucket, "eu-central-1",
                () -> new AwsCredentials("AKIDEXAMPLE", "secret", "token"), Clock.systemUTC(), Duration.ofMillis(1));
    }

    @Test
    public void putGetAndDeleteAnObjectWithAKeyThatNeedsEncoding() {
        S3BlobStore store = store("data");
        byte[] content = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        store.put("reports/2026 11/a+b&c.json", content, "application/json");

        assertTrue(requests.contains("PUT /data/reports/2026%2011/a%2Bb%26c.json"), requests.toString());
        assertEquals("application/json", types.get("reports/2026 11/a+b&c.json"));
        assertArrayEquals(content, store.get("reports/2026 11/a+b&c.json").orElseThrow());
        store.delete("reports/2026 11/a+b&c.json");
        assertTrue(store.get("reports/2026 11/a+b&c.json").isEmpty());
    }

    @Test
    public void listsAllPagesOfAPrefix() {
        S3BlobStore store = store("data");
        for (String key : List.of("a/1", "a/2", "a/3&x", "a/4", "a/5", "b/1")) {
            store.put(key, new byte[]{1}, "application/octet-stream");
        }

        assertEquals(List.of("a/1", "a/2", "a/3&x", "a/4", "a/5"), store.list("a/"));
        assertEquals(List.of("a/1", "a/2", "a/3&x", "a/4", "a/5", "b/1"), store.list(""));
        assertEquals(List.of(), store.list("c/"));
    }

    @Test
    public void aRefusedOperationIsAccessDenied() {
        BlobAccessDeniedException denied = assertThrows(BlobAccessDeniedException.class,
                () -> store("data").put("secret/x", new byte[]{1}, "text/plain"));
        assertTrue(denied.getMessage().contains("403 AccessDenied"), denied.getMessage());
    }

    @Test
    public void aMissingBucketIsAnErrorNotAMissingObject() {
        BlobStoreException missing = assertThrows(BlobStoreException.class, () -> store("other").get("x"));
        assertEquals("Bucket other does not exist", missing.getMessage());
    }

    @Test
    public void throttlingIsRetried() {
        unavailable.set(2);
        store("data").put("x", new byte[]{1}, "text/plain");
        assertArrayEquals(new byte[]{1}, objects.get("x"));
    }

    @Test
    public void bucketsAreAddressedInTheHostNameOrByPath() {
        assertEquals(URI.create("https://data.s3.eu-central-1.amazonaws.com/"), S3BlobStoreFactory.root("data", "eu-central-1", null));
        assertEquals(URI.create("https://s3.eu-central-1.amazonaws.com/my.data/"), S3BlobStoreFactory.root("my.data", "eu-central-1", null));
        assertEquals(URI.create("https://s3.fr-par.scw.cloud/data/"), S3BlobStoreFactory.root("data", "fr-par", "https://s3.fr-par.scw.cloud"));
    }
}
