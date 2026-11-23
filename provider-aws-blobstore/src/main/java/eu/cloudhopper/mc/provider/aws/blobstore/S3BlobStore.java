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

import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.provider.aws.auth.SigV4Signer;
import eu.cloudhopper.mc.runtime.BlobAccessDeniedException;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A bucket in S3 or an S3-compatible object storage, through the S3 REST API signed with SigV4.
 * Throttling, server errors and I/O errors are retried with backoff; HTTP 403 becomes a
 * {@link BlobAccessDeniedException}.
 */
class S3BlobStore implements BlobStore {

    private static final int ATTEMPTS = 4;
    private static final byte[] NONE = new byte[0];

    private final HttpClient http;
    private final URI root;
    private final String bucket;
    private final String region;
    private final Supplier<AwsCredentials> credentials;
    private final Clock clock;
    private final Duration backoff;

    /**
     * @param root the bucket's URL, ending with "/": {@code https://<bucket>.s3.<region>.amazonaws.com/}
     *             or {@code <endpoint>/<bucket>/}
     */
    S3BlobStore(HttpClient http, URI root, String bucket, String region, Supplier<AwsCredentials> credentials,
            Clock clock, Duration backoff) {
        this.http = http;
        this.root = root;
        this.bucket = bucket;
        this.region = region;
        this.credentials = credentials;
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public Optional<byte[]> get(String key) {
        HttpResponse<byte[]> response = call("GET", object(key), Map.of(), NONE, "get " + key);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(response.body());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call("PUT", object(key), Map.of("Content-Type", contentType), content, "put " + key);
    }

    @Override
    public void delete(String key) {
        call("DELETE", object(key), Map.of(), NONE, "delete " + key);
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            StringBuilder query = new StringBuilder("list-type=2&prefix=").append(encode(prefix, false));
            if (token != null) {
                query.append("&continuation-token=").append(encode(token, false));
            }
            HttpResponse<byte[]> response = call("GET", URI.create(root + "?" + query), Map.of(), NONE, "list " + prefix);
            Document page = parse(response.body());
            NodeList contents = page.getDocumentElement().getElementsByTagName("Contents");
            for (int i = 0; i < contents.getLength(); i++) {
                keys.add(text((Element) contents.item(i), "Key"));
            }
            token = "true".equals(text(page.getDocumentElement(), "IsTruncated"))
                    ? text(page.getDocumentElement(), "NextContinuationToken") : null;
        } while (token != null && !token.isEmpty());
        return keys;
    }

    private URI object(String key) {
        return URI.create(root + encode(key, true));
    }

    /**
     * One request, retried on throttling, server errors and I/O errors. Returns the response of a
     * success or, for GET, of a 404; anything else is an exception.
     */
    private HttpResponse<byte[]> call(String method, URI uri, Map<String, String> headers, byte[] body, String what) {
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, String> signed = SigV4Signer.signS3(method, uri, headers, body, region, credentials.get(),
                    clock.instant());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            Map<String, String> all = new LinkedHashMap<>(headers);
            all.putAll(signed);
            all.forEach((name, value) -> {
                if (!"Host".equals(name)) {
                    builder.header(name, value);
                }
            });
            try {
                HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response;
                }
                String code = errorCode(response.body());
                lastError = status + " " + code;
                if (status == 404 && "NoSuchBucket".equals(code)) {
                    throw new BlobStoreException("Bucket " + bucket + " does not exist");
                }
                if (status == 404 && "GET".equals(method) && uri.getRawQuery() == null) {
                    return response; // no such object
                }
                if (status == 403) {
                    throw new BlobAccessDeniedException("S3 refused " + what + " in bucket " + bucket + ": " + lastError);
                }
                if (status < 500 && status != 429 && !"SlowDown".equals(code)) {
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
        throw new BlobStoreException("S3 " + what + " in bucket " + bucket + " failed: " + lastError);
    }

    /** RFC 3986 encoding of everything but unreserved characters and, in keys, "/". */
    static String encode(String value, boolean keepSlash) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || (keepSlash && c == '/')) {
                out.append(c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
            }
        }
        return out.toString();
    }

    private static String errorCode(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            return text(parse(body).getDocumentElement(), "Code");
        } catch (BlobStoreException e) {
            return "";
        }
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new BlobStoreException("Cannot read the S3 response", e);
        }
    }

    /** The text of the first child element of that name, or "". */
    private static String text(Element parent, String name) {
        for (org.w3c.dom.Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getLocalName() != null
                    ? element.getLocalName() : element.getTagName())) {
                return element.getTextContent();
            }
        }
        return "";
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
