package eu.cloudhopper.mc.test.system.tests.generator.springboot;

/*-
 * #%L
 * system-tests-generator-aws-terraform - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.generator.bindings.springboot.http.TestContextSpringBoot;
import eu.cloudhopper.mc.test.tck.core.CompatibilityTestRunner;

/**
 * Runs the TCK against the springboot-http target: {@code mvn package}, then
 * {@code mvn exec:java} in this module. Needs no cloud account.
 */
public class TckLauncher {
    public static void main(String[] args) throws Exception {
        CompatibilityTestRunner.runWith("springboot-http", new TestContextSpringBoot());
    }
}
