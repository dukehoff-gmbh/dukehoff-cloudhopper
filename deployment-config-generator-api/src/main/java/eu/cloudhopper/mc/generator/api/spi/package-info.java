/**
 * Defines the Service Provider Interface (SPI) for Cloudhopper generators.
 * <p>
 * Generator modules must implement {@link eu.cloudhopper.mc.generator.api.spi.DeploymentConfigGenerator}
 * to participate in the Cloudhopper code generation process.
 *
 * <p>
 * The SPI allows full control over how deployment artifacts are generated from annotated source code.
 */
package eu.cloudhopper.mc.generator.api.spi;

/*-
 * #%L
 * deployment-config-generator-api - a library from the "Cloudhopper" project.
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
