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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The identity provider of the TCK: an RSA key created for each run, whose public key set the
 * deployment gets as the property {@code cloudhopper.auth.jwks}, so that no provider has to be
 * reachable. The TCK signs the tokens of the authentication test with it.
 */
public final class TckIssuer {

    /** The issuer of the TCK's tokens. */
    public static final String ISSUER = "https://tck.cloudhopper.invalid";
    /** The audience of the TCK's tokens. */
    public static final String AUDIENCE = "cloudhopper-tck";
    /** The key ID of the run's key. */
    public static final String KID = "tck-run";

    private static final KeyPair KEYS = generate();

    private TckIssuer() {
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create the TCK's key", e);
        }
    }

    /**
     * @return the public key set as JSON
     */
    public static String jwks() {
        RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + KID + "\",\"n\":\""
                + unsigned(key.getModulus()) + "\",\"e\":\"" + unsigned(key.getPublicExponent()) + "\"}]}";
    }

    /**
     * A token of the issuer, valid for five minutes.
     *
     * @param claims further claims, values as JSON (e.g. {@code "\"text\""} or {@code "42"})
     * @return the signed token
     */
    public static String token(Map<String, String> claims) {
        return token(AUDIENCE, Instant.now().plusSeconds(300), claims);
    }

    /**
     * @param audience the audience
     * @param expires  the expiry
     * @param claims   further claims, values as JSON
     * @return the signed token
     */
    public static String token(String audience, Instant expires, Map<String, String> claims) {
        String payload = "{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + audience + "\",\"exp\":" + expires.getEpochSecond()
                + claims.entrySet().stream().map(e -> ",\"" + e.getKey() + "\":" + e.getValue()).collect(Collectors.joining())
                + "}";
        return sign("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}", payload, KEYS.getPrivate());
    }

    private static String sign(String header, String payload, PrivateKey key) {
        try {
            String signed = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + b64(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign a token", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return b64(bytes);
    }
}
