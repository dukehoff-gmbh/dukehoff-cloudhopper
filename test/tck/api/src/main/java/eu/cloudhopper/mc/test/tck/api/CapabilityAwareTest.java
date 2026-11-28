package eu.cloudhopper.mc.test.tck.api;

/*-
 * #%L
 * test-tck-api - a library from the "Cloudhopper" project.
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

import java.util.List;

/**
 * A test that needs capabilities of the deployment beyond the annotations, such as reading
 * secrets; it runs only on targets whose binding lists them in
 * {@link TestContext#supportedCapabilities()}.
 */
public interface CapabilityAwareTest extends FeatureAwareTest {

    /** Secrets referenced by the deployment (Terraform variable {@code cloudhopper_secrets} or the like). */
    String SECRETS = "secrets";

    /**
     * Configuration values from the project's own Terraform (the local
     * {@code cloudhopper_extra_config} in an override file).
     */
    String PROJECT_TERRAFORM_CONFIG = "project-terraform-config";

    /**
     * The platform enforces the declared access of {@code @Bucket} and {@code @Table} per
     * identity (IAM policies), not only the runtime: a function of a read-only identity that
     * bypasses the runtime's check is refused by the platform.
     */
    String PLATFORM_PERMISSIONS = "platform-permissions";

    /**
     * @return the capabilities the test needs, e.g. {@link #SECRETS}
     */
    List<String> requiredCapabilities();
}
