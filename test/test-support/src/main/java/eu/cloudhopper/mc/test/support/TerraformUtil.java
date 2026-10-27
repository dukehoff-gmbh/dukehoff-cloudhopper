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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TerraformUtil {

    public static JsonNode readTerraformOutput(Path terraformDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("terraform", "output", "-json");
        pb.directory(terraformDir.toFile());
        Process p = pb.start();
        byte[] output = p.getInputStream().readAllBytes();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("terraform output failed: " + new String(output, StandardCharsets.UTF_8));
        }
        return new ObjectMapper().readTree(output);
    }

    public static String getOutputString(Path terraformDir, String key) throws IOException, InterruptedException {
        JsonNode outputs = readTerraformOutput(terraformDir);
        final String value = outputs.path(key).path("value").asText();
        System.out.println("Terraform output key = "+key+" value = "+value);
        return value;
    }
}
