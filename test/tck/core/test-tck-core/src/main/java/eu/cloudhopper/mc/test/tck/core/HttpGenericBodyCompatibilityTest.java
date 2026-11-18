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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Posts a JSON object to a function whose body is a {@code Map<String, List<String>>} and whose result
 * is a {@code Map<String, Object>}; the method also declares {@code throws IOException}. A body that
 * makes it throw must give a server error, not a success.
 */
public class HttpGenericBodyCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "submitForm";

    @Override
    public void run(TestContext context) throws Exception {
        URI url = new URI(context.getHttpUrl(FUNCTION_NAME));
        System.out.println("📞 Calling: " + url);

        HttpClientHelper.HttpResponse response = HttpClientHelper.post(url,
                "{\"name\":[\"Toni\"],\"tags\":[\"a\",\"b\"]}");
        System.out.println("📩 Raw Response: " + response.getBody());
        Assert.assertEquals(200, response.getStatusCode());
        JsonNode result = new ObjectMapper().readTree(response.getBody());
        Assert.assertEquals(2, result.get("fields").asInt());
        Assert.assertEquals("a", result.get("tags").get(0).asText());
        Assert.assertEquals("b", result.get("tags").get(1).asText());

        HttpClientHelper.HttpResponse failed = HttpClientHelper.post(url, "{\"fail\":[]}");
        System.out.println("📩 Response to a rejected form: " + failed.getStatusCode() + " " + failed.getBody());
        Assert.assertTrue("expected a server error, got " + failed.getStatusCode(), failed.getStatusCode() >= 500);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(HttpTrigger.class.getName(),
                        List.of(HttpTrigger.HttpTriggerAttribute.METHOD,
                                HttpTrigger.HttpTriggerAttribute.PATH)));
    }
}
