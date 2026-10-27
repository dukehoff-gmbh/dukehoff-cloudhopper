package eu.cloudhopper.mc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
 * Declares that a function should be exposed via an HTTP endpoint.
 *
 * <p>This annotation provides HTTP metadata such as method, path, summary, and operation ID,
 * which can be used by code generators to configure API gateways or HTTP routing
 * (e.g., AWS API Gateway, GCP Cloud Functions, Azure HTTP Triggers).</p>
 *
 * <p>It is typically used in combination with {@link eu.cloudhopper.mc.annotations.Function}
 * to describe the HTTP-facing contract of a function.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Function(name = "hello")
 * @HttpTrigger(
 *     method = "GET",
 *     path = "/hello/{name}",
 *     summary = "Says hello",
 *     description = "Returns a greeting using the given name",
 *     operationId = "sayHello"
 * )
 * public String handleRequest(@PathParam("name") String name) {
 *     return "Hello, " + name + "!";
 * }
 * }</pre>
 *
 * <p>Code generators may use this information to:</p>
 * <ul>
 *     <li>Generate OpenAPI documentation</li>
 *     <li>Configure routing rules in serverless platforms</li>
 *     <li>Generate client SDKs</li>
 * </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface HttpTrigger {

    /**
     * A short summary of the operation.
     * <p>Typically used in generated documentation.</p>
     *
     * @return summary string
     */
    String summary() default "";

    /**
     * A longer, detailed description of the operation.
     * <p>Useful for API documentation or developer portals.</p>
     *
     * @return description string
     */
    String description() default "";

    /**
     * A unique identifier for the operation.
     * <p>This may be used for client SDK generation or route mapping.</p>
     *
     * @return operation ID
     */
    String operationId() default "";

    /**
     * The HTTP path to which the function is bound (e.g., {@code "/hello/{name}"}).
     * <p>Supports path parameters enclosed in braces.</p>
     *
     * @return endpoint path
     */
    String path() default "";

    /**
     * The HTTP method to use (e.g., {@code "GET"}, {@code "POST"}).
     *
     * @return HTTP method
     */
    String method() default "";

    /**
     * Constants representing attribute names of {@link HttpTrigger}.
     *
     * <p>These can be used by generator implementations to declare
     * support for specific attributes.</p>
     */
    class HttpTriggerAttribute {
        public static final String SUMMARY = "summary";
        public static final String DESCRIPTION = "description";
        public static final String OPERATION_ID = "operationId";
        public static final String PATH = "path";
        public static final String METHOD = "method";
    }
}