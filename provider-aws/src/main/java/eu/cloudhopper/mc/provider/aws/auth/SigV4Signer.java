package eu.cloudhopper.mc.provider.aws.auth;

/*-
 * #%L
 * provider-aws - a library from the "Cloudhopper" project.
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs HTTP requests with AWS Signature Version 4, for the AWS APIs the runtime calls itself
 * (e.g. SQS) without the AWS SDK. Follows the AWS SigV4 specification; the unit tests check it
 * against the official test suite.
 */
public final class SigV4Signer {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final HexFormat HEX = HexFormat.of();

    private SigV4Signer() {
    }

    /**
     * Computes the headers that sign a request.
     *
     * @param method      the HTTP method
     * @param uri         the request URI (path and query are signed)
     * @param headers     the headers that are sent and signed, without {@code host} and
     *                    {@code x-amz-date}, which are added
     * @param body        the request body, empty for none
     * @param service     the service signing name, e.g. {@code sqs}
     * @param region      the region, e.g. {@code eu-central-1}
     * @param credentials the credentials
     * @param now         the time of the request
     * @return the headers to add: {@code Host}, {@code X-Amz-Date}, {@code X-Amz-Security-Token}
     *         (temporary credentials) and {@code Authorization}
     */
    public static Map<String, String> sign(String method, URI uri, Map<String, String> headers, byte[] body,
            String service, String region, AwsCredentials credentials, Instant now) {
        return sign(method, uri, headers, body, service, region, credentials, now, false);
    }

    /**
     * Computes the headers that sign an S3 request. S3 signs the path as it is sent, without
     * encoding it a second time, and needs the header {@code x-amz-content-sha256}, which is added.
     *
     * @param method      the HTTP method
     * @param uri         the request URI, with the object key encoded per segment
     * @param headers     the headers that are sent and signed
     * @param body        the request body, empty for none
     * @param region      the region
     * @param credentials the credentials
     * @param now         the time of the request
     * @return the headers to add, as {@link #sign} returns them, and {@code X-Amz-Content-Sha256}
     */
    public static Map<String, String> signS3(String method, URI uri, Map<String, String> headers, byte[] body,
            String region, AwsCredentials credentials, Instant now) {
        Map<String, String> withHash = new LinkedHashMap<>(headers);
        String hash = HEX.formatHex(sha256(body));
        withHash.put("X-Amz-Content-Sha256", hash);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("X-Amz-Content-Sha256", hash);
        result.putAll(sign(method, uri, withHash, body, "s3", region, credentials, now, true));
        return result;
    }

    private static Map<String, String> sign(String method, URI uri, Map<String, String> headers, byte[] body,
            String service, String region, AwsCredentials credentials, Instant now, boolean s3) {
        String amzDate = AMZ_DATE.format(now);
        String date = amzDate.substring(0, 8);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("Host", host(uri));
        result.put("X-Amz-Date", amzDate);
        if (credentials.sessionToken() != null) {
            result.put("X-Amz-Security-Token", credentials.sessionToken());
        }

        TreeMap<String, String> canonical = new TreeMap<>();
        headers.forEach((name, value) -> canonical.put(name.toLowerCase(Locale.ROOT), trim(value)));
        result.forEach((name, value) -> canonical.put(name.toLowerCase(Locale.ROOT), trim(value)));
        String signedHeaders = String.join(";", canonical.keySet());
        StringBuilder canonicalHeaders = new StringBuilder();
        canonical.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));

        String canonicalRequest = method + "\n"
                + (s3 ? s3Path(uri) : canonicalPath(uri)) + "\n"
                + canonicalQuery(uri) + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + HEX.formatHex(sha256(body));
        String scope = date + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + HEX.formatHex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] key = hmac(("AWS4" + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8), date);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = HEX.formatHex(hmac(key, stringToSign));

        result.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + credentials.accessKeyId() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return result;
    }

    private static String host(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("https".equals(uri.getScheme()) && port == 443)
                || ("http".equals(uri.getScheme()) && port == 80);
        return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
    }

    /** Collapses runs of spaces and trims, as the canonical header value requires. */
    private static String trim(String value) {
        return value.trim().replaceAll(" +", " ");
    }

    /**
     * The canonical path: every segment of the raw (already encoded) path is encoded once more, as
     * SigV4 requires for all services but S3; "/" for none.
     */
    private static String canonicalPath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            segments.add(encode(segment));
        }
        return String.join("/", segments);
    }

    /** S3: the raw path as it is sent; "/" for none. */
    private static String s3Path(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private static String canonicalQuery(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            pairs.add(new String[]{encode(decode(name)), encode(decode(value))});
        }
        pairs.sort((a, b) -> a[0].equals(b[0]) ? a[1].compareTo(b[1]) : a[0].compareTo(b[0]));
        List<String> encoded = new ArrayList<>();
        pairs.forEach(p -> encoded.add(p[0] + "=" + p[1]));
        return String.join("&", encoded);
    }

    /** RFC 3986 encoding: everything but unreserved characters, as SigV4 requires. */
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

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
