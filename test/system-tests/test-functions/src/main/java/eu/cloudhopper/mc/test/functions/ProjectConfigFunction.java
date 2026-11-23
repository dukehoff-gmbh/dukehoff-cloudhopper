package eu.cloudhopper.mc.test.functions;

/*-
 * #%L
 * demo - a library from the "Cloudhopper" project.
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class ProjectConfigFunction {

    /**
     * Returns {@code tck.project}, a value the project's own Terraform sets (the local
     * {@code cloudhopper_extra_config} in an override file); "none" where nothing sets it. Shares
     * an identity, so that the TCK deployment on GCP stays within the quota of service accounts
     * created per minute.
     */
    @Function(name = "projectConfig", identity = "tck-shared")
    public String projectConfig(@ConfigProperty(name = "tck.project", defaultValue = "none") String value) {
        return value;
    }
}
