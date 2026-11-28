package eu.cloudhopper.mc.deployment.config.generator;

/*-
 * #%L
 * deployment-config-generator - a library from the "Cloudhopper" project.
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
// Annotation Processor
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.generator.api.ConfigGenerationException;
import eu.cloudhopper.mc.generator.api.HandlerInfo;
import com.google.auto.service.AutoService;
import eu.cloudhopper.mc.annotations.HeaderParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.PathParam;
import eu.cloudhopper.mc.annotations.QueryParam;
import eu.cloudhopper.mc.annotations.ScheduledTrigger;
import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.annotations.EventTrigger;
import java.util.regex.Pattern;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.MethodHandlerInfo;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.BODY;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.CONTEXT;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.EVENT_METADATA;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.HEADER;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.QUERY;
import java.util.HashSet;
import javax.annotation.processing.Messager;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
@SupportedAnnotationTypes({"eu.cloudhopper.mc.annotations.Function", "eu.cloudhopper.mc.annotations.Reflective"})
@SupportedOptions({"generatorId", "configOutputDir", "artifactId", "classifier", "version", "targetDir",
        AnnotationFeatureValidator.STRICT_OPTION, "runtime", "nativeArchitecture",
        "nativeReflectionPackages"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ServerlessFunctionProcessor extends BaseDeploymentInfoProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private TypeMirror contextType;
    private TypeMirror eventMetadataType;
    private TypeMirror claimsType;
    private TypeMirror storageEventType;

    /**
     * Event source names valid on every target: Azure queue names are the strictest
     * (3-63 lowercase letters, digits and single hyphens); Pub/Sub topics must start with a letter.
     */
    private static final Pattern EVENT_SOURCE_NAME = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    /** Table names valid on every target: Azure Table Storage allows only letters and digits. */
    private static final Pattern TABLE_NAME = Pattern.compile("[a-z][a-z0-9]{2,62}");
    private static final Pattern IDENTITY_NAME = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    /** A {@code @Queue} parameter of a function. */
    private record QueueUse(ExecutableElement method, eu.cloudhopper.mc.generator.api.QueueInfo queue) {
    }

    /** The {@code @Queue} parameters of all functions. */
    private final List<QueueUse> queueUses = new ArrayList<>();
    /** The {@code @Bucket} parameters of all functions. */
    private final List<BucketUse> bucketUses = new ArrayList<>();

    /** A {@code @Bucket} parameter of a function. */
    private record BucketUse(ExecutableElement method, eu.cloudhopper.mc.generator.api.BucketInfo bucket) {
    }

    /** The {@code @Table} parameters of all functions. */
    private final List<TableUse> tableUses = new ArrayList<>();

    /** A {@code @Table} parameter of a function. */
    private record TableUse(ExecutableElement method, eu.cloudhopper.mc.generator.api.TableInfo table) {
    }

    /** The queues functions of the project consume and create ({@code @EventTrigger(QUEUE, create = true)}). */
    private final Set<String> consumedQueues = new HashSet<>();

    /** Functions without {@code identity}, by their implicit identity (the lowercase function name). */
    private final java.util.Map<String, String> implicitIdentities = new java.util.HashMap<>();
    /** Methods with an explicit {@code identity}, by that identity. */
    private final java.util.Map<String, List<ExecutableElement>> explicitIdentities = new java.util.HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.typeUtils = env.getTypeUtils();
        this.elementUtils = env.getElementUtils();
        this.contextType = elementUtils
                .getTypeElement("eu.cloudhopper.mc.runtime.HandlerContext")
                .asType();
        this.eventMetadataType = elementUtils
                .getTypeElement("eu.cloudhopper.mc.runtime.EventMetadata")
                .asType();
        this.claimsType = elementUtils
                .getTypeElement("eu.cloudhopper.mc.runtime.Claims")
                .asType();
        this.storageEventType = elementUtils
                .getTypeElement("eu.cloudhopper.mc.runtime.StorageEvent")
                .asType();
    }

    /** The metadata of @Reflective types and of -AnativeReflectionPackages. */
    private NativeReflectionMetadata nativeReflection;

    @Override
    public boolean process(Set<? extends TypeElement> annoTypes, RoundEnvironment round) {
        if (deploymentGenerator == null) {
            return true;
        }
        if (nativeReflection == null) {
            nativeReflection = new NativeReflectionMetadata(processingEnv);
        }
        nativeReflection.process(round);
        for (Element e : round.getElementsAnnotatedWith(Function.class)) {

            ExecutableElement m = (ExecutableElement) e;
            TypeElement cls = (TypeElement) m.getEnclosingElement();
            Function fn = m.getAnnotation(Function.class);
            HttpTrigger http = m.getAnnotation(HttpTrigger.class);
            ScheduledTrigger sched = m.getAnnotation(ScheduledTrigger.class);
            EventTrigger event = m.getAnnotation(EventTrigger.class);
            if (featureValidator != null) {
                featureValidator.validate(m);
            }

            List<ParamInfo> params = new ArrayList<>();
            for (VariableElement p : m.getParameters()) {
                TypeMirror tm = p.asType();
                String name = p.getSimpleName().toString();
                if (typeUtils.isSameType(tm, contextType)) {
                    params.add(new ParamInfo(CONTEXT, null, tm.toString(), name));
                } else if (typeUtils.isSameType(tm, eventMetadataType)) {
                    params.add(new ParamInfo(EVENT_METADATA, null, tm.toString(), name));
                } else if (typeUtils.isSameType(tm, claimsType)) {
                    params.add(new ParamInfo(ParamType.CLAIMS, null, tm.toString(), name));
                } else if (p.getAnnotation(eu.cloudhopper.mc.annotations.ClaimParam.class) != null) {
                    params.add(new ParamInfo(ParamType.CLAIM,
                            p.getAnnotation(eu.cloudhopper.mc.annotations.ClaimParam.class).value(), tm.toString(), name));
                } else if (p.getAnnotation(PathParam.class) != null) {
                    PathParam ann = p.getAnnotation(PathParam.class);

                    String pathParamName = ann.value().isBlank()
                            ? p.getSimpleName().toString()
                            : ann.value();
                    params.add(new ParamInfo(ParamType.PATH, pathParamName, p.asType().toString(), p.getSimpleName().toString()));

                } else if (p.getAnnotation(QueryParam.class) != null) {
                    QueryParam queryParamAnnotation = p.getAnnotation(QueryParam.class);
                    String queryParamName = queryParamAnnotation.value().isBlank()
                            ? p.getSimpleName().toString()
                            : queryParamAnnotation.value();
                    params.add(new ParamInfo(QUERY,
                            queryParamName,
                            tm.toString(), name));
                } else if (p.getAnnotation(eu.cloudhopper.mc.annotations.Queue.class) != null) {
                    var queue = p.getAnnotation(eu.cloudhopper.mc.annotations.Queue.class);
                    params.add(new ParamInfo(ParamType.QUEUE, queue.name(), tm.toString(), name,
                            String.valueOf(queue.existing())));
                } else if (p.getAnnotation(eu.cloudhopper.mc.annotations.Bucket.class) != null) {
                    var bucket = p.getAnnotation(eu.cloudhopper.mc.annotations.Bucket.class);
                    // access and existing, e.g. "READ_WRITE:false"
                    params.add(new ParamInfo(ParamType.BUCKET, bucket.name(), tm.toString(), name,
                            bucket.access().name() + ":" + bucket.existing()));
                } else if (p.getAnnotation(eu.cloudhopper.mc.annotations.Table.class) != null) {
                    var table = p.getAnnotation(eu.cloudhopper.mc.annotations.Table.class);
                    // access and existing, e.g. "READ_WRITE:false"
                    params.add(new ParamInfo(ParamType.TABLE, table.name(), tm.toString(), name,
                            table.access().name() + ":" + table.existing()));
                } else if (p.getAnnotation(org.eclipse.microprofile.config.inject.ConfigProperty.class) != null) {
                    var config = p.getAnnotation(org.eclipse.microprofile.config.inject.ConfigProperty.class);
                    params.add(new ParamInfo(ParamType.CONFIG, config.name(), tm.toString(), name,
                            config.defaultValue()));
                } else if (p.getAnnotation(HeaderParam.class) != null) {
                    params.add(new ParamInfo(HEADER,
                            p.getAnnotation(HeaderParam.class).value(),
                            tm.toString(), name));
                } else {
                    params.add(new ParamInfo(BODY, null, tm.toString(), name));
                }
            }

            boolean authenticated = http != null && (http.authenticated() || http.scopes().length > 0);
            if (!validateParams(m, params) || (event != null && !validateEventTrigger(m, event, params))
                    || !validateIdentity(m, fn) || !validateClaims(m, params, authenticated)
                    || !validateNativeImage(m, fn)) {
                continue;
            }
            String pkg = elementUtils.getPackageOf(cls).getQualifiedName().toString();
            String handlerCN = cls.getSimpleName() + "_" + m.getSimpleName() + "_Handler";
            String serviceFQ = cls.getQualifiedName().toString();
            String bodyType = params.stream()
                    .filter(pi -> pi.getType() == BODY)
                    .map(ParamInfo::getTypeName)
                    .findFirst().orElse("java.lang.Void");
            String returnType = m.getReturnType().toString();

            MethodHandlerInfo info = new MethodHandlerInfo(
                    pkg, handlerCN, serviceFQ,
                    m.getSimpleName().toString(),
                    bodyType, returnType, params,
                    !m.getThrownTypes().isEmpty()
            );
            info.setAuthentication(authenticated, http == null ? List.of() : List.of(http.scopes()));
            List<Map.Entry<String, Boolean>> primeMethods = primeMethods(cls);
            if (primeMethods == null) {
                continue;
            }
            info.setPrimeMethods(primeMethods);
            try {
                HandlerGenerator.generateHandler(info, processingEnv.getFiler());
            } catch (Exception ex) {
                processingEnv.getMessager().printMessage(ERROR,
                        "Failed to generate adapter: " + ex.getMessage(), m);
                continue;
            }
            String adapterSimpleName = info.getHandlerClassName();
            String adapterFQN = info.getPackageName() + "." + adapterSimpleName;
            if ("void".equals(returnType)) {
                returnType = "java.lang.Void";
            }

            HandlerInfo hinfo = new HandlerInfo(
                    fn.name(), fn.memory(), fn.timeout(), fn.minInstances(), fn.extensions(), fn.identity(),
                    adapterSimpleName,
                    adapterFQN, pkg,
                    m.getSimpleName().toString(),
                    bodyType, returnType,
                    artifactId, version, classifier, targetDir
            );
            List<eu.cloudhopper.mc.generator.api.QueueInfo> queues = params.stream()
                    .filter(pi -> pi.getType() == ParamType.QUEUE)
                    .map(pi -> new eu.cloudhopper.mc.generator.api.QueueInfo(pi.getName(),
                            Boolean.parseBoolean(pi.getDefaultValue())))
                    .toList();
            hinfo.setPublishedQueues(queues);
            queues.forEach(q -> queueUses.add(new QueueUse(m, q)));
            if (event != null && event.source() == eu.cloudhopper.mc.annotations.EventSource.QUEUE && event.create()) {
                consumedQueues.add(event.name());
            }
            List<eu.cloudhopper.mc.generator.api.BucketInfo> buckets = params.stream()
                    .filter(pi -> pi.getType() == ParamType.BUCKET)
                    .map(pi -> new eu.cloudhopper.mc.generator.api.BucketInfo(pi.getName(),
                            pi.getDefaultValue().split(":")[0], Boolean.parseBoolean(pi.getDefaultValue().split(":")[1])))
                    .toList();
            hinfo.setBuckets(buckets);
            buckets.forEach(b -> bucketUses.add(new BucketUse(m, b)));
            List<eu.cloudhopper.mc.generator.api.TableInfo> tables = params.stream()
                    .filter(pi -> pi.getType() == ParamType.TABLE)
                    .map(pi -> new eu.cloudhopper.mc.generator.api.TableInfo(pi.getName(),
                            pi.getDefaultValue().split(":")[0], Boolean.parseBoolean(pi.getDefaultValue().split(":")[1])))
                    .toList();
            hinfo.setTables(tables);
            BindingTypes bindingTypes = new BindingTypes(elementUtils).add(m.getReturnType());
            m.getParameters().forEach(p -> bindingTypes.add(p.asType()));
            hinfo.setBindingTypes(bindingTypes.names());
            tables.forEach(t -> tableUses.add(new TableUse(m, t)));
            List<eu.cloudhopper.mc.generator.api.ConfigPropertyInfo> configProperties = new ArrayList<>(params.stream()
                    .filter(pi -> pi.getType() == ParamType.CONFIG)
                    .map(pi -> new eu.cloudhopper.mc.generator.api.ConfigPropertyInfo(pi.getName(),
                            !pi.getTypeName().startsWith("java.util.Optional<")
                            && org.eclipse.microprofile.config.inject.ConfigProperty.UNCONFIGURED_VALUE.equals(pi.getDefaultValue())))
                    .toList());
            if (authenticated) {
                // the provider of the tokens, passed like any other configuration property
                for (String[] auth : new String[][] {
                        {eu.cloudhopper.mc.runtime.Authenticator.ISSUER, "true"},
                        {eu.cloudhopper.mc.runtime.Authenticator.AUDIENCE, "true"},
                        {eu.cloudhopper.mc.runtime.Authenticator.JWKS_URI, "false"},
                        {eu.cloudhopper.mc.runtime.Authenticator.JWKS, "false"},
                        {eu.cloudhopper.mc.runtime.Authenticator.REQUIRED_CLAIMS, "false"}}) {
                    if (configProperties.stream().noneMatch(c -> c.getName().equals(auth[0]))) {
                        configProperties.add(new eu.cloudhopper.mc.generator.api.ConfigPropertyInfo(auth[0],
                                Boolean.parseBoolean(auth[1])));
                    }
                }
            }
            hinfo.setConfigProperties(configProperties);
            try {
                deploymentGenerator.generateServerlessFunctionConfiguration(
                        generatorId, configOutputDir, hinfo, processingEnv);
                if (http != null) {
                    deploymentGenerator.generateApiResourceAndIntegration(
                            generatorId, configOutputDir, hinfo, http, processingEnv);
                }
                if (sched != null) {
                    deploymentGenerator.generateScheduledTrigger(
                            generatorId, configOutputDir, hinfo, sched, processingEnv);
                }
                if (event != null) {
                    deploymentGenerator.generateEventTrigger(
                            generatorId, configOutputDir, hinfo, event, processingEnv);
                }
            } catch (ConfigGenerationException cex) {
                processingEnv.getMessager().printMessage(ERROR,
                        cex.getMessage(), m);
            }
        }

        if (round.processingOver()) {
            checkQueues();
            checkBuckets();
            checkTables();
            try {
                deploymentGenerator.finalizeConfig(generatorId, configOutputDir);
            } catch (ConfigGenerationException e) {
                processingEnv.getMessager().printMessage(ERROR, e.getMessage());
            }
        }

        return true;
    }

    /**
     * At the end of processing: all {@code @Bucket} parameters of one name agree on
     * {@code existing}, since the generator either creates that bucket or uses it as it is, and the
     * target's runtime module for {@code @Bucket} is on the classpath.
     */
    private void checkBuckets() {
        Map<String, BucketUse> first = new HashMap<>();
        for (BucketUse use : bucketUses) {
            BucketUse other = first.putIfAbsent(use.bucket().getName(), use);
            if (other != null && other.bucket().isExisting() != use.bucket().isExisting()) {
                processingEnv.getMessager().printMessage(ERROR,
                        "@Bucket(name = \"" + use.bucket().getName() + "\") on " + use.method().getSimpleName()
                        + " declares existing = " + use.bucket().isExisting() + ", but on "
                        + other.method().getSimpleName() + " existing = " + other.bucket().isExisting()
                        + "; all parameters of one bucket must agree", use.method());
            }
        }
        checkRuntimeModule(eu.cloudhopper.mc.annotations.Bucket.class, "use buckets (@Bucket)",
                bucketUses.isEmpty() ? null : bucketUses.get(0).method());
    }

    /**
     * At the end of processing: all {@code @Table} parameters of one name agree on
     * {@code existing}, and the target's runtime module for {@code @Table} is on the classpath.
     */
    private void checkTables() {
        Map<String, TableUse> first = new HashMap<>();
        for (TableUse use : tableUses) {
            TableUse other = first.putIfAbsent(use.table().getName(), use);
            if (other != null && other.table().isExisting() != use.table().isExisting()) {
                processingEnv.getMessager().printMessage(ERROR,
                        "@Table(name = \"" + use.table().getName() + "\") on " + use.method().getSimpleName()
                        + " declares existing = " + use.table().isExisting() + ", but on "
                        + other.method().getSimpleName() + " existing = " + other.table().isExisting()
                        + "; all parameters of one table must agree", use.method());
            }
        }
        checkRuntimeModule(eu.cloudhopper.mc.annotations.Table.class, "use tables (@Table)",
                tableUses.isEmpty() ? null : tableUses.get(0).method());
    }

    /**
     * At the end of processing: every project queue a function sends to is created by a queue
     * trigger of the project, and the target's runtime module for {@code @Queue} is on the classpath.
     */
    private void checkQueues() {
        for (QueueUse use : queueUses) {
            if (!use.queue().isExisting() && !consumedQueues.contains(use.queue().getName())) {
                processingEnv.getMessager().printMessage(ERROR,
                        "@Queue(name = \"" + use.queue().getName() + "\") on " + use.method().getSimpleName()
                        + ": no function of the project consumes this queue with @EventTrigger(source = QUEUE, name = \""
                        + use.queue().getName() + "\"), so nothing would create it. For a queue another system owns,"
                        + " declare @Queue(name = \"" + use.queue().getName() + "\", existing = true)", use.method());
            }
        }
        checkRuntimeModule(eu.cloudhopper.mc.annotations.Queue.class, "send to queues (@Queue)",
                queueUses.isEmpty() ? null : queueUses.get(0).method());
    }

    /**
     * At the end of processing: the target's runtime module for a parameter annotation is on the
     * classpath if a function uses the annotation.
     *
     * @param annotation the parameter annotation
     * @param what       what the functions do, for the message, e.g. "use buckets (@Bucket)"
     * @param user       a function that uses the annotation, or {@code null} if none does
     */
    private void checkRuntimeModule(Class<?> annotation, String what, ExecutableElement user) {
        if (user == null || generatorFeatureInfo == null) {
            return;
        }
        generatorFeatureInfo.getFeatures().stream()
                .filter(f -> annotation.getName().equals(f.getSupportedAnnotation()))
                .filter(f -> f.getRuntimeClass() != null && f.getRuntimeModule() != null)
                .findFirst()
                .filter(f -> processingEnv.getElementUtils().getTypeElement(f.getRuntimeClass()) == null)
                .ifPresent(f -> processingEnv.getMessager().printMessage(ERROR,
                        "Functions " + what + ", and generator '" + generatorId + "' needs the runtime module "
                        + f.getRuntimeModule() + " for that: add it as a dependency", user));
    }

    /**
     * Checks the identity name and that an explicit identity is not also the implicit identity of a
     * function without one: both would silently run as one identity.
     */
    /**
     * The {@code @OnPrime} methods of a function class: without parameters and not private.
     *
     * @return the methods (name to static), or {@code null} after an error
     */
    private List<Map.Entry<String, Boolean>> primeMethods(TypeElement cls) {
        List<Map.Entry<String, Boolean>> methods = new ArrayList<>();
        boolean valid = true;
        for (ExecutableElement method : javax.lang.model.util.ElementFilter.methodsIn(cls.getEnclosedElements())) {
            if (method.getAnnotation(eu.cloudhopper.mc.annotations.OnPrime.class) == null) {
                continue;
            }
            if (!method.getParameters().isEmpty() || method.getModifiers().contains(javax.lang.model.element.Modifier.PRIVATE)) {
                processingEnv.getMessager().printMessage(ERROR, "@OnPrime method " + method.getSimpleName()
                        + " must have no parameters and must not be private", method);
                valid = false;
                continue;
            }
            methods.add(Map.entry(method.getSimpleName().toString(),
                    method.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)));
        }
        return valid ? methods : null;
    }

    /**
     * A native image ({@code -Aruntime=native}) is one executable for all functions, built for
     * one architecture ({@code -AnativeArchitecture}, arm64 by default): a function that asks
     * for another architecture cannot run it. SnapStart exists only for the Java runtime.
     */
    private boolean validateNativeImage(ExecutableElement method, Function fn) {
        if (!"native".equals(processingEnv.getOptions().getOrDefault("runtime", "jvm").trim())) {
            return true;
        }
        String architecture = processingEnv.getOptions().getOrDefault("nativeArchitecture", "arm64").trim();
        boolean valid = true;
        for (eu.cloudhopper.mc.annotations.Extension extension : fn.extensions()) {
            if (eu.cloudhopper.mc.annotations.ExtensionKeys.Aws.Architecture.KEY.equals(extension.name())
                    && !architecture.equals(extension.value())) {
                processingEnv.getMessager().printMessage(ERROR, "Function " + fn.name() + " asks for the architecture "
                        + extension.value() + ", but the native image is built for " + architecture
                        + ": one executable serves all functions. Remove the extension or build for "
                        + extension.value() + " (-AnativeArchitecture).", method);
                valid = false;
            }
            if (eu.cloudhopper.mc.annotations.ExtensionKeys.Aws.SnapStart.KEY.equals(extension.name())) {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.WARNING, "Function " + fn.name()
                        + ": SnapStart is only available for the Java runtime and is ignored for the native image,"
                        + " which starts without it.", method);
            }
        }
        return valid;
    }

    private boolean validateIdentity(ExecutableElement method, Function fn) {
        String identity = fn.identity();
        if (identity.isEmpty()) {
            String implicit = fn.name().toLowerCase();
            implicitIdentities.put(implicit, fn.name());
            for (ExecutableElement other : explicitIdentities.getOrDefault(implicit, List.of())) {
                reportIdentityClash(other, implicit, fn.name());
            }
            return true;
        }
        if (identity.length() > 32 || !IDENTITY_NAME.matcher(identity).matches()) {
            processingEnv.getMessager().printMessage(ERROR,
                    "@Function identity '" + identity + "' is not valid on every target: use up to 32 lowercase"
                    + " letters, digits and single hyphens, starting with a letter", method);
            return false;
        }
        explicitIdentities.computeIfAbsent(identity, k -> new ArrayList<>()).add(method);
        String function = implicitIdentities.get(identity);
        if (function != null) {
            reportIdentityClash(method, identity, function);
            return false;
        }
        return true;
    }

    private void reportIdentityClash(ExecutableElement method, String identity, String function) {
        processingEnv.getMessager().printMessage(ERROR,
                "@Function identity '" + identity + "' is also the identity of function '" + function
                + "', which has no identity of its own declared; both would run as one identity. Use another"
                + " identity name, or declare identity = \"" + identity + "\" on '" + function
                + "' as well if they should share it", method);
    }

    private boolean validateEventTrigger(ExecutableElement method, EventTrigger event, List<ParamInfo> params) {
        String name = event.name();
        if (name.length() < 3 || name.length() > 63 || !EVENT_SOURCE_NAME.matcher(name).matches()) {
            processingEnv.getMessager().printMessage(ERROR,
                    "@EventTrigger name '" + name + "' is not valid on every target: use 3 to 63 lowercase"
                    + " letters, digits and single hyphens, starting with a letter.", method);
            return false;
        }
        if (event.source() == EventSource.STORAGE) {
            for (VariableElement p : method.getParameters()) {
                boolean isBody = params.stream().anyMatch(pi -> pi.getType() == BODY
                        && pi.getVarName().equals(p.getSimpleName().toString()));
                if (isBody && !typeUtils.isSameType(p.asType(), storageEventType)) {
                    processingEnv.getMessager().printMessage(ERROR,
                            "A function with @EventTrigger(source = STORAGE) receives a"
                            + " eu.cloudhopper.mc.runtime.StorageEvent; parameter '" + p.getSimpleName()
                            + "' has type " + p.asType() + ".", p);
                    return false;
                }
            }
        }
        return true;
    }

    private static final java.util.Set<String> CLAIM_TYPES = java.util.Set.of("java.lang.String", "java.lang.Long",
            "java.lang.Integer", "java.lang.Double", "java.lang.Boolean", "java.util.List<java.lang.String>");

    /** Claims exist only on routes that require a token. */
    private boolean validateClaims(ExecutableElement method, List<ParamInfo> params, boolean authenticated) {
        Messager messager = processingEnv.getMessager();
        for (ParamInfo p : params) {
            if (p.getType() != ParamType.CLAIM && p.getType() != ParamType.CLAIMS) {
                continue;
            }
            if (!authenticated) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Parameter '" + p.getVarName() + "' of "
                        + method.getSimpleName() + " reads the caller's token: the route needs"
                        + " @HttpTrigger(authenticated = true)", method);
                return false;
            }
            if (p.getType() == ParamType.CLAIM && !CLAIM_TYPES.contains(p.getTypeName())) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@ClaimParam on parameter '" + p.getVarName() + "' of "
                        + method.getSimpleName() + " needs the type String, Long, Integer, Double, Boolean or"
                        + " List<String>, not " + p.getTypeName(), method);
                return false;
            }
        }
        return true;
    }

    private boolean validateParams(ExecutableElement method,
            List<ParamInfo> params) {
        Messager messager = processingEnv.getMessager();

        // Count body params
        long bodyCount = params.stream()
                .filter(p -> p.getType() == ParamInfo.ParamType.BODY)
                .count();
        if (bodyCount > 1) {
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Method " + method.getSimpleName()
                    + " has more than one un-annotated body parameter; only one is allowed.",
                    method);
            return false;
        }

        for (ParamInfo p : params) {
            if (p.getType() == ParamInfo.ParamType.QUEUE
                    && !p.getTypeName().startsWith("eu.cloudhopper.mc.runtime.Publisher<")) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Queue on parameter '" + p.getVarName() + "' of " + method.getSimpleName()
                        + " needs the type Publisher<T>, e.g. Publisher<Order>", method);
                return false;
            }
        }

        for (ParamInfo p : params) {
            if (p.getType() != ParamInfo.ParamType.BUCKET) {
                continue;
            }
            if (!p.getTypeName().equals("eu.cloudhopper.mc.runtime.BlobStore")) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Bucket on parameter '" + p.getVarName() + "' of " + method.getSimpleName()
                        + " needs the type BlobStore", method);
                return false;
            }
            boolean existing = p.getDefaultValue().endsWith(":true");
            if (!existing && (p.getName().length() < 3 || p.getName().length() > 63
                    || !EVENT_SOURCE_NAME.matcher(p.getName()).matches())) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Bucket(name = \"" + p.getName() + "\") on " + method.getSimpleName()
                        + ": use 3 to 63 lowercase letters, digits and single hyphens, starting with a letter,"
                        + " so that the name is valid on every target", method);
                return false;
            }
        }

        for (ParamInfo p : params) {
            if (p.getType() != ParamInfo.ParamType.TABLE) {
                continue;
            }
            if (!p.getTypeName().equals("eu.cloudhopper.mc.runtime.KeyValueStore")) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Table on parameter '" + p.getVarName() + "' of " + method.getSimpleName()
                        + " needs the type KeyValueStore", method);
                return false;
            }
            boolean existing = p.getDefaultValue().endsWith(":true");
            if (!existing && !TABLE_NAME.matcher(p.getName()).matches()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Table(name = \"" + p.getName() + "\") on " + method.getSimpleName()
                        + ": use 3 to 63 lowercase letters and digits, starting with a letter,"
                        + " so that the name is valid on every target", method);
                return false;
            }
        }

        // a parameter has no injection point name to derive the property name from
        for (ParamInfo p : params) {
            if (p.getType() == ParamInfo.ParamType.CONFIG && (p.getName() == null || p.getName().isBlank())) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@ConfigProperty on parameter '" + p.getVarName() + "' of " + method.getSimpleName()
                        + " needs a name, e.g. @ConfigProperty(name = \"report.bucket\")", method);
                return false;
            }
        }

        // Check for duplicate keys in PATH/QUERY/HEADER
        Set<String> seen = new HashSet<>();
        for (ParamInfo p : params) {
            if (p.getType() == ParamInfo.ParamType.PATH
                    || p.getType() == ParamInfo.ParamType.QUERY
                    || p.getType() == ParamInfo.ParamType.HEADER) {
                if (!seen.add(p.getName())) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Duplicate parameter name '" + p.getName()
                            + "' in method " + method.getSimpleName(),
                            method);
                    return false;
                }
            }
        }
        return true;
    }
}
