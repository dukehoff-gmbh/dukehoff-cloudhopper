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
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.net.URI;
import java.util.List;
import org.junit.Assert;

/**
 * Statuses and headers of HTTP functions: {@code HttpStatusException}, {@code HttpResponse},
 * 500 without internals, and 400 for a request body that is not valid JSON.
 */
public class HttpStatusCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "httpStatus";

    @Override
    public void run(TestContext context) throws Exception {
        String url = context.getHttpUrl(FUNCTION_NAME);

        HttpClientHelper.HttpResponse ok = call(url, "hello");
        Assert.assertEquals(200, ok.getStatusCode());
        Assert.assertEquals("{\"case\":\"hello\"}", ok.getBody().replace(" ", "").trim());

        HttpClientHelper.HttpResponse notFound = call(url, "not-found");
        Assert.assertEquals(404, notFound.getStatusCode());
        Assert.assertEquals("{\"error\":\"no such item \\\"x\\\"\"}", notFound.getBody().trim());
        assertJson(notFound);

        HttpClientHelper.HttpResponse unauthorized = call(url, "unauthorized");
        Assert.assertEquals(401, unauthorized.getStatusCode());
        Assert.assertEquals("Bearer", unauthorized.getHeader("WWW-Authenticate"));

        HttpClientHelper.HttpResponse fail = call(url, "fail");
        Assert.assertEquals(500, fail.getStatusCode());
        Assert.assertFalse("500 must not reveal the exception: " + fail.getBody(),
                fail.getBody().contains("internal detail"));
        Assert.assertEquals("{\"error\":\"Internal Server Error\"}", fail.getBody().trim());

        HttpClientHelper.HttpResponse created = call(url, "created");
        Assert.assertEquals(201, created.getStatusCode());
        Assert.assertEquals("/items/x", created.getHeader("Location"));
        Assert.assertEquals("created", created.getHeader("X-Cloudhopper-Test"));
        Assert.assertEquals("{\"id\":\"x\"}", created.getBody().replace(" ", "").trim());

        HttpClientHelper.HttpResponse empty = call(url, "empty");
        Assert.assertEquals(204, empty.getStatusCode());
        Assert.assertEquals("", empty.getBody());

        HttpClientHelper.HttpResponse csv = call(url, "csv");
        Assert.assertEquals(200, csv.getStatusCode());
        Assert.assertTrue("Content-Type: " + csv.getHeader("Content-Type"),
                csv.getHeader("Content-Type").startsWith("text/csv"));
        Assert.assertEquals("a,b\n1,2\n", csv.getBody());

        // a body that is not valid JSON for the input type of another function
        String update = context.getHttpUrl("updatePlayer").replace("{id}", "1");
        HttpClientHelper.HttpResponse invalid = HttpClientHelper.put(new URI(update), "{not json");
        System.out.println("📩 invalid body: " + invalid.getStatusCode() + " " + invalid.getBody());
        Assert.assertEquals(400, invalid.getStatusCode());
        Assert.assertEquals("{\"error\":\"Invalid request body\"}", invalid.getBody().trim());
    }

    private static HttpClientHelper.HttpResponse call(String url, String which) throws Exception {
        URI uri = new URI(url.replace("{case}", which));
        HttpClientHelper.HttpResponse response = HttpClientHelper.get(uri);
        System.out.println("📩 " + which + ": " + response.getStatusCode() + " " + response.getHeaders()
                + " " + response.getBody());
        return response;
    }

    private static void assertJson(HttpClientHelper.HttpResponse response) {
        String type = response.getHeader("Content-Type");
        Assert.assertTrue("Content-Type: " + type, type != null && type.startsWith("application/json"));
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(HttpTrigger.class.getName(),
                        List.of(HttpTrigger.HttpTriggerAttribute.METHOD,
                                HttpTrigger.HttpTriggerAttribute.PATH)));
    }
}
