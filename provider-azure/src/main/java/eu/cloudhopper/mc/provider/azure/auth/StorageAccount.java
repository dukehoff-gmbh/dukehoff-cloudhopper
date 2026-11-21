package eu.cloudhopper.mc.provider.azure.auth;

/*-
 * #%L
 * provider-azure - a library from the "Cloudhopper" project.
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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A storage account from a connection string such as the function app's
 * {@code AzureWebJobsStorage}, and the SharedKey signature of requests to it.
 *
 * @param name           the account name
 * @param key            the account key (Base64)
 * @param protocol       {@code https} or {@code http}
 * @param endpointSuffix e.g. {@code core.windows.net}
 */
public record StorageAccount(String name, String key, String protocol, String endpointSuffix) {

    /**
     * @param connectionString {@code DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=...}
     * @return the account
     * @throws IllegalArgumentException if the account name or key is missing
     */
    public static StorageAccount parse(String connectionString) {
        Map<String, String> parts = new HashMap<>();
        for (String part : connectionString.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                parts.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        String name = parts.get("AccountName");
        String key = parts.get("AccountKey");
        if (name == null || key == null) {
            throw new IllegalArgumentException("The connection string has no AccountName or AccountKey");
        }
        return new StorageAccount(name, key, parts.getOrDefault("DefaultEndpointsProtocol", "https"),
                parts.getOrDefault("EndpointSuffix", "core.windows.net"));
    }

    /**
     * @param service {@code queue}, {@code blob}, …
     * @return the endpoint of a service, e.g. {@code https://acct.queue.core.windows.net}
     */
    public String endpoint(String service) {
        return protocol + "://" + name + "." + service + "." + endpointSuffix;
    }

    /**
     * The {@code Authorization} header of a request, signed with SharedKey (Blob, Queue and File
     * services, version 2009-09-19 and later).
     *
     * @param method        the HTTP method
     * @param path          the URL path, e.g. {@code /orders/messages}
     * @param query         the query parameters, may be empty
     * @param contentLength the body length; 0 signs an empty length
     * @param contentType   the content type, or an empty string
     * @param msHeaders     the {@code x-ms-*} headers that are sent
     * @return the header value {@code SharedKey <account>:<signature>}
     */
    public String sharedKey(String method, String path, Map<String, String> query, long contentLength,
            String contentType, Map<String, String> msHeaders) {
        StringBuilder canonicalHeaders = new StringBuilder();
        new TreeMap<>(lowercase(msHeaders)).forEach((n, v) -> canonicalHeaders.append(n).append(':').append(v.trim()).append('\n'));
        StringBuilder resource = new StringBuilder("/").append(name).append(path);
        new TreeMap<>(lowercase(query)).forEach((n, v) -> resource.append('\n').append(n).append(':').append(v));
        String stringToSign = method + "\n"
                + "\n" // Content-Encoding
                + "\n" // Content-Language
                + (contentLength == 0 ? "" : String.valueOf(contentLength)) + "\n"
                + "\n" // Content-MD5
                + contentType + "\n"
                + "\n" // Date: x-ms-date is used
                + "\n" // If-Modified-Since
                + "\n" // If-Match
                + "\n" // If-None-Match
                + "\n" // If-Unmodified-Since
                + "\n" // Range
                + canonicalHeaders
                + resource;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            return "SharedKey " + name + ":" + Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> lowercase(Map<String, String> map) {
        Map<String, String> result = new HashMap<>();
        map.forEach((n, v) -> result.put(n.toLowerCase(Locale.ROOT), v));
        return result;
    }

    @Override
    public String toString() {
        return "StorageAccount[" + name + "]";
    }
}
