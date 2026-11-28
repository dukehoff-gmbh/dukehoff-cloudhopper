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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.EventTrigger;
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
    private final Set<String> generatedIdentities = new java.util.HashSet<>();
    /** The queues all functions send to (@Queue), by name, for the FINALIZE phase. */
    private final Map<String, eu.cloudhopper.mc.generator.api.QueueInfo> publishedQueues = new java.util.TreeMap<>();
    /** The function that consumes each queue (@EventTrigger QUEUE), by queue name, for the FINALIZE phase. */
    private final Map<String, String> queueConsumers = new java.util.TreeMap<>();
    /** Whether the consuming function creates the queue (@EventTrigger create), by queue name. */
    private final Map<String, Boolean> queueCreated = new java.util.TreeMap<>();
    /** The identities of the functions that send to each queue (@Queue), by queue name. */
    private final Map<String, Set<String>> queuePublishers = new java.util.TreeMap<>();
    /** The buckets functions use (@Bucket), by name, for the FINALIZE phase. */
    private final Map<String, eu.cloudhopper.mc.generator.api.BucketInfo> buckets = new java.util.TreeMap<>();
    /** The access of each identity to each bucket (READ, WRITE or READ_WRITE), by bucket name and identity. */
    private final Map<String, Map<String, String>> bucketAccess = new java.util.TreeMap<>();
    /** The tables functions use (@Table), by name, for the FINALIZE phase. */
    private final Map<String, eu.cloudhopper.mc.generator.api.TableInfo> tables = new java.util.TreeMap<>();
    /** The access of each identity to each table (READ, WRITE or READ_WRITE), by table name and identity. */
    private final Map<String, Map<String, String>> tableAccess = new java.util.TreeMap<>();
    /** The function whose storage trigger (@EventTrigger STORAGE) has each bucket, by bucket name. */
    private final Map<String, String> storageBuckets = new java.util.TreeMap<>();
    /** Whether that storage trigger creates the bucket (@EventTrigger create), by bucket name. */
    private final Map<String, Boolean> storageBucketCreated = new java.util.TreeMap<>();
    /** The configuration properties (@ConfigProperty) the functions of each identity read, by identity. */
    private final Map<String, Set<String>> identityConfigProperties = new java.util.TreeMap<>();
    /**
     * The deployment unit of each function on targets that deploy one unit per identity: its
     * {@code @Function(identity)}, or empty for the default unit of the functions without one.
     */
    private final Map<String, String> functionUnits = new java.util.TreeMap<>();
    private String generatorId;

    public GenericDeploymentConfigGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.templateRenderer = new TemplateRenderer();
        this.sharedConfigGenerated = false;
        generatorId = processingEnv.getOptions().getOrDefault("generatorId", "").trim();
        generatorConfig = GeneratorConfigLoader.loadGeneratorConfig(processingEnv, GENERATOR_CONFIG_DIR + generatorId + "-templates.json");
        List<String> generatorIds = new ArrayList<>(List.of(generatorId));
        if (generatorConfig != null && generatorConfig.getBasedOn() != null) {
            String baseId = generatorConfig.getBasedOn();
            GeneratorConfig base = GeneratorConfigLoader.loadGeneratorConfig(processingEnv, GENERATOR_CONFIG_DIR + baseId + "-templates.json", baseId);
            if (base != null) {
                generatorConfig.setPhases(mergePhases(base.getPhases(), generatorConfig.getPhases()));
                generatorIds.add(baseId);
            }
        }
        this.templateRenderer.initTemplateLoaders(this.getClass(), generatorIds, this::getTemplateDirectory, processingEnv);
    }

    /**
     * The base generator's templates followed by the generator's own; an own template
     * with the same name replaces the base generator's template.
     */
    static Map<String, List<TemplateDescriptor>> mergePhases(Map<String, List<TemplateDescriptor>> base,
            Map<String, List<TemplateDescriptor>> own) {
        Map<String, List<TemplateDescriptor>> safeOwn = own == null ? Map.of() : own;
        Set<String> ownNames = safeOwn.values().stream().flatMap(List::stream)
                .map(TemplateDescriptor::getTemplateName).collect(Collectors.toSet());
        Map<String, List<TemplateDescriptor>> merged = new HashMap<>();
        if (base != null) {
            base.forEach((phase, templates) -> templates.stream()
                    .filter(t -> !ownNames.contains(t.getTemplateName()))
                    .forEach(t -> merged.computeIfAbsent(phase, k -> new ArrayList<>()).add(t)));
        }
        safeOwn.forEach((phase, templates) -> merged.computeIfAbsent(phase, k -> new ArrayList<>()).addAll(templates));
        return merged;
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
            if (generatedIdentities.add(handlerInfo.getIdentity())) {
                runPhase(processingEnv, GenerationPhase.IDENTITY, dataModel, handlerInfo, outputDir,
                        handlerInfo.getIdentity());
            }
            functionUnits.put(handlerInfo.getFunctionId(), unit(handlerInfo));
            Set<String> properties = identityConfigProperties.computeIfAbsent(handlerInfo.getIdentity(),
                    identity -> new java.util.TreeSet<>());
            handlerInfo.getConfigProperties().forEach(property -> properties.add(property.getName()));
            handlerInfo.getPublishedQueues().forEach(queue -> {
                publishedQueues.putIfAbsent(queue.getName(), queue);
                queuePublishers.computeIfAbsent(queue.getName(), name -> new java.util.TreeSet<>())
                        .add(handlerInfo.getIdentity());
            });
            handlerInfo.getBuckets().forEach(bucket -> {
                buckets.putIfAbsent(bucket.getName(), bucket);
                bucketAccess.computeIfAbsent(bucket.getName(), name -> new java.util.TreeMap<>())
                        .merge(handlerInfo.getIdentity(), bucket.getAccess(),
                                (a, b) -> a.equals(b) ? a : "READ_WRITE");
            });
            handlerInfo.getTables().forEach(table -> {
                tables.putIfAbsent(table.getName(), table);
                tableAccess.computeIfAbsent(table.getName(), name -> new java.util.TreeMap<>())
                        .merge(handlerInfo.getIdentity(), table.getAccess(),
                                (a, b) -> a.equals(b) ? a : "READ_WRITE");
            });
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
        putOptions(dataModel);
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
        // targets that run all functions in one deployment unit configure the publishers there
        dataModel.put("publishedQueues", new ArrayList<>(publishedQueues.values()));
        dataModel.put("queueConsumers", queueConsumers);
        dataModel.put("queueCreated", queueCreated);
        dataModel.put("queuePublishers", queuePublishers);
        dataModel.put("identityConfigProperties", identityConfigProperties);
        // the deployment units, sorted, the default unit ("") first if a function has no identity
        dataModel.put("units", new ArrayList<>(new java.util.TreeSet<>(functionUnits.values())));
        dataModel.put("functionUnits", functionUnits);
        saveFunctionUnits(configOutputDir);
        dataModel.put("buckets", new ArrayList<>(buckets.values()));
        dataModel.put("bucketAccess", bucketAccess);
        dataModel.put("storageBuckets", storageBuckets);
        dataModel.put("storageBucketCreated", storageBucketCreated);
        dataModel.put("tables", new ArrayList<>(tables.values()));
        dataModel.put("tableAccess", tableAccess);

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

    @Override
    public void generateEventTrigger(String generatorId, String configOutputDir, HandlerInfo handlerInfo,
            EventTrigger eventTrigger, ProcessingEnvironment processingEnv) throws ConfigGenerationException {
        Map<String, Object> dataModel = createBaseDataModel(handlerInfo);
        dataModel.put("packageName", handlerInfo.getHandlerPackage());
        dataModel.put("eventSource", eventTrigger.source().name());
        dataModel.put("eventName", eventTrigger.name());
        dataModel.put("eventCreate", eventTrigger.create());
        if (eventTrigger.source() == eu.cloudhopper.mc.annotations.EventSource.QUEUE) {
            queueConsumers.putIfAbsent(eventTrigger.name(), handlerInfo.getFunctionId());
            queueCreated.putIfAbsent(eventTrigger.name(), eventTrigger.create());
        }
        if (eventTrigger.source() == eu.cloudhopper.mc.annotations.EventSource.STORAGE) {
            storageBuckets.putIfAbsent(eventTrigger.name(), handlerInfo.getFunctionId());
            storageBucketCreated.putIfAbsent(eventTrigger.name(), eventTrigger.create());
        }
        runPhase(processingEnv, GenerationPhase.EVENT, dataModel, handlerInfo, configOutputDir,
                handlerInfo.getFunctionId().toLowerCase() + "_event");
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
            if (descriptor.isClassOutput()) {
                // a resource of the compiled classes, e.g. META-INF/native-image/.../reachability-metadata.json
                templateRenderer.renderClassOutput(processingEnv, descriptor, dataModel, fileName);
            } else if (descriptor.isJavaFile()) {
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
        if (handlerInfo != null) {
            // targets with one deployment unit per identity: the unit of this function
            dataModel.put("unit", unit(handlerInfo));
        }
        putOptions(dataModel);
        return dataModel;
    }

    /**
     * Options of the build that templates may read: {@code runtime} ({@code jvm} by default, or
     * {@code native} for a native image) and {@code nativeArchitecture} (e.g. {@code arm64}).
     */
    /**
     * The directory of the built artifact relative to the generated configuration (e.g.
     * {@code ../..} from {@code target/deployment/aws-terraform} to {@code target}), so that the
     * configuration does not depend on the path of the build machine; the absolute target
     * directory if the two do not share a root.
     */
    static String artifactDir(Map<String, String> options) {
        String target = options.getOrDefault("targetDir", "./target");
        String config = options.getOrDefault("configOutputDir", "target/generated-config");
        try {
            java.nio.file.Path relative = java.nio.file.Path.of(config).toAbsolutePath().normalize()
                    .relativize(java.nio.file.Path.of(target).toAbsolutePath().normalize());
            String path = relative.toString().replace('\\', '/');
            return path.isEmpty() ? "." : path;
        } catch (IllegalArgumentException e) {
            return target.replace('\\', '/');
        }
    }

    private void putOptions(Map<String, Object> dataModel) {
        Map<String, String> options = processingEnv.getOptions();
        // an option given without a value (-Akey=) is present with the value null
        dataModel.put("runtime", java.util.Objects.requireNonNullElse(options.get("runtime"), "jvm").trim());
        dataModel.put("nativeArchitecture", java.util.Objects.requireNonNullElse(options.get("nativeArchitecture"), "arm64").trim());
        dataModel.put("artifactDir", artifactDir(options));
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
        // scopes imply authentication
        dataModel.put("authenticated", apiOp.authenticated() || apiOp.scopes().length > 0);
        dataModel.put("scopes", List.of(apiOp.scopes()));
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

    /**
     * @param handlerInfo a function
     * @return its deployment unit on targets with one unit per identity: the
     *         {@code @Function(identity)}, or empty for the default unit
     */
    public static String unit(HandlerInfo handlerInfo) {
        return handlerInfo.isSharedIdentity() ? handlerInfo.getIdentity() : "";
    }

    /** Writes META-INF/function-units.properties: function ID = unit, for the TCK bindings. */
    private void saveFunctionUnits(String configOutputDir) {
        Properties units = new Properties();
        units.putAll(functionUnits);
        java.nio.file.Path file = java.nio.file.Path.of(configOutputDir, "META-INF", "function-units.properties");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            try (java.io.Writer out = java.nio.file.Files.newBufferedWriter(file)) {
                units.store(out, "the deployment unit of each function; empty: the default unit");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Cannot write " + file + ": " + e.getMessage());
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
