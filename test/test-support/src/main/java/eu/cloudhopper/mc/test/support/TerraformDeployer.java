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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TerraformDeployer {

    private final Path workingDir;

    public TerraformDeployer(Path workingDir) {
        this.workingDir = workingDir;
    }

    public void init() throws IOException, InterruptedException {
        System.out.println("🚧 Running terraform in: " + workingDir.toAbsolutePath());

        if (!workingDir.toFile().exists()) {
            throw new IllegalStateException("❌ Terraform directory not found: " + workingDir.toAbsolutePath());
        }
        run("init");
    }

    public void apply() throws IOException, InterruptedException {
        run("apply", "-auto-approve");
    }

    public void destroy() throws IOException, InterruptedException {
        // Run init first so destroy also works when called standalone (e.g. a
        // pre-deploy cleanup, or cleanup after a deploy that failed during init).
        // terraform init is idempotent, so this is safe to repeat.
        init();
        run("destroy", "-auto-approve");
    }

    private void run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("terraform");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.inheritIO();

        Map<String, String> env = pb.environment();
        env.put("PATH", System.getenv("PATH"));

        Process process = pb.start();

        // Start a thread to continuously read output
        Thread outputReader = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        outputReader.start();

        int exitCode = process.waitFor();
        outputReader.join();  // wait until all output is printed

        if (exitCode != 0) {
            throw new RuntimeException("Terraform command failed with exit code " + exitCode);
        }
    }
}
