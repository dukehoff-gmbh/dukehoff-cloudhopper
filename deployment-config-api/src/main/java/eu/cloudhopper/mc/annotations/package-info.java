/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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
/**
 * Provides annotations for declaring cloud functions and API metadata.
 * <p>
 * Application developers annotate plain methods to describe how their
 * functions should be deployed and integrated; the annotation processor
 * generates the provider-specific handlers and deployment artifacts.
 * <p>
 * Function and trigger annotations (applied to a method):
 * <ul>
 *   <li>{@link eu.cloudhopper.mc.annotations.Function} - marks a method as a deployable function</li>
 *   <li>{@link eu.cloudhopper.mc.annotations.ScheduledTrigger} - defines a time-based (cron) trigger</li>
 *   <li>{@link eu.cloudhopper.mc.annotations.HttpTrigger} - adds HTTP metadata for API gateway integration</li>
 * </ul>
 * Parameter-binding annotations (applied to a method parameter):
 * <ul>
 *   <li>{@link eu.cloudhopper.mc.annotations.RequestBody} - binds the deserialized request body</li>
 *   <li>{@link eu.cloudhopper.mc.annotations.PathParam} - binds a URI path template variable</li>
 *   <li>{@link eu.cloudhopper.mc.annotations.QueryParam} - binds a query string parameter</li>
 *   <li>{@link eu.cloudhopper.mc.annotations.HeaderParam} - binds an HTTP request header</li>
 * </ul>
 * Vendor extensions:
 * <ul>
 *   <li>{@link eu.cloudhopper.mc.annotations.Extension} / {@link eu.cloudhopper.mc.annotations.Extensions} - attach provider-specific attributes (see {@link eu.cloudhopper.mc.annotations.ExtensionKeys})</li>
 * </ul>
 */
package eu.cloudhopper.mc.annotations;
