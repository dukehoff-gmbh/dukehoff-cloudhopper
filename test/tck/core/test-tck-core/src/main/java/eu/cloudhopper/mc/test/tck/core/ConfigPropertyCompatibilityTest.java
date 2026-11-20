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
import eu.cloudhopper.mc.test.tck.api.TckConfiguration;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.util.List;
import org.junit.Assert;

/**
 * {@code @ConfigProperty}: the function reads the value the deployment sets
 * ({@link TckConfiguration}), the default of a property without a value, and an empty
 * {@code Optional} for a property nobody sets. Every target passes configuration as environment
 * variables, so the test runs on all of them.
 */
public class ConfigPropertyCompatibilityTest implements FeatureAwareTest {

    private static final String FUNCTION_NAME = "config";

    @Override
    public void run(TestContext context) throws Exception {
        Object result = context.invokeFunctionDirect(FUNCTION_NAME, null);
        System.out.println("⚙️ Configuration read by " + FUNCTION_NAME + ": " + result);
        Assert.assertEquals(TckConfiguration.GREETING + "|3|none", result);
    }

    @Override
    public List<RequiredFeature> requiredFeatures() {
        return List.of(new RequiredFeature(Function.class.getName(), List.of(Function.FunctionAttribute.NAME)));
    }
}
