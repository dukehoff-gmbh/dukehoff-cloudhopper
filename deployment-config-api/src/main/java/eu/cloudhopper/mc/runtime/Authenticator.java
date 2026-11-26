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

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Checks the caller's token on routes with {@code @HttpTrigger(authenticated = true)}; the
 * generated handlers call it before the function. Configuration properties of the deployment:
 *
 * <ul>
 *   <li>{@code cloudhopper.auth.issuer} (required): the issuer of the OpenID Connect provider,
 *       exactly as in the tokens' {@code iss} claim, e.g.
 *       {@code https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_AbC123}</li>
 *   <li>{@code cloudhopper.auth.audience} (required): the expected {@code aud} claim (for access
 *       tokens of Amazon Cognito: the {@code client_id} of the app client)</li>
 *   <li>{@code cloudhopper.auth.jwks-uri}: the address of the provider's key set; without it, the
 *       {@code jwks_uri} of {@code <issuer>/.well-known/openid-configuration}</li>
 *   <li>{@code cloudhopper.auth.jwks}: the key set itself as JSON (or Base64-encoded JSON), for
 *       providers the functions cannot reach; then nothing is fetched</li>
 * </ul>
 *
 * <p>No token: {@code 401} with {@code WWW-Authenticate: Bearer}; an invalid or expired token:
 * {@code 401}; a valid token without a required scope: {@code 403}. The reason goes to the log,
 * not to the client.</p>
 */
public final class Authenticator {

    /** The issuer of the tokens. */
    public static final String ISSUER = "cloudhopper.auth.issuer";
    /** The audience of the tokens. */
    public static final String AUDIENCE = "cloudhopper.auth.audience";
    /** The address of the key set. */
    public static final String JWKS_URI = "cloudhopper.auth.jwks-uri";
    /** The key set as JSON. */
    public static final String JWKS = "cloudhopper.auth.jwks";

    private static final Logger LOG = Logger.getLogger(Authenticator.class.getName());

    private static volatile Cached cached;

    private record Cached(List<String> settings, JwtVerifier verifier) {
    }

    private Authenticator() {
    }

    /**
     * Verifies the bearer token of a request.
     *
     * @param headers the request headers, keyed case-insensitively
     * @param scopes  the scopes the token must have
     * @return the claims of the token
     * @throws HttpStatusException 401 without a valid token, 403 without a required scope
     * @throws IllegalStateException if the issuer or audience is not configured, or the keys
     *         cannot be fetched
     */
    public static Claims authenticate(Map<String, String> headers, String... scopes) {
        String authorization = headers == null ? null : headers.get("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new HttpStatusException(401, "Authentication required").header("WWW-Authenticate", "Bearer");
        }
        String value = authorization.trim();
        if (value.length() < 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw invalid("not a bearer token");
        }
        Claims claims;
        try {
            claims = verifier().verify(value.substring(7).trim());
        } catch (JwtVerifier.InvalidTokenException e) {
            throw invalid(e.getMessage());
        }
        List<String> granted = claims.getScopes();
        for (String scope : scopes) {
            if (!granted.contains(scope)) {
                LOG.log(Level.INFO, "Token of " + JwtVerifier.printable(claims.getSubject()) + " refused: scope "
                        + scope + " missing");
                throw new HttpStatusException(403, "Insufficient scope").header("WWW-Authenticate",
                        "Bearer error=\"insufficient_scope\", scope=\"" + String.join(" ", scopes) + "\"");
            }
        }
        return claims;
    }

    private static HttpStatusException invalid(String reason) {
        LOG.log(Level.INFO, "Token refused: " + reason);
        return new HttpStatusException(401, "Invalid token")
                .header("WWW-Authenticate", "Bearer error=\"invalid_token\"");
    }

    private static JwtVerifier verifier() {
        Config config = ConfigProvider.getConfig();
        String issuer = required(config, ISSUER);
        String audience = required(config, AUDIENCE);
        String jwks = config.getOptionalValue(JWKS, String.class).filter(v -> !v.isBlank()).orElse(null);
        String jwksUri = config.getOptionalValue(JWKS_URI, String.class).filter(v -> !v.isBlank()).orElse(null);
        List<String> settings = java.util.Arrays.asList(issuer, audience, jwks, jwksUri);
        Cached current = cached;
        if (current == null || !current.settings().equals(settings)) {
            JwtVerifier.KeySource keys = jwks != null
                    ? JwtVerifier.fixed(jwks)
                    : new JwtVerifier.RemoteKeys(issuer, jwksUri, Clock.systemUTC());
            current = new Cached(settings, new JwtVerifier(issuer, audience, keys, Clock.systemUTC()));
            cached = current;
        }
        return current.verifier();
    }

    private static String required(Config config, String name) {
        return config.getOptionalValue(name, String.class).filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalStateException("A route requires authentication, but " + name
                        + " is not configured (environment variable "
                        + name.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT) + ")"));
    }

    static void reset() {
        cached = null;
    }
}
