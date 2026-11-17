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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraformDataSourcesTest {

    @Test
    void findsTheNamesOfDataSourcesOfOneType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a_queue.tf"), """
                data "aws_sqs_queue" "a_queue" {
                  name = "existing-queue"
                }
                resource "aws_sqs_queue" "b_queue" {
                  name = "created-queue"
                }
                """);
        Files.writeString(dir.resolve("b_storage.tf"), """
                data "aws_s3_bucket" "b_bucket" {
                  bucket = "existing-bucket"
                }
                data "aws_sqs_queue" "c_queue" {
                  name = "${var.prefix}computed"
                }
                """);
        Files.writeString(dir.resolve("notes.txt"), "data \"aws_sqs_queue\" \"x\" { name = \"ignored\" }");

        assertEquals(List.of("existing-queue"), TerraformDataSources.names(dir, "aws_sqs_queue", "name"));
        assertEquals(List.of("existing-bucket"), TerraformDataSources.names(dir, "aws_s3_bucket", "bucket"));
        assertEquals(List.of(), TerraformDataSources.names(dir.resolve("missing"), "aws_s3_bucket", "bucket"));
    }
}
