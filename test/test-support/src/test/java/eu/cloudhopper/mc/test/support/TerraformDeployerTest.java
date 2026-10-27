package eu.cloudhopper.mc.test.support;

/*-
 * #%L
 * test-support - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.test.support.TerraformDeployer;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;

/**
 * Manual test for verifying the TerraformDeployer lifecycle.
 *
 * This test is disabled by default. It requires a real Terraform project
 * (e.g., in target/deployment/aws) with proper init/apply/destroy structure.
 */
public class TerraformDeployerTest {

    @Test
    @Disabled("Run manually with a real Terraform directory and credentials")
    public void testTerraformLifecycle() throws Exception {
        Path source = Path.of(
                TerraformDeployerTest.class.getClassLoader().getResource("main.tf").toURI()
        );

        Path tempDir = Files.createTempDirectory("tck-tf");
        Files.copy(source, tempDir.resolve("main.tf"));

        TerraformDeployer deployer = new TerraformDeployer(tempDir);

        deployer.init();
        deployer.apply();
        deployer.destroy();

    }
}
