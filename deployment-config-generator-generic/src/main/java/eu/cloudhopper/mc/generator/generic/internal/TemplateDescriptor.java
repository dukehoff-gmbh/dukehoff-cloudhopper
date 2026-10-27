package eu.cloudhopper.mc.generator.generic.internal;

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
 * Describes a single template file used by the {@code GenericDeploymentConfigGenerator}.
 * <p>
 * Each descriptor provides metadata about:
 * <ul>
 *   <li>Which template file to use</li>
 *   <li>Where to place the generated output</li>
 *   <li>What file extension to apply</li>
 *   <li>Whether the output is a Java source file</li>
 * </ul>
 *
 * <p>
 * This class is typically declared in the {@code generator.properties} file using JSON,
 * or passed programmatically via {@link eu.cloudhopper.mc.generator.generic.TemplateRegistration}.
 */
public class TemplateDescriptor {

    private String templateName;
    private String description;
    private String outputFileExtension;
    private String outputSubDirectory;
    private boolean javaFile;

    /**
     * Constructs an empty descriptor.
     * <p>
     * Required for JSON deserialization.
     */
    public TemplateDescriptor() {
        // Empty constructor for JSON deserialization
    }

    /**
     * Constructs a new descriptor with all attributes.
     *
     * @param templateName         the name of the template file (without extension)
     * @param description          a short human-readable description
     * @param outputFileExtension  file extension for the generated output (e.g. "tf", "java")
     * @param outputSubDirectory   subfolder under the output directory (e.g. "src/main/java")
     * @param javaFile             whether the generated file should be treated as a Java source file
     */
    public TemplateDescriptor(String templateName,
                              String description,
                              String outputFileExtension,
                              String outputSubDirectory,
                              boolean javaFile) {
        this.templateName = templateName;
        this.description = description;
        this.outputFileExtension = outputFileExtension;
        this.outputSubDirectory = outputSubDirectory;
        this.javaFile = javaFile;
    }

    /**
     * @return the template name (without file extension)
     */
    public String getTemplateName() {
        return templateName;
    }

    /**
     * @return a short human-readable description of the template's purpose
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the file extension to use for the generated output (e.g. "tf", "java")
     */
    public String getOutputFileExtension() {
        return outputFileExtension;
    }

    /**
     * @return the subdirectory under the output folder where the generated file should be placed
     */
    public String getOutputSubDirectory() {
        return outputSubDirectory;
    }

    /**
     * @return true if the output should be treated as a Java source file
     */
    public boolean isJavaFile() {
        return javaFile;
    }

    @Override
    public String toString() {
        return "TemplateDescriptor{" +
               "templateName='" + templateName + '\'' +
               ", description='" + description + '\'' +
               ", outputFileExtension='" + outputFileExtension + '\'' +
               ", outputSubDirectory='" + outputSubDirectory + '\'' +
               ", javaFile=" + javaFile +
               '}';
    }
}