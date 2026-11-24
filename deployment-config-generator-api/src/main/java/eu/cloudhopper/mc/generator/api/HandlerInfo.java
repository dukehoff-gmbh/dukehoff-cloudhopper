package eu.cloudhopper.mc.generator.api;

import eu.cloudhopper.mc.annotations.Extension;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Describes a discovered handler class annotated with
 * {@link eu.cloudhopper.mc.annotations.Function}.
 * <p>
 * This class is used internally during annotation processing and generation to
 * hold metadata about each function implementation, including handler location,
 * input/output types, function configuration, and build artifact metadata.
 * <p>
 * It is passed into template processing or custom generators to drive
 * generation logic.
 */
public class HandlerInfo {

    private final String functionId;
    private final String handlerClassName;
    private final String handlerFullyQualifiedName;
    private final String handlerPackage;
    private final String handlerMethod;
    private final String inputType;
    private final String outputType;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String targetDir;
    private String wrapperFullyQualifiedName;
    private final int memory;
    private final int timeout;
    private final int minInstances;
    private final Extension[] extensions;
    private final String identity;
    private java.util.List<ConfigPropertyInfo> configProperties = java.util.List.of();
    private java.util.List<QueueInfo> publishedQueues = java.util.List.of();
    private java.util.List<BucketInfo> buckets = java.util.List.of();
    private java.util.List<TableInfo> tables = java.util.List.of();

    /**
     * Constructs a new {@code HandlerInfo} with the required properties.
     *
     * @param functionId unique identifier of the function
     * @param memory memory limit in MB
     * @param timeout timeout in seconds
     * @param minInstances minimum instances (e.g., warm start)
     * @param extensions extensions, e.g. function architecture
     * @param handlerClassName simple name of the handler class
     * @param handlerFullyQualifiedName fully qualified name of the handler
     * class
     * @param handlerPackage package name of the handler class
     * @param handlerMethod method name implementing the handler
     * @param inputType input type (possibly generic)
     * @param outputType output type (possibly generic)
     * @param artifactId Maven artifactId of the module
     * @param version Maven version of the module
     * @param classifier optional classifier
     * @param targetDir build output directory
     */
    public HandlerInfo(
            String functionId,
            int memory,
            int timeout,
            int minInstances,
            Extension[] extensions,
            String handlerClassName,
            String handlerFullyQualifiedName,
            String handlerPackage,
            String handlerMethod,
            String inputType,
            String outputType,
            String artifactId,
            String version,
            String classifier,
            String targetDir
    ) {
        this(functionId, memory, timeout, minInstances, extensions, "", handlerClassName,
                handlerFullyQualifiedName, handlerPackage, handlerMethod, inputType, outputType,
                artifactId, version, classifier, targetDir);
    }

    /**
     * Constructs a new {@code HandlerInfo} with an identity.
     *
     * @param functionId unique identifier of the function
     * @param memory memory limit in MB
     * @param timeout timeout in seconds
     * @param minInstances minimum instances (e.g., warm start)
     * @param extensions extensions, e.g. function architecture
     * @param identity the identity from {@code @Function(identity)}; empty for one of the function's own
     * @param handlerClassName simple name of the handler class
     * @param handlerFullyQualifiedName fully qualified name of the handler
     * class
     * @param handlerPackage package name of the handler class
     * @param handlerMethod method name implementing the handler
     * @param inputType input type (possibly generic)
     * @param outputType output type (possibly generic)
     * @param artifactId Maven artifactId of the module
     * @param version Maven version of the module
     * @param classifier optional classifier
     * @param targetDir build output directory
     */
    public HandlerInfo(
            String functionId,
            int memory,
            int timeout,
            int minInstances,
            Extension[] extensions,
            String identity,
            String handlerClassName,
            String handlerFullyQualifiedName,
            String handlerPackage,
            String handlerMethod,
            String inputType,
            String outputType,
            String artifactId,
            String version,
            String classifier,
            String targetDir
    ) {
        this.functionId = functionId;
        this.memory = memory;
        this.timeout = timeout;
        this.minInstances = minInstances;
        this.extensions = extensions;
        this.identity = identity == null ? "" : identity;
        this.handlerClassName = handlerClassName;
        this.handlerFullyQualifiedName = handlerFullyQualifiedName;
        this.handlerPackage = handlerPackage;
        this.handlerMethod = handlerMethod;
        this.inputType = inputType;
        this.outputType = outputType;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.targetDir = targetDir;
    }

    /**
     * @return the function ID
     */
    public String getFunctionId() {
        return functionId.toLowerCase();
    }

    /**
     * The identity the function runs as: the value of {@code @Function(identity)}, or the
     * function ID if none is given, so that every function without one has its own.
     *
     * @return the identity name
     */
    public String getIdentity() {
        return identity.isEmpty() ? getFunctionId() : identity;
    }

    /**
     * @return {@code true} if the identity was given in {@code @Function(identity)}
     */
    public boolean isSharedIdentity() {
        return !identity.isEmpty();
    }

