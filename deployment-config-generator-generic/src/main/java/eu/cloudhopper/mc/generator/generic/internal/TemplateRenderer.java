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
import eu.cloudhopper.mc.generator.api.ConfigGenerationException;
import eu.cloudhopper.mc.generator.api.HandlerInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.JavaFileObject;

/**
 *
 * @author antonepple
 */
public class TemplateRenderer {

    private final Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);

        /**
     * Call this once during processor init.
     *
     * @param anchorClass   any class in your JAR so ClassTemplateLoader can find "/templates"
     * @param classpathBase e.g. "/templates"
     * @param processingEnv your ProcessingEnvironment
     * @param generatorId   the same id you used in @TemplateRegistration
     */
    public void initTemplateLoaders(
        Class<?> anchorClass,
        String classpathBase,
        ProcessingEnvironment processingEnv,
        String generatorId
    ) {
        List<TemplateLoader> loaders = new ArrayList<>();

        String overrideBase = "cloudhopper-templates/" + generatorId;
        loaders.add(new FilerTemplateLoader(processingEnv.getFiler(), overrideBase));

        loaders.add(new ClassTemplateLoader(anchorClass, classpathBase));

        freemarkerConfig.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0])));
    }
    

    public void renderTemplate(TemplateDescriptor templateDescriptor, String outputDirName, Map<String, Object> dataModel, String fileName) throws ConfigGenerationException {
        try {

            Template template = freemarkerConfig.getTemplate(templateDescriptor.getTemplateName());

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String output = writer.toString();

            Path outputDir = Path.of(outputDirName, templateDescriptor.getOutputSubDirectory());
            Files.createDirectories(outputDir);

            String baseFileName = fileName + "." + templateDescriptor.getOutputFileExtension();
            String outputFileName = baseFileName;

            Path outputPath = outputDir.resolve(outputFileName);

            try (FileWriter fileWriter = new FileWriter(outputPath.toFile())) {
                fileWriter.write(output);
            }

            System.err.println("Generated file: " + outputPath.toString());

        } catch (IOException | TemplateException e) {
            throw new ConfigGenerationException("Failed to render template: " + templateDescriptor.getTemplateName(), e);
        }
    }

    public void generateJavaFile(ProcessingEnvironment processingEnv, TemplateDescriptor templateDescriptor, Map<String, Object> dataModel, HandlerInfo handlerInfo) throws ConfigGenerationException {
        try {
            Template template = freemarkerConfig.getTemplate(templateDescriptor.getTemplateName());
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String fileContent = writer.toString();
            final String extractedClassName = extractClassName(fileContent);
            handlerInfo.setWrapperClassName(extractedClassName);
            System.err.println("Generate file extractedClassName: " + extractedClassName);

            createClassFile(processingEnv, handlerInfo.getHandlerPackage(), extractedClassName, fileContent);
        } catch (IOException | TemplateException e) {
            e.printStackTrace();
            throw new ConfigGenerationException("Failed to generate Java file from template: " + templateDescriptor.getTemplateName(), e);
        }
    }

    private void createClassFile(ProcessingEnvironment processingEnv, String packageName, String fileName, String fileContent) {
        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + fileName);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return;
        }

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println(fileContent);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static String extractClassName(String fileContent) {
        System.err.println(fileContent);
        CompilationUnit cu = StaticJavaParser.parse(fileContent);
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        if (!classes.isEmpty()) {
            return classes.get(0).getNameAsString();
        }
        return "UnknownClassName";

    }
}
