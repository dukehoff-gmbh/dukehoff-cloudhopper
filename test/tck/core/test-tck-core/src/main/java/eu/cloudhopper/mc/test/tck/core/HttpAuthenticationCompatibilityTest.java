package eu.cloudhopper.mc.test.tck.core;

/*-
 * #%L
 * test-tck-core - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.HttpClientHelper;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TckIssuer;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Assert;

/**
 * A route with {@code @HttpTrigger(authenticated = true, scopes = "tck:read")}: tokens of the
 * TCK's issuer ({@link TckIssuer}), whose key set the deployment gets as configuration or, on
 * targets whose gateway checks the tokens, reads from the issuer
 * ({@link TestContext#getAuthIssuer()}).
 */
public class HttpAuthenticationCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "whoAmI";

    @Override
    public void run(TestContext context) throws Exception {
        URI url = new URI(context.getHttpUrl(FUNCTION_NAME));
        String issuer = context.getAuthIssuer();
        Instant valid5m = Instant.now().plusSeconds(300);
        Map<String, String> claims = Map.of("sub", "\"user-42\"", "level", "3", "custom:tenant", "\"acme\"",
                "scope", "\"profile tck:read\"");

        HttpClientHelper.HttpResponse missing = call(url, null, "no token");
        Assert.assertEquals(401, missing.getStatusCode());
        Assert.assertTrue("WWW-Authenticate: " + missing.getHeader("WWW-Authenticate"),
                String.valueOf(missing.getHeader("WWW-Authenticate")).startsWith("Bearer"));

        Assert.assertEquals(401, call(url, "Bearer not.a.token", "garbage").getStatusCode());
        Assert.assertEquals(401, call(url, "Bearer " + TckIssuer.token(issuer, TckIssuer.AUDIENCE,
                Instant.now().minusSeconds(600), claims), "expired").getStatusCode());
        Assert.assertEquals(401, call(url, "Bearer " + TckIssuer.token(issuer, "another-app",
                valid5m, claims), "other audience").getStatusCode());
        Assert.assertEquals(401, call(url, "Bearer " + TckIssuer.token("https://other-issuer.cloudhopper.invalid",
                TckIssuer.AUDIENCE, valid5m, claims), "other issuer").getStatusCode());
        String valid = TckIssuer.token(issuer, TckIssuer.AUDIENCE, valid5m, claims);
        String[] parts = valid.split("\\.");
        Assert.assertEquals(401, call(url, "Bearer " + parts[0] + "." + parts[1] + "." + parts[2].substring(0, 20) + "x",
                "bad signature").getStatusCode());

        HttpClientHelper.HttpResponse scope = call(url, "Bearer " + TckIssuer.token(issuer, TckIssuer.AUDIENCE, valid5m,
                Map.of("sub", "\"user-42\"", "scope", "\"profile\"")), "without scope");
        Assert.assertEquals(403, scope.getStatusCode());

        HttpClientHelper.HttpResponse ok = call(url, "Bearer " + valid, "valid");
        Assert.assertEquals(200, ok.getStatusCode());
        Assert.assertEquals("\"user-42:3:acme\"", ok.getBody().trim());
    }

    private static HttpClientHelper.HttpResponse call(URI url, String authorization, String what) throws Exception {
        HttpClientHelper.HttpResponse response = HttpClientHelper.get(url,
                authorization == null ? Map.of() : Map.of("Authorization", authorization));
        System.out.println("🔑 " + what + ": " + response.getStatusCode() + " " + response.getBody());
        return response;
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(HttpTrigger.class.getName(),
                        List.of(HttpTrigger.HttpTriggerAttribute.METHOD, HttpTrigger.HttpTriggerAttribute.PATH,
                                HttpTrigger.HttpTriggerAttribute.AUTHENTICATED, HttpTrigger.HttpTriggerAttribute.SCOPES)));
    }
}
