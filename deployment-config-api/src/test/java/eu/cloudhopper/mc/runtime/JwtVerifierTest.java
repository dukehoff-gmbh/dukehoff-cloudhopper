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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

    private static final String ISSUER = "https://issuer.example";
    private static final String AUDIENCE = "app";
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static KeyPair rsa;
    private static KeyPair ec;
    private static String jwks;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        rsa = rsaGenerator.generateKeyPair();
        KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        ec = ecGenerator.generateKeyPair();
        jwks = "{\"keys\":[" + rsaJwk("r1", (RSAPublicKey) rsa.getPublic()) + "," + ecJwk("e1", (ECPublicKey) ec.getPublic()) + "]}";
    }

    @AfterEach
    void resetAuthenticator() {
        Authenticator.reset();
        System.clearProperty(Authenticator.ISSUER);
        System.clearProperty(Authenticator.AUDIENCE);
        System.clearProperty(Authenticator.JWKS);
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String b64(String text) {
        return b64(text.getBytes(StandardCharsets.UTF_8));
    }

    static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return b64(bytes);
    }

    static String rsaJwk(String kid, RSAPublicKey key) {
        return "{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"" + unsigned(key.getModulus())
                + "\",\"e\":\"" + unsigned(key.getPublicExponent()) + "\"}";
    }

    static String ecJwk(String kid, ECPublicKey key) {
        return "{\"kty\":\"EC\",\"kid\":\"" + kid + "\",\"crv\":\"P-256\",\"x\":\"" + unsigned(key.getW().getAffineX())
                + "\",\"y\":\"" + unsigned(key.getW().getAffineY()) + "\"}";
    }

    static String sign(String header, String payload, String jca, PrivateKey key) throws Exception {
        String signed = b64(header) + "." + b64(payload);
        Signature signature = Signature.getInstance(jca);
        signature.initSign(key);
        signature.update(signed.getBytes(StandardCharsets.US_ASCII));
        return signed + "." + b64(signature.sign());
    }

    static String rs256(String payload) throws Exception {
        return sign("{\"alg\":\"RS256\",\"kid\":\"r1\",\"typ\":\"JWT\"}", payload, "SHA256withRSA", rsa.getPrivate());
    }

    static String claims(String extra) {
        return "{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\",\"sub\":\"user-1\",\"exp\":"
                + NOW.plusSeconds(300).getEpochSecond() + extra + "}";
    }

    private static JwtVerifier verifier() {
        return new JwtVerifier(ISSUER, AUDIENCE, JwtVerifier.fixed(jwks), CLOCK);
    }

    private static void refused(String token) {
        assertThrows(JwtVerifier.InvalidTokenException.class, () -> verifier().verify(token));
    }

    @Test
    void validRs256Token() throws Exception {
        Claims claims = verifier().verify(rs256(claims(",\"email\":\"a@b.c\",\"n\":3,\"roles\":[\"x\",\"y\"]")));
        assertEquals("user-1", claims.getSubject());
        assertEquals("a@b.c", claims.getString("email"));
        assertEquals(3L, claims.getLong("n"));
        assertEquals(List.of("x", "y"), claims.getStringList("roles"));
    }

    @Test
    void validEs256Token() throws Exception {
        String token = sign("{\"alg\":\"ES256\",\"kid\":\"e1\"}", claims(""), "SHA256withECDSAinP1363Format", ec.getPrivate());
        assertEquals("user-1", verifier().verify(token).getSubject());
    }

    @Test
    void tokenWithoutKidIsCheckedAgainstTheKeysOfItsType() throws Exception {
        String token = sign("{\"alg\":\"RS256\"}", claims(""), "SHA256withRSA", rsa.getPrivate());
        assertEquals("user-1", verifier().verify(token).getSubject());
    }

    @Test
    void unsignedAndSymmetricTokensAreRefused() throws Exception {
        refused(b64("{\"alg\":\"none\"}") + "." + b64(claims("")) + ".");
        refused(b64("{\"alg\":\"none\"}") + "." + b64(claims("")) + ".c2ln");
        // HS256 with the public key as secret (algorithm confusion)
        String signed = b64("{\"alg\":\"HS256\",\"kid\":\"r1\"}") + "." + b64(claims(""));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(rsa.getPublic().getEncoded(), "HmacSHA256"));
        refused(signed + "." + b64(mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII))));
        // RS256 header, signed by the EC key
        refused(sign("{\"alg\":\"ES256\",\"kid\":\"r1\"}", claims(""), "SHA256withECDSAinP1363Format", ec.getPrivate()));
    }

    @Test
    void tamperedTokensAreRefused() throws Exception {
        String token = rs256(claims(""));
        String[] parts = token.split("\\.");
        refused(parts[0] + "." + b64(claims(",\"admin\":true")) + "." + parts[2]);
        refused(parts[0] + "." + parts[1]);
        refused(token + ".x");
        refused("garbage");
        refused(sign("{\"alg\":\"RS256\",\"kid\":\"other\"}", claims(""), "SHA256withRSA", rsa.getPrivate()));
        refused(sign("{\"alg\":\"RS256\",\"kid\":\"r1\",\"crit\":[\"b64\"]}", claims(""), "SHA256withRSA", rsa.getPrivate()));
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"iss\":\"https://evil\",\"aud\":\"app\",\"exp\":"
                + NOW.plusSeconds(60).getEpochSecond() + "}"));
    }

    @Test
    void issuerAudienceAndTimesAreChecked() throws Exception {
        long exp = NOW.plusSeconds(300).getEpochSecond();
        refused(rs256("{\"iss\":\"https://other\",\"aud\":\"app\",\"exp\":" + exp + "}"));
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"other\",\"exp\":" + exp + "}"));
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"app\"}"));
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"app\",\"exp\":" + NOW.minusSeconds(61).getEpochSecond() + "}"));
        refused(rs256(claims(",\"nbf\":" + NOW.plusSeconds(120).getEpochSecond())));
        verifier().verify(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"app\",\"exp\":" + NOW.minusSeconds(30).getEpochSecond() + "}"));
        verifier().verify(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":[\"x\",\"app\"],\"exp\":" + exp + "}"));
        // access tokens of Amazon Cognito: client_id instead of aud
        verifier().verify(rs256("{\"iss\":\"" + ISSUER + "\",\"client_id\":\"app\",\"exp\":" + exp + "}"));
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"other\",\"client_id\":\"app\",\"exp\":" + exp + "}"));
    }

    @Test
    void shortRsaKeysAreSkipped() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();
        String set = "{\"keys\":[" + rsaJwk("w", (RSAPublicKey) weak.getPublic()) + "]}";
        assertEquals(0, JwtVerifier.parseKeySet(set).size());
    }

    @Test
    void unknownKidRefreshesTheKeys() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        JwtVerifier.KeySource source = refresh -> {
            if (refresh) {
                refreshes.incrementAndGet();
                return JwtVerifier.parseKeySet(jwks);
            }
            return List.of();
        };
        new JwtVerifier(ISSUER, AUDIENCE, source, CLOCK).verify(rs256(claims("")));
        assertEquals(1, refreshes.get());
    }

    @Test
    void keysComeFromTheDiscoveryDocument() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicInteger fetches = new AtomicInteger();
        server.createContext("/.well-known/openid-configuration", exchange -> {
            byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/keys\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/keys", exchange -> {
            fetches.incrementAndGet();
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JwtVerifier verifier = new JwtVerifier(issuer, AUDIENCE, new JwtVerifier.RemoteKeys(issuer, null, CLOCK), CLOCK);
            String payload = "{\"iss\":\"" + issuer + "\",\"aud\":\"app\",\"sub\":\"u\",\"exp\":" + NOW.plusSeconds(60).getEpochSecond() + "}";
            assertEquals("u", verifier.verify(rs256(payload)).getSubject());
            assertEquals("u", verifier.verify(rs256(payload)).getSubject());
            assertEquals(1, fetches.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void outOfRangeTimesAndPaddingAreRefused() throws Exception {
        refused(rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"app\",\"exp\":9223372036854775807}"));
        refused(rs256(claims(",\"nbf\":-9223372036854775807")));
        String token = rs256(claims(""));
        refused(token + "==");
    }

    @Test
    void jsonIsReadStrictly() {
        assertThrows(IllegalArgumentException.class, () -> JsonReader.read("\"\\u+041\""));
        assertThrows(IllegalArgumentException.class, () -> JsonReader.read("0001"));
        assertThrows(IllegalArgumentException.class, () -> JsonReader.read("1."));
        assertThrows(IllegalArgumentException.class, () -> JsonReader.read("{\"a\":1,\"\\u0061\":2}"));
        assertThrows(IllegalArgumentException.class, () -> JsonReader.read("[".repeat(40) + "]".repeat(40)));
        assertEquals("A", JsonReader.read("\"\\u0041\""));
        assertEquals(-12L, JsonReader.read("-12"));
        assertEquals(1.5e3, JsonReader.read("1.5e3"));
    }

    @Test
    void failedFetchesAreRetriedAtMostEvery30Seconds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicInteger fetches = new AtomicInteger();
        server.createContext("/keys", exchange -> {
            fetches.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            JwtVerifier.RemoteKeys keys = new JwtVerifier.RemoteKeys(ISSUER, base + "/keys", CLOCK);
            for (int i = 0; i < 5; i++) {
                assertThrows(IllegalStateException.class, () -> keys.keys(true));
            }
            assertEquals(1, fetches.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keysWithoutIdFitTokensWithId() throws Exception {
        String set = "{\"keys\":[" + rsaJwk("r1", (RSAPublicKey) rsa.getPublic()).replace("\"kid\":\"r1\",", "") + "]}";
        new JwtVerifier(ISSUER, AUDIENCE, JwtVerifier.fixed(set), CLOCK).verify(rs256(claims("")));
    }

    @Test
    void logValuesArePrintable() {
        assertEquals("X?INFO", JwtVerifier.printable("X\nINFO"));
        assertEquals(67, JwtVerifier.printable("k".repeat(500)).length());
    }

    @Test
    void discoveryNeedsHttpsForOtherHosts() {
        JwtVerifier.RemoteKeys keys = new JwtVerifier.RemoteKeys("http://issuer.example", null, CLOCK);
        assertThrows(IllegalStateException.class, () -> keys.keys(false));
    }

    @Test
    void authenticatorAnswers401And403() throws Exception {
        System.setProperty(Authenticator.ISSUER, ISSUER);
        System.setProperty(Authenticator.AUDIENCE, AUDIENCE);
        System.setProperty(Authenticator.JWKS, jwks);

        HttpStatusException missing = assertThrows(HttpStatusException.class, () -> Authenticator.authenticate(Map.of()));
        assertEquals(401, missing.getStatus());
        assertEquals("Bearer", missing.getHeaders().get("www-authenticate"));
        assertEquals(401, assertThrows(HttpStatusException.class,
                () -> Authenticator.authenticate(Headers.of(Map.of("Authorization", "Basic dXNlcjpwdw==")))).getStatus());

        // the token is valid now, not at the fixed time of the other tests
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        String token = rs256("{\"iss\":\"" + ISSUER + "\",\"aud\":\"app\",\"sub\":\"u\",\"exp\":" + exp
                + ",\"scope\":\"items:read profile\",\"scp\":[\"extra\"]}");
        Map<String, String> headers = Headers.of(Map.of("authorization", "bearer " + token));
        assertEquals("u", Authenticator.authenticate(headers).getSubject());
        assertEquals("u", Authenticator.authenticate(headers, "items:read", "extra").getSubject());
        HttpStatusException scope = assertThrows(HttpStatusException.class,
                () -> Authenticator.authenticate(headers, "items:write"));
        assertEquals(403, scope.getStatus());
        assertTrue(scope.getHeaders().get("WWW-Authenticate").contains("insufficient_scope"));
        assertEquals("Invalid token", assertThrows(HttpStatusException.class,
                () -> Authenticator.authenticate(Headers.of(Map.of("Authorization", "Bearer " + token + "x")))).getMessage());
    }

    @Test
    void missingConfigurationFailsWith500() {
        assertThrows(IllegalStateException.class,
                () -> Authenticator.authenticate(Map.of("Authorization", "Bearer a.b.c")));
    }
}