    /**
     * The configuration properties the function reads with {@code @ConfigProperty}.
     *
     * @return the properties, in parameter order
     */
    public java.util.List<ConfigPropertyInfo> getConfigProperties() {
        return configProperties;
    }

    /**
     * @param configProperties the configuration properties the function reads
     */
    public void setConfigProperties(java.util.List<ConfigPropertyInfo> configProperties) {
        this.configProperties = configProperties == null ? java.util.List.of() : java.util.List.copyOf(configProperties);
    }

    /**
     * The queues the function sends to through {@code @Queue} parameters.
     *
     * @return the queues, in parameter order
     */
    public java.util.List<QueueInfo> getPublishedQueues() {
        return publishedQueues;
    }

    /**
     * @param publishedQueues the queues the function sends to
     */
    public void setPublishedQueues(java.util.List<QueueInfo> publishedQueues) {
        this.publishedQueues = publishedQueues == null ? java.util.List.of() : java.util.List.copyOf(publishedQueues);
    }

    /**
     * The buckets the function reads or writes through {@code @Bucket} parameters.
     *
     * @return the buckets, in parameter order
     */
    public java.util.List<BucketInfo> getBuckets() {
        return buckets;
    }

    /**
     * @param buckets the buckets the function reads or writes
     */
    public void setBuckets(java.util.List<BucketInfo> buckets) {
        this.buckets = buckets == null ? java.util.List.of() : java.util.List.copyOf(buckets);
    }

    /**
     * The tables the function reads or writes through {@code @Table} parameters.
     *
     * @return the tables, in parameter order
     */
    public java.util.List<TableInfo> getTables() {
        return tables;
    }

    /**
     * @param tables the tables the function reads or writes
     */
    public void setTables(java.util.List<TableInfo> tables) {
        this.tables = tables == null ? java.util.List.of() : java.util.List.copyOf(tables);
    }

    /**
     * @return the configured memory (MB)
     */
    public int getMemory() {
        return memory;
    }

    /**
     * @return the configured timeout (seconds)
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @return the configured minimum number of instances
     */
    public int getMinInstances() {
        return minInstances;
    }

    /**
     * @return the handler class name (simple name)
     */
    public String getHandlerClassName() {
        return handlerClassName;
    }

    /**
     * @return the fully qualified handler class name
     */
    public String getHandlerFullyQualifiedName() {
        return handlerFullyQualifiedName;
    }

    /**
     * @return the handler package name
     */
    public String getHandlerPackage() {
        return handlerPackage;
    }

    /**
     * @return the method name that handles the request
     */
    public String getHandlerMethod() {
        return handlerMethod;
    }

    /**
     * @return the handler input type (with generics if present)
     */
    public String getInputType() {
        return inputType;
    }

    /**
     * @return the handler output type (with generics if present)
     */
    public String getOutputType() {
        return outputType;
    }

    /**
     * @return the artifactId of the module containing the function
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @return the version of the module containing the function
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the classifier (if any) of the artifact
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * @return the build output directory
     */
    public String getTargetDir() {
        return targetDir;
    }

    /**
     * @return the extensions as map 
     */
    public Map<String, String> getExtensionsMap() {
        return Arrays.asList(extensions).stream().collect(Collectors.toMap(extension -> extension.name(), extension -> extension.value()));
    }

    /**
     * Sets the fully qualified name of the generated wrapper class. Typically
     * used by generators when wrapping the handler for runtime platforms.
     *
     * @param wrapperFullyQualifiedName name to assign
     */
    public void setWrapperClassName(String wrapperFullyQualifiedName) {
        this.wrapperFullyQualifiedName = handlerPackage + "." + wrapperFullyQualifiedName;
        System.err.println("setWrapperClassName " + this.wrapperFullyQualifiedName);
    }

    /**
     * @return the fully qualified wrapper class name, if set
     */
    public String getWrapperFullyQualifiedName() {
        System.err.println("getWrapperFullyQualifiedName " + wrapperFullyQualifiedName);
        return wrapperFullyQualifiedName;
    }

    /**
     * @return the raw input type without generic arguments (for imports)
     */
    public String getInputTypeImport() {
        return stripGenericArguments(inputType);
    }

    /**
     * @return the raw output type without generic arguments (for imports)
     */
    public String getOutputTypeImport() {
        return stripGenericArguments(outputType);
    }

    /**
     * The input type as a class literal for Java code, e.g. {@code com.example.Order.class}.
     * A parameterized type has no class literal of its own ({@code Map<String, Object>.class} does not
     * compile), so it is the literal of the raw type, cast to the parameterized one:
     * {@code (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class}.
     *
     * @return a Java expression of type {@code Class<input type>}
     */
    public String getInputClassLiteral() {
        String raw = stripGenericArguments(inputType);
        if (raw.equals(inputType)) {
            return inputType + ".class";
        }
        return "(Class<" + inputType + ">) (Class<?>) " + raw + ".class";
    }

    private String stripGenericArguments(String type) {
        int genericStart = type.indexOf('<');
        if (genericStart != -1) {
            return type.substring(0, genericStart);
        }
        return type;
    }
}
