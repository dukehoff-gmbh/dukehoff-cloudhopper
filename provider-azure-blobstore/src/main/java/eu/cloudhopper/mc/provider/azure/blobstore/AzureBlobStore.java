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

import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A blob container of a storage account, through the Blob service REST API signed with the
 * account's SharedKey. Throttling, server errors and I/O errors are retried with backoff;
 * HTTP 403 becomes a {@link BlobAccessDeniedException}.
 */
class AzureBlobStore implements BlobStore {

    static final String VERSION = "2021-08-06";
    private static final int ATTEMPTS = 4;
    private static final byte[] NONE = new byte[0];
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final HttpClient http;
    private final StorageAccount account;
    private final String endpoint;
    private final String container;
    private final Clock clock;
    private final Duration backoff;

    /**
     * @param endpoint the Blob service endpoint, e.g. {@code https://acct.blob.core.windows.net}
     */
    AzureBlobStore(HttpClient http, StorageAccount account, URI endpoint, String container, Clock clock, Duration backoff) {
        this.http = http;
        this.account = account;
        this.endpoint = endpoint.toString().replaceAll("/$", "");
        this.container = container;
        this.clock = clock;
        this.backoff = backoff;
    }

    @Override
    public Optional<byte[]> get(String key) {
        HttpResponse<byte[]> response = call("GET", blob(key), Map.of(), Map.of(), "", NONE, "get " + key);
        return response.statusCode() == 404 ? Optional.empty() : Optional.of(response.body());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call("PUT", blob(key), Map.of(), Map.of("x-ms-blob-type", "BlockBlob"), contentType, content, "put " + key);
    }

    @Override
    public void delete(String key) {
        call("DELETE", blob(key), Map.of(), Map.of(), "", NONE, "delete " + key);
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        String marker = null;
        do {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("restype", "container");
            query.put("comp", "list");
            if (!prefix.isEmpty()) {
                query.put("prefix", prefix);
            }
            if (marker != null) {
                query.put("marker", marker);
            }
            HttpResponse<byte[]> response = call("GET", "/" + container, query, Map.of(), "", NONE, "list " + prefix);
            Element root = parse(response.body()).getDocumentElement();
            NodeList blobs = root.getElementsByTagName("Blob");
            for (int i = 0; i < blobs.getLength(); i++) {
                keys.add(text((Element) blobs.item(i), "Name"));
            }
            marker = text(root, "NextMarker");
        } while (!marker.isEmpty());
        return keys;
    }

    /** The encoded path of a blob: "/" stays, the rest of the name is encoded. */
    private String blob(String key) {
        return "/" + container + "/" + encode(key, true);
    }

    /**
     * One request, retried on throttling, server errors and I/O errors. Returns the response of a
     * success or of a missing blob (GET and DELETE); anything else is an exception.
     */
    private HttpResponse<byte[]> call(String method, String path, Map<String, String> query, Map<String, String> headers,
            String contentType, byte[] body, String what) {
        StringBuilder uri = new StringBuilder(endpoint).append(path);
        String separator = "?";
        for (Map.Entry<String, String> parameter : query.entrySet()) {
            uri.append(separator).append(parameter.getKey()).append('=').append(encode(parameter.getValue(), false));
            separator = "&";
        }
        String lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Map<String, String> msHeaders = new LinkedHashMap<>(headers);
            msHeaders.put("x-ms-date", RFC_1123.format(clock.instant()));
            msHeaders.put("x-ms-version", VERSION);
            String authorization = account.sharedKey(method, path, query, body.length, contentType, msHeaders);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri.toString())).timeout(Duration.ofSeconds(30))
                    .header("Authorization", authorization)
                    .method(method, body.length == 0 && !"PUT".equals(method) ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            msHeaders.forEach(builder::header);
            if (!contentType.isEmpty()) {
                builder.header("Content-Type", contentType);
            }
            try {
                HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response;
                }
                String code = response.headers().firstValue("x-ms-error-code").orElse("");
                lastError = status + " " + code;
                if (status == 404 && "BlobNotFound".equals(code)) {
                    return response;
                }
                if (status == 404 && "ContainerNotFound".equals(code)) {
                    throw new BlobStoreException("Container " + container + " does not exist in storage account "
                            + account.name());
                }
                if (status == 403) {
                    throw new BlobAccessDeniedException("Blob Storage refused " + what + " in container " + container
                            + ": " + lastError);
                }
                if (status < 500 && status != 408 && status != 429) {
                    break;
                }
            } catch (IOException e) {
                lastError = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BlobStoreException("Interrupted during " + what + " in container " + container, e);
            }
            if (attempt < ATTEMPTS) {
                pause(attempt, what);
            }
        }
        throw new BlobStoreException("Blob Storage " + what + " in container " + container + " of storage account "
                + account.name() + " failed: " + lastError);
    }

    /** RFC 3986 encoding of everything but unreserved characters and, in blob names, "/". */
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

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new BlobStoreException("Cannot read the Blob Storage response", e);
        }
    }

    /** The text of the first child element of that name, or "". */
    private static String text(Element parent, String name) {
        for (org.w3c.dom.Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
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
            throw new BlobStoreException("Interrupted during " + what + " in container " + container, e);
        }
    }
}
