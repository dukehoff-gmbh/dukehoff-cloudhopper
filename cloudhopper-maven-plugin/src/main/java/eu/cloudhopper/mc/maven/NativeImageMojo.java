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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Builds the native image of the project's aws-terraform functions: compiles the function jar
 * with GraalVM in a container (native images cannot be cross-compiled) into the executable
 * {@code bootstrap}, the entry point {@code eu.cloudhopper.mc.provider.aws.NativeRuntime}, and
 * packs it into the zip the generated Terraform deploys
 * ({@code <artifactId>-<version>-<classifier>-native.zip}). Needs Docker.
 */
@Mojo(name = "native-image", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class NativeImageMojo extends AbstractMojo {

    /** The GraalVM image that compiles the native image. */
    @Parameter(property = "native.image", defaultValue = "ghcr.io/graalvm/native-image-community:25.0.2")
    private String image;

    /** The architecture of the executable: arm64 or x86_64; another than the machine's runs emulated. */
    @Parameter(property = "native.architecture", defaultValue = "arm64")
    private String architecture;

    /** The directory of the jar and the results. */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private java.io.File targetDirectory;

    /** The artifact name: the jar is {@code <finalName>-<classifier>.jar}. */
    @Parameter(defaultValue = "${project.artifactId}-${project.version}", readonly = true)
    private String finalName;

    /** The classifier of the function jar, as for the generated Terraform. */
    @Parameter(property = "cloudhopper.classifier", defaultValue = "aws-terraform")
    private String classifier;

    /** Further options of native-image, e.g. -J-Xmx3g to stay within Docker's memory. */
    @Parameter(property = "native.options")
    private List<String> options;

    /** Skips the goal. */
    @Parameter(property = "native.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping the native image");
            return;
        }
        Path target = targetDirectory.toPath().toAbsolutePath();
        String jar = finalName + "-" + classifier + ".jar";
        if (!Files.isRegularFile(target.resolve(jar))) {
            throw new MojoExecutionException("No function jar " + target.resolve(jar)
                    + ": run the goal after the jar is built (phase package, after the shade plugin)");
        }
        List<String> command = new java.util.ArrayList<>(List.of("docker", "run", "--rm",
                "--platform", "linux/" + architecture, "-v", target + ":/work", image,
                "--no-fallback", "-cp", "/work/" + jar, "-o", "/work/bootstrap"));
        if (options != null) {
            command.addAll(options);
        }
        command.add("eu.cloudhopper.mc.provider.aws.NativeRuntime");
        getLog().info("Building the native image for linux/" + architecture + " with " + image
                + " (takes a minute or more)");
        Docker.run(getLog(), target, command);
        Path zip = target.resolve(finalName + "-" + classifier + "-native.zip");
        try {
            zip(target.resolve("bootstrap"), zip);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + zip + ": " + e.getMessage(), e);
        }
        getLog().info("Native image: " + zip);
    }

    /** A zip with the executable {@code bootstrap} (mode 0755), as the custom runtime expects it. */
    static void zip(Path bootstrap, Path zip) throws IOException {
        try (OutputStream out = Files.newOutputStream(zip);
                ZipArchiveOutputStream archive = new ZipArchiveOutputStream(out)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("bootstrap");
            entry.setUnixMode(0100755);
            entry.setSize(Files.size(bootstrap));
            archive.putArchiveEntry(entry);
            Files.copy(bootstrap, archive);
            archive.closeArchiveEntry();
        }
    }
}
