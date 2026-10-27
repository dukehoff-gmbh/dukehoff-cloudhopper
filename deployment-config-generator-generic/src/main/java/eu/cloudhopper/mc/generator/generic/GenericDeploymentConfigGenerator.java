package eu.cloudhopper.mc.generator.generic;

/*-
 * #%L
 * deployment-config-spi - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.generator.api.GenerationPhase;
import eu.cloudhopper.mc.generator.api.HandlerInfo;
import eu.cloudhopper.mc.generator.api.spi.DeploymentConfigGenerator;
import eu.cloudhopper.mc.generator.generic.annotations.TemplateRegistration;
import eu.cloudhopper.mc.generator.generic.internal.GeneratorConfig;
import eu.cloudhopper.mc.generator.generic.internal.GeneratorConfigLoader;
import eu.cloudhopper.mc.generator.generic.internal.TemplateDescriptor;
import eu.cloudhopper.mc.generator.generic.internal.TemplateRenderer;
import com.github.javaparser.quality.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.ScheduledTrigger;

/**
 * A default implementation of {@link DeploymentConfigGenerator} that uses
 * JSON-based configuration and Freemarker templates to generate deployment
 * files.
 * <p>
 * This generator is designed for template-based backends (e.g. Terraform,
 * CloudFormation, etc.) and requires no custom Java logic from integrators
 * beyond registering templates via annotations.
 *
 * <p>
 * The generator:
 * <ul>
 * <li>Loads generator configuration from a JSON file named
 * {@code <generatorId>-templates.json}</li>
 * <li>Renders templates grouped by {@link GenerationPhase}</li>
 * <li>Provides base and API-specific data models to templates</li>
 * <li>Supports Java file generation as well as static file generation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * To use this generator, implement a class annotated with
 * {@link TemplateRegistration}:
 *
 * <pre>{@code
 * @TemplateRegistration(
 *     generatorId = "aws-terraform",
 *     templates = {
 *         @Template(
 *             phase = GenerationPhase.FUNCTION,
 *             templateName = "function",
 *             outputFileExtension = "tf",
 *             outputSubDirectory = "deployment/aws",
 *             description = "Main Lambda function resource"
 *         )
 *     }
 * )
 * public class AwsTemplateRegistration { }
 * }</pre>
 *
 * The corresponding template files must be available in:
 * {@code /templates/<generatorId>/function.ftl}
 *
 * <p>
 * This class is loaded reflectively by the Cloudhopper annotation processor if
 * no custom {@link DeploymentConfigGenerator} is provided.
 */
public class GenericDeploymentConfigGenerator implements DeploymentConfigGenerator {

    private static final String API_HANDLER_FILE = "handler-info.properties";
    private String GENERATOR_CONFIG_DIR = "META-INF/cloudhopper/";

    GeneratorConfig generatorConfig;

    private final TemplateRenderer templateRenderer;
    private final ProcessingEnvironment processingEnv;
    private boolean sharedConfigGenerated;
    private String generatorId;

