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
import eu.cloudhopper.mc.test.tck.api.FeatureAwareTest;
import eu.cloudhopper.mc.test.tck.api.RequiredFeature;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.List;
import org.junit.Assert;

public class DirectCallToAPIFunctionCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "ping";

    @Override
    public void run(TestContext context) throws Exception {
        System.out.println("📞 Directly invoking: " + FUNCTION_NAME);
        Object result = context.invokeFunctionDirect(FUNCTION_NAME, null);
        System.out.println("🏓 Raw result: " + result);
        Assert.assertEquals("pong", result);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(
                new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)));

    }
}
