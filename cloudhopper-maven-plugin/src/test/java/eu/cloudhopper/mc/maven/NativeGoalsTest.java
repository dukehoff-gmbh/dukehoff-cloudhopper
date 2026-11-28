/*-
 * #%L
 * cloudhopper-maven-plugin - a library from the "Cloudhopper" project.
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
package eu.cloudhopper.mc.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeGoalsTest {

    @TempDir
    Path dir;

    @Test
    void readsTheHandlersOfTheGeneratedTerraform() throws Exception {
        Files.writeString(dir.resolve("price_function.tf"), """
                resource "aws_lambda_function" "price" {
                  function_name = "${var.cloudhopper_name_prefix}price"
                  handler       = "com.example.AwsLambdaPrices_price_HandlerHandler$Auto::handleRequest"
                  runtime       = "provided.al2023"
                }
                """);
        Files.writeString(dir.resolve("api_api.tf"), "resource \"aws_apigatewayv2_api\" \"main\" {}");
        assertEquals(Map.of("price", "com.example.AwsLambdaPrices_price_HandlerHandler$Auto::handleRequest"),
                new NativeSmokeMojo().handlers(dir));
    }

    @Test
    void refusesTheTerraformOfTheJavaRuntime() throws Exception {
        Files.writeString(dir.resolve("price_function.tf"), """
                  function_name = "${var.cloudhopper_name_prefix}price"
                  handler       = "com.example.X$Auto::handleRequest"
                  runtime = "java21"
                """);
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> new NativeSmokeMojo().handlers(dir));
        assertTrue(e.getMessage().contains("Java runtime"), e.getMessage());
    }

    @Test
    void anInvocationErrorOrA5xxIsAFailure() throws Exception {
        NativeSmokeMojo mojo = new NativeSmokeMojo();
        assertNull(mojo.problem("{\"statusCode\":200,\"body\":\"{}\"}"));
        assertNull(mojo.problem("{\"statusCode\":404,\"body\":\"{}\"}"));
        assertNull(mojo.problem("\"pong\""));
        assertTrue(mojo.problem("{\"statusCode\":500,\"body\":\"{\\\"error\\\":\\\"Internal Server Error\\\"}\"}").contains("500"));
        assertTrue(mojo.problem("{\"errorType\":\"java.lang.IllegalStateException\",\"errorMessage\":\"x\"}").contains("IllegalStateException"));
        assertEquals("no answer", mojo.problem(null));
    }

    @Test
    void theZipHoldsTheExecutable() throws Exception {
        Path bootstrap = Files.writeString(dir.resolve("bootstrap"), "#!/bin/sh\n");
        Path zip = dir.resolve("app-native.zip");
        NativeImageMojo.zip(bootstrap, zip);
        try (ZipFile file = ZipFile.builder().setPath(zip).get()) {
            var entry = file.getEntry("bootstrap");
            assertEquals(0755, entry.getUnixMode() & 0777);
            assertEquals(10, entry.getSize());
        }
    }
}