    public GenericDeploymentConfigGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.templateRenderer = new TemplateRenderer();
        this.sharedConfigGenerated = false;
        generatorId = processingEnv.getOptions().getOrDefault("generatorId", "").trim();
        this.templateRenderer.initTemplateLoaders(this.getClass(), getTemplateDirectory(generatorId), processingEnv, generatorId);
        generatorConfig = GeneratorConfigLoader.loadGeneratorConfig(processingEnv, GENERATOR_CONFIG_DIR + generatorId + "-templates.json");
    }

    @Override
    public boolean supportsGenerator(String generatorId) {
        return generatorConfig.getGeneratorId().equals(generatorId);
    }

    @Override
    public void generateServerlessFunctionConfiguration(String generatorId, String outputDir, HandlerInfo handlerInfo, ProcessingEnvironment env) throws ConfigGenerationException {
    
        Map<String, Object> dataModel = createBaseDataModel(handlerInfo);
        try {
            if (!sharedConfigGenerated) {
                sharedConfigGenerated = generateSharedConfig(generatorId, outputDir, dataModel, handlerInfo);
            }
            runPhase(processingEnv, GenerationPhase.FUNCTION, dataModel, handlerInfo, outputDir, handlerInfo.getFunctionId().toLowerCase());
        } catch (ConfigGenerationException e) {
            throw new ConfigGenerationException("Failed to generate function config for generator: " + generatorId, e);
        }
    }

    @Override
    public void generateApiResourceAndIntegration(String generatorId, String outputDir, HandlerInfo handlerInfo, HttpTrigger httpTrigger, ProcessingEnvironment env) throws ConfigGenerationException {
        Map<String, Object> dataModel = getApiIntegrationDataModelForTemplateRendering(handlerInfo, httpTrigger);
        saveHandlerInfo(handlerInfo, httpTrigger, outputDir);
        runPhase(processingEnv, GenerationPhase.API, dataModel, handlerInfo, outputDir, handlerInfo.getFunctionId().toLowerCase() + "_api");
    }

    @Override
    public void finalizeConfig(String generatorId, String configOutputDir) throws ConfigGenerationException {

        Properties properties = loadProperties(configOutputDir);
        Map<String, Object> dataModel = new HashMap<>();
        final Map<String, String> lambdaMap = properties.stringPropertyNames().stream()
                .filter(k -> k.endsWith("_Arn"))
                .collect(Collectors.toMap(k -> k, properties::getProperty));
        Map<String, Map<String, String>> lambdaMetaMap = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.endsWith("_functionId") || key.endsWith("_path") || key.endsWith("_method") || key.endsWith("_operationId")) {
                String baseKey = key.substring(0, key.lastIndexOf('_'));
                lambdaMetaMap.computeIfAbsent(baseKey, k -> new HashMap<>())
                        .put(key.substring(key.lastIndexOf('_') + 1), properties.getProperty(key));
            }
        }
        dataModel.put("lambdaMap", lambdaMap);
        dataModel.put("lambdaMetaMap", lambdaMetaMap);

        try {
            runPhase(processingEnv, GenerationPhase.FINALIZE, dataModel, null, configOutputDir, "api");
            copyDocumentationResources(generatorId, configOutputDir);
        } catch (ConfigGenerationException e) {
            throw new ConfigGenerationException("Failed to finalize config for generator: " + generatorId, e);
        }
    }

    protected boolean generateSharedConfig(String providerName, String configOutputDir, Map<String, Object> dataModel, HandlerInfo handlerInfo) {
        try {
            runPhase(processingEnv, GenerationPhase.SHARED, dataModel, handlerInfo, configOutputDir, "shared-resources");
        } catch (ConfigGenerationException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void generateScheduledTrigger(String generatorID, String configOutputDir, HandlerInfo handlerInfo, ScheduledTrigger schedule, ProcessingEnvironment processingEnv) {

        Map<String, Object> dataModel = createBaseDataModel(handlerInfo);
        dataModel.put("scheduleExpression", schedule.cron());
        dataModel.put("scheduleTimezone", schedule.timezone());

        try {
            runPhase(processingEnv, GenerationPhase.SCHEDULE, dataModel, handlerInfo, configOutputDir, handlerInfo.getFunctionId().toLowerCase() + "_schedule");
        } catch (ConfigGenerationException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate schedule for function " + handlerInfo.getFunctionId().toLowerCase() + ": " + e.getMessage());
        }
    }

    private void runPhase(ProcessingEnvironment processingEnv,
            GenerationPhase phase,
            Map<String, Object> dataModel,
            @Nullable HandlerInfo handlerInfo,
            String outputDir,
            String fileName) throws ConfigGenerationException {
        System.err.println("Running generation phase " + phase.getId() + ": " + GenerationPhase.FUNCTION.getDescription());
        List<TemplateDescriptor> templates = generatorConfig.getPhases().get(phase.getId());
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Running generation phase " + phase.getId() + ": " + GenerationPhase.FUNCTION.getDescription());
        if (templates == null) {
            return; // No templates for this phase
        }
        for (TemplateDescriptor descriptor : templates) {
            if (descriptor.isJavaFile()) {
                if (handlerInfo == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Cannot generate Java file in phase '" + phase.getId() + "' because there is no HandlerInfo.");
                    continue;
                }
                templateRenderer.generateJavaFile(processingEnv, descriptor, dataModel, handlerInfo);
                if (handlerInfo.getWrapperFullyQualifiedName() != null) {
                    dataModel.put("handlerWrapperFullyQualifiedName", handlerInfo.getWrapperFullyQualifiedName());
                }
            } else {
                String base = fileName;
                String effectiveFileName = base + "_" + stripExtension(descriptor.getTemplateName());
                templateRenderer.renderTemplate(descriptor, outputDir, dataModel, effectiveFileName);
            }
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    protected Map<String, Object> createBaseDataModel(HandlerInfo handlerInfo) {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("handlerInfo", handlerInfo);
        return dataModel;
    }

    protected Map<String, Object> getApiIntegrationDataModelForTemplateRendering(HandlerInfo handlerInfo, HttpTrigger apiOp) {
        Map<String, Object> dataModel = createBaseDataModel(handlerInfo);
        dataModel.put("packageName", handlerInfo.getHandlerPackage());
        dataModel.put("className", handlerInfo.getHandlerClassName() + "Api");
        dataModel.put("methodName", handlerInfo.getHandlerMethod());
        dataModel.put("summary", apiOp.summary());
        dataModel.put("description", apiOp.description());
        dataModel.put("operationId", apiOp.operationId());
        dataModel.put("path", apiOp.path());
        dataModel.put("httpMethod", apiOp.method().toUpperCase());
        dataModel.put("handlerClassName", handlerInfo.getHandlerClassName());
        dataModel.put("inputType", handlerInfo.getInputType());
        dataModel.put("outputType", handlerInfo.getOutputType());

        List<Map<String, String>> parameters = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(apiOp.path());
        while (matcher.find()) {
            Map<String, String> param = new HashMap<>();
            param.put("in", "PATH");
            param.put("name", matcher.group(1));
            parameters.add(param);
        }
        dataModel.put("parameters", parameters);
        return dataModel;
    }

    protected String getTemplateDirectory(String providerName) {
        return "/templates/" + providerName.toLowerCase();
    }

    private File getConfigFile(String configOutputDir) throws IOException {
        Path metaInfPath = Paths.get(configOutputDir, "META-INF");
        Files.createDirectories(metaInfPath);
        return new File(metaInfPath.toFile(), API_HANDLER_FILE);
    }

    private Properties loadProperties(String configOutputDir) {
        Properties properties = new Properties();
        try (InputStream is = new FileInputStream(getConfigFile(configOutputDir))) {
            properties.load(is);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private void saveProperties(Properties properties, String configOutputDir) {
        try (OutputStream os = new FileOutputStream(getConfigFile(configOutputDir))) {
            properties.store(os, "Generated Handler Info");
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error writing properties file: " + e.getMessage());
        }
    }

    private void saveHandlerInfo(HandlerInfo handlerInfo, HttpTrigger httpTrigger, String configOutputDir) {
        Properties properties = loadProperties(configOutputDir);
        properties.setProperty(handlerInfo.getHandlerClassName() + "_Arn", handlerInfo.getFunctionId().toLowerCase());
        properties.setProperty(handlerInfo.getHandlerClassName() + "_functionId", handlerInfo.getFunctionId());
        properties.setProperty(handlerInfo.getHandlerClassName() + "_path", httpTrigger.path());
        properties.setProperty(handlerInfo.getHandlerClassName() + "_method", httpTrigger.method().toUpperCase());
        properties.setProperty(handlerInfo.getHandlerClassName() + "_operationId", httpTrigger.operationId());
        saveProperties(properties, configOutputDir);
    }

    protected void copyDocumentationResources(String generatorId, String outputDir) {
        String basePath = getTemplateDirectory(generatorId) + "/doc/";
        try (InputStream indexStream = this.getClass().getResourceAsStream(basePath + "doc.index")) {
            if (indexStream == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "No doc.index found — skipping documentation copy.");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try (InputStream resourceStream = this.getClass().getResourceAsStream(basePath + line)) {
                    if (resourceStream == null) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                "Missing documentation resource listed in doc.index: " + line);
                        continue;
                    }

                    Path targetPath = Paths.get(outputDir, line);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to copy documentation resources: " + e.getMessage());
        }
    }

    @Override
    public String getGeneratorID() {
        return generatorId;
    }

}
