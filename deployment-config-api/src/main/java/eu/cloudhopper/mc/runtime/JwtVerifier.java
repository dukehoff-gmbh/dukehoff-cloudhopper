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

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifies signed JWTs (JWS compact serialization) of one OpenID Connect issuer with the JDK's
 * cryptography: RS256, RS384, RS512, ES256, ES384 and ES512, keys from a JSON Web Key Set.
 * Unsigned tokens ({@code alg: none}), symmetric algorithms and tokens with critical header
 * parameters are refused. The claims {@code iss}, {@code aud} (or {@code client_id}, as in Amazon
 * Cognito access tokens), {@code exp} and {@code nbf} are checked with a clock skew of a minute.
 */
final class JwtVerifier {

    private static final Logger LOG = Logger.getLogger(JwtVerifier.class.getName());

    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
    private static final int MIN_RSA_BITS = 2048;

    /** Thrown for a token that is not valid; the message is for the log, not for the client. */
    static final class InvalidTokenException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidTokenException(String message) {
            super(message);
        }
    }

    /** A source of verification keys. */
    interface KeySource {
        /**
         * @param refresh {@code true} to look for keys added since the last call (unknown key ID)
         * @return the keys
         */
        List<Jwk> keys(boolean refresh);
    }

    /** A public key of a key set. */
    record Jwk(String kid, String alg, PublicKey key) {
    }

    private enum Algorithm {
        RS256("SHA256withRSA", null), RS384("SHA384withRSA", null), RS512("SHA512withRSA", null),
        ES256("SHA256withECDSAinP1363Format", "secp256r1"), ES384("SHA384withECDSAinP1363Format", "secp384r1"),
        ES512("SHA512withECDSAinP1363Format", "secp521r1");

        final String jca;
        final String curve;

        Algorithm(String jca, String curve) {
            this.jca = jca;
            this.curve = curve;
        }

        boolean fits(PublicKey key) {
            if (curve == null) {
                return key instanceof RSAPublicKey;
            }
            return key instanceof ECPublicKey ec && curve.equals(curveOf(ec));
        }
    }

    private final String issuer;
    private final String audience;
    private final KeySource keys;
    private final Clock clock;

    JwtVerifier(String issuer, String audience, KeySource keys, Clock clock) {
        this.issuer = Objects.requireNonNull(issuer);
        this.audience = Objects.requireNonNull(audience);
        this.keys = Objects.requireNonNull(keys);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param token the token, without the {@code Bearer} prefix
     * @return the claims of the token
     * @throws InvalidTokenException if the token is not valid
     */
    Claims verify(String token) throws InvalidTokenException {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new InvalidTokenException("not a signed JWT");
        }
        Map<String, Object> header = json(parts[0], "header");
        if (header.containsKey("crit")) {
            throw new InvalidTokenException("critical header parameters are not supported");
        }
        Algorithm algorithm;
        try {
            algorithm = Algorithm.valueOf(String.valueOf(header.get("alg")));
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("algorithm not allowed: " + printable(header.get("alg")));
        }
        String kid = header.get("kid") instanceof String s ? s : null;
        byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature = decode(parts[2], "signature");

        List<Jwk> candidates = candidates(keys.keys(false), algorithm, kid);
        if (candidates.isEmpty()) {
            candidates = candidates(keys.keys(true), algorithm, kid);
        }
        if (candidates.isEmpty()) {
            throw new InvalidTokenException("no key for kid " + printable(kid) + " and " + algorithm);
        }
        boolean verified = false;
        for (Jwk jwk : candidates) {
            if (verifies(algorithm, jwk.key(), signed, signature)) {
                verified = true;
                break;
            }
        }
        if (!verified) {
            throw new InvalidTokenException("signature does not match");
        }

        Map<String, Object> claims = json(parts[1], "payload");
        if (!issuer.equals(claims.get("iss"))) {
            throw new InvalidTokenException("issuer " + printable(claims.get("iss")) + " is not " + issuer);
        }
        if (!audienceMatches(claims)) {
            throw new InvalidTokenException("audience does not match");
        }
        Instant now = clock.instant();
        Instant expires = time(claims, "exp");
        if (expires == null) {
            throw new InvalidTokenException("no exp claim");
        }
        // compared as now - skew < exp and now + skew >= nbf, so that no time is shifted past its range
        if (!now.minus(CLOCK_SKEW).isBefore(expires)) {
            throw new InvalidTokenException("expired");
        }
        Instant notBefore = time(claims, "nbf");
        if (notBefore != null && now.plus(CLOCK_SKEW).isBefore(notBefore)) {
            throw new InvalidTokenException("not valid yet");
        }
        return new Claims(claims);
    }

    private boolean audienceMatches(Map<String, Object> claims) {
        Object aud = claims.get("aud");
        if (aud instanceof String s) {
            return audience.equals(s);
        }
        if (aud instanceof List<?> list) {
            return list.contains(audience);
        }
        // access tokens of Amazon Cognito carry the app client in client_id instead
        return aud == null && audience.equals(claims.get("client_id"));
    }

    private static Instant time(Map<String, Object> claims, String name) throws InvalidTokenException {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Long seconds) {
                return Instant.ofEpochSecond(seconds);
            }
            if (value instanceof Double seconds && Double.isFinite(seconds)) {
                return Instant.ofEpochMilli((long) (seconds * 1000));
            }
        } catch (java.time.DateTimeException | ArithmeticException e) {
            throw new InvalidTokenException(name + " is out of range");
        }
        throw new InvalidTokenException(name + " is not a number");
    }

    /** A value from an unverified token, safe for a log line. */
    static String printable(Object value) {
        String text = String.valueOf(value);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length() && result.length() < 64; i++) {
            char c = text.charAt(i);
            result.append(c < 0x20 || c == 0x7f ? '?' : c);
        }
        return text.length() > 64 ? result + "..." : result.toString();
    }

    private static List<Jwk> candidates(List<Jwk> keys, Algorithm algorithm, String kid) {
        List<Jwk> result = new ArrayList<>();
        for (Jwk jwk : keys) {
            // a key without an ID fits any token; some providers publish none
            if (kid != null && jwk.kid() != null && !kid.equals(jwk.kid())) {
                continue;
            }
            if (jwk.alg() != null && !jwk.alg().equals(algorithm.name())) {
                continue;
            }
            if (algorithm.fits(jwk.key())) {
                result.add(jwk);
            }
        }
        return result;
    }

    private static boolean verifies(Algorithm algorithm, PublicKey key, byte[] signed, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(algorithm.jca);
            verifier.initVerify(key);
            verifier.update(signed);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static Map<String, Object> json(String part, String what) throws InvalidTokenException {
        try {
            return JsonReader.readObject(new String(decode(part, what), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("invalid " + what);
        }
    }

    private static byte[] decode(String part, String what) throws InvalidTokenException {
        // JWS uses base64url without padding; one token has exactly one encoding
        if (part.indexOf('=') >= 0) {
            throw new InvalidTokenException("invalid " + what);
        }
        try {
            return Base64.getUrlDecoder().decode(part);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("invalid " + what);
        }
    }

    // ---------------------------------------------------------------- key sets

    /**
     * Reads the keys of a JSON Web Key Set; keys for encryption, of other types and RSA keys
     * shorter than 2048 bits are skipped.
     *
     * @param jwks the key set as JSON
     * @return the keys
     * @throws IllegalArgumentException if the text is not a key set
     */
    static List<Jwk> parseKeySet(String jwks) {
        Object keys = JsonReader.readObject(jwks).get("keys");
        if (!(keys instanceof List<?> list)) {
            throw new IllegalArgumentException("Not a JSON Web Key Set: no \"keys\"");
        }
        List<Jwk> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> key)) {
                continue;
            }
            if (key.get("use") != null && !"sig".equals(key.get("use"))) {
                continue;
            }
            try {
                PublicKey publicKey = publicKey(key);
                if (publicKey != null) {
                    result.add(new Jwk(text(key, "kid"), text(key, "alg"), publicKey));
                }
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Skipping key " + key.get("kid") + " of the key set: " + e.getMessage());
            }
        }
        return result;
    }

    private static PublicKey publicKey(Map<?, ?> key) throws GeneralSecurityException {
        String kty = text(key, "kty");
        if ("RSA".equals(kty)) {
            BigInteger modulus = unsigned(text(key, "n"));
            if (modulus.bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("RSA key shorter than " + MIN_RSA_BITS + " bits");
            }
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, unsigned(text(key, "e"))));
        }
        if ("EC".equals(kty)) {
            String curve = switch (String.valueOf(text(key, "crv"))) {
                case "P-256" -> "secp256r1";
                case "P-384" -> "secp384r1";
                case "P-521" -> "secp521r1";
                default -> throw new IllegalArgumentException("unsupported curve " + text(key, "crv"));
            };
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(curve));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPoint point = new ECPoint(unsigned(text(key, "x")), unsigned(text(key, "y")));
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
        }
        return null;
    }

    private static String curveOf(ECPublicKey key) {
        int bits = key.getParams().getCurve().getField().getFieldSize();
        return switch (bits) {
            case 256 -> "secp256r1";
            case 384 -> "secp384r1";
            case 521 -> "secp521r1";
            default -> "unknown";
        };
    }

    private static String text(Map<?, ?> key, String name) {
        Object value = key.get(name);
        return value instanceof String s ? s : null;
    }

    private static BigInteger unsigned(String base64url) {
        if (base64url == null) {
            throw new IllegalArgumentException("missing key parameter");
        }
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64url));
    }

    /** Keys given in the configuration. */
    static KeySource fixed(String jwks) {
        List<Jwk> keys = List.copyOf(parseKeySet(jwks));
        return refresh -> keys;
    }

    /**
     * Keys fetched from the key set URI, or from the {@code jwks_uri} of the issuer's discovery
     * document; kept for an hour, fetched again at most every 30 seconds for an unknown key ID.
     */
    static final class RemoteKeys implements KeySource {

        private static final Duration MAX_AGE = Duration.ofHours(1);
        private static final Duration MIN_REFRESH = Duration.ofSeconds(30);
        private static final int MAX_DOCUMENT = 1 << 20;
        private static final HttpClient HTTP = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private final String issuer;
        private final Clock clock;
        private URI jwksUri;
        private List<Jwk> keys;
        private Instant fetched;
        private Instant attempted;

        RemoteKeys(String issuer, String jwksUri, Clock clock) {
            this.issuer = issuer;
            this.jwksUri = jwksUri == null ? null : URI.create(jwksUri);
            this.clock = clock;
            if (this.jwksUri != null && !"https".equals(this.jwksUri.getScheme()) && !isLocal(this.jwksUri)) {
                LOG.log(Level.WARNING, "The key set of " + issuer + " is fetched without HTTPS from " + jwksUri
                        + "; whoever can change it on the way can sign tokens");
            }
        }

        @Override
        public synchronized List<Jwk> keys(boolean refresh) {
            Instant now = clock.instant();
            boolean stale = keys == null || now.isAfter(fetched.plus(MAX_AGE));
            if (!stale && !refresh) {
                return keys;
            }
            // at most one attempt every 30 seconds, also after a failure: tokens with made-up key
            // IDs must not make every request wait for the provider
            if (attempted != null && now.isBefore(attempted.plus(MIN_REFRESH))) {
                if (keys == null) {
                    throw new IllegalStateException("The keys of " + issuer + " are not available yet");
                }
                return keys;
            }
            attempted = now;
            try {
                keys = List.copyOf(parseKeySet(get(jwksUri())));
                fetched = now;
            } catch (RuntimeException e) {
                if (keys == null) {
                    throw e;
                }
                LOG.log(Level.WARNING, "Cannot fetch the keys of " + issuer + ", keeping the known ones: " + e.getMessage());
            }
            return keys;
        }

        private URI jwksUri() {
            if (jwksUri == null) {
                String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
                URI discovery = URI.create(base + "/.well-known/openid-configuration");
                requireHttps(discovery);
                Map<String, Object> document = JsonReader.readObject(get(discovery));
                if (!issuer.equals(document.get("issuer"))) {
                    throw new IllegalStateException("The discovery document of " + issuer + " names the issuer "
                            + document.get("issuer"));
                }
                if (!(document.get("jwks_uri") instanceof String uri)) {
                    throw new IllegalStateException("The discovery document of " + issuer + " has no jwks_uri");
                }
                URI keysUri = URI.create(uri);
                requireHttps(keysUri);
                jwksUri = keysUri;
            }
            return jwksUri;
        }

        private static boolean isLocal(URI uri) {
            String host = uri.getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        }

        private static void requireHttps(URI uri) {
            if (!"https".equals(uri.getScheme()) && !isLocal(uri)) {
                throw new IllegalStateException("The issuer's documents must be fetched over HTTPS: " + uri
                        + "; set cloudhopper.auth.jwks-uri to use another address");
            }
        }

        private static String get(URI uri) {
            try {
                java.net.http.HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET().build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new IllegalStateException(uri + " answered " + response.statusCode());
                }
                if (response.body().length() > MAX_DOCUMENT) {
                    throw new IllegalStateException(uri + " answered more than " + MAX_DOCUMENT + " characters");
                }
                return response.body();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot fetch " + uri + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching " + uri, e);
            }
        }
    }
}
