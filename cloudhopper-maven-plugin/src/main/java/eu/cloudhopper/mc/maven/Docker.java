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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/** Runs the Docker CLI. */
final class Docker {

    private Docker() {
    }

    /**
     * Runs a command and logs its output.
     *
     * @return the output
     * @throws MojoExecutionException if it cannot run or exits with an error
     */
    static String run(Log log, Path dir, List<String> command) throws MojoExecutionException {
        log.debug("Running " + String.join(" ", command));
        try {
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    String text = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    output.append(text);
                    log.debug(text.stripTrailing());
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new MojoExecutionException(String.join(" ", command.subList(0, Math.min(3, command.size())))
                        + " exited with " + exit + ":\n" + tail(output.toString()));
            }
            return output.toString();
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot run " + command.get(0) + " (is Docker installed?): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted", e);
        }
    }

    static String tail(String output) {
        String[] lines = output.split("\\R");
        int from = Math.max(0, lines.length - 40);
        return String.join("\n", java.util.Arrays.asList(lines).subList(from, lines.length));
    }
}
