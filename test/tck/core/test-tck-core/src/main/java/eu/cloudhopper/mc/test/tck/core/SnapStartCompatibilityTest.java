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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.annotations.ExtensionKeys;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.test.tck.api.CapabilityAwareTest;
import eu.cloudhopper.mc.test.tck.api.ExtensionAwareTest;
import eu.cloudhopper.mc.test.tck.api.HttpClientHelper;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TckConfiguration;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.net.URI;
import java.util.List;
import org.junit.Assert;

/**
 * {@code x-aws-snapstart}: right after the deployment, the version the triggers invoke is active
 * and runs with SnapStart, and the function answers the first call through its HTTP trigger,
 * which invokes it through the alias. Where the target supports secrets, the function reads one,
 * with the credentials a SnapStart function gets.
 */
public class SnapStartCompatibilityTest implements ExtensionAwareTest {

    private static final String FUNCTION_NAME = "snapstart";

    @Override
    public void run(TestContext context) throws Exception {
        // the deployment waits until the version is active before the alias points to it
        Assert.assertTrue("version behind the alias active, with SnapStart", context.isSnapStartActive(FUNCTION_NAME));
        String url = context.getHttpUrl(FUNCTION_NAME);
        System.out.println("📞 Calling: " + url);
        HttpClientHelper.HttpResponse response = HttpClientHelper.get(URI.create(url));
        System.out.println("📩 Raw Response (" + response.getStatusCode() + "): " + response.getBody());
        if (response.getStatusCode() != 200) {
            // no invocation in the logs: the trigger could not invoke the alias; otherwise the function failed
            System.out.println("📜 Logs of " + FUNCTION_NAME + ":");
            context.fetchLogs(FUNCTION_NAME).forEach(line -> System.out.println("  " + line));
        }
        String expected = "snap:" + (context.supportedCapabilities().contains(CapabilityAwareTest.SECRETS)
                ? TckConfiguration.SECRET : "none");
        Assert.assertEquals(expected, new ObjectMapper().readValue(response.getBody(), String.class));
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)),
                new RequiredFeature(HttpTrigger.class.getName(),
                        List.of(HttpTrigger.HttpTriggerAttribute.METHOD,
                                HttpTrigger.HttpTriggerAttribute.OPERATION_ID,
                                HttpTrigger.HttpTriggerAttribute.PATH)));
    }

    @Override
    public List<String> requiredExtensions() {
        return List.of(ExtensionKeys.Aws.SnapStart.KEY);
    }
}
