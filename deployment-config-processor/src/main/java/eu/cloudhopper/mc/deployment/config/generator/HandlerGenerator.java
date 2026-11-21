package eu.cloudhopper.mc.deployment.config.generator;

/*-
 * #%L
 * deployment-config-processor - a library from the "Cloudhopper" project.
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
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import javax.annotation.processing.Filer;

/**
 * Generates a CloudRequestHandler adapter class for a given MethodHandlerInfo.
 */
public class HandlerGenerator {

    /**
     * Information about a single parameter in the target method.
     */
    public static class ParamInfo {

        public enum ParamType {
            BODY, PATH, QUERY, HEADER, CONTEXT, EVENT_METADATA, CONFIG
        }

        private final ParamType type;
        private final String name;      // e.g. path/query/header key
        private final String typeName;  // fully-qualified Java type
        private final String varName;   // local variable name in generated code
        private final String defaultValue; // CONFIG: @ConfigProperty(defaultValue)

        public ParamInfo(ParamType type, String name, String typeName, String varName) {
            this(type, name, typeName, varName, null);
        }

        public ParamInfo(ParamType type, String name, String typeName, String varName, String defaultValue) {
            this.type = type;
            this.name = name;
            this.typeName = typeName;
            this.varName = varName;
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public ParamType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getVarName() {
            return varName;
        }
    }

    /**
     * Captures all necessary info to generate the adapter class.
     */
    public static class MethodHandlerInfo {

        private final String packageName;
        private final String handlerClassName;
        private final String serviceClassName;
        private final String methodName;
        private final String bodyType;
        private final String returnType;
        private final List<ParamInfo> params;
        private final boolean declaresExceptions;

        public MethodHandlerInfo(String packageName,
                String handlerClassName,
                String serviceClassName,
                String methodName,
                String bodyType,
                String returnType,
                List<ParamInfo> params) {
            this(packageName, handlerClassName, serviceClassName, methodName, bodyType, returnType, params, false);
        }

        /**
         * @param declaresExceptions whether the method has a {@code throws} clause; the handler then wraps
         *                           checked exceptions in {@code FunctionException}
         */
        public MethodHandlerInfo(String packageName,
                String handlerClassName,
                String serviceClassName,
                String methodName,
                String bodyType,
                String returnType,
                List<ParamInfo> params,
                boolean declaresExceptions) {
            this.packageName = packageName;
            this.handlerClassName = handlerClassName;
            this.serviceClassName = serviceClassName;
            this.methodName = methodName;
            this.bodyType = bodyType;
            this.returnType = returnType;
            this.params = params;
            this.declaresExceptions = declaresExceptions;
        }

        public boolean isDeclaresExceptions() {
            return declaresExceptions;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getHandlerClassName() {
            return handlerClassName;
        }

        public String getServiceClassName() {
            return serviceClassName;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getBodyType() {
            return bodyType;
        }

        public String getReturnType() {
            return returnType;
        }

        public List<ParamInfo> getParams() {
            return params;
        }
    }

    /**
     * The class for a {@code .class} literal: primitives boxed, type arguments removed.
     */
    static TypeName rawClassName(String fqn) {
        TypeName type = toTypeName(fqn);
        if (type.isPrimitive()) {
            return type.box();
        }
        if (type instanceof ParameterizedTypeName parameterized) {
            return parameterized.rawType();
        }
        return type;
    }

    private static TypeName toTypeName(String fqn) {
        switch (fqn) {
            case "byte":
                return TypeName.BYTE;
            case "short":
                return TypeName.SHORT;
            case "int":
                return TypeName.INT;
            case "long":
                return TypeName.LONG;
            case "float":
                return TypeName.FLOAT;
            case "double":
                return TypeName.DOUBLE;
            case "boolean":
                return TypeName.BOOLEAN;
            case "char":
                return TypeName.CHAR;
            case "void":    // use boxed Void so it works in generics
                return ClassName.get("java.lang", "Void");
        }

        int genericsStart = fqn.indexOf('<');
        if (genericsStart > 0 && fqn.endsWith(">")) {
            String raw = fqn.substring(0, genericsStart);
            String inner = fqn.substring(genericsStart + 1, fqn.length() - 1);
            List<String> args = splitTypeArguments(inner);
            ClassName rawName = ClassName.bestGuess(raw.trim());
            TypeName[] typeArgs = new TypeName[args.size()];
            for (int i = 0; i < args.size(); i++) {
                typeArgs[i] = toTypeName(args.get(i).trim());
            }
            return ParameterizedTypeName.get(rawName, typeArgs);
        }

        // fall back to reference type lookup (including boxed types)
        return ClassName.bestGuess(fqn);
    }

    /**
     * Splits type arguments at the commas of the top level only:
     * {@code String, List<Map<String, Integer>>} gives two arguments.
     */
    static List<String> splitTypeArguments(String arguments) {
        List<String> result = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(arguments.substring(start, i));
                start = i + 1;
            }
        }
        result.add(arguments.substring(start));
        return result;
    }

    /**
     * Generates a JavaFile for the adapter class, without writing to disk.
     */
    public static JavaFile generateHandlerFile(MethodHandlerInfo info) {
        // Determine type names
        TypeName bodyType = toTypeName(info.getBodyType());
        TypeName returnType = toTypeName(info.getReturnType());

        // CloudRequestHandler<I,O> interface
        ParameterizedTypeName cloudInterface = ParameterizedTypeName.get(
                ClassName.get("eu.cloudhopper.mc.runtime", "CloudRequestHandler"),
                bodyType,
                returnType
        );

        // Build the handler class
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(info.getHandlerClassName())
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(cloudInterface);

        // Delegate field
        ClassName serviceType = ClassName.bestGuess(info.getServiceClassName());
        FieldSpec delegateField = FieldSpec.builder(serviceType, "delegate", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", serviceType)
                .build();
        classBuilder.addField(delegateField);

        TypeName stringMap = ParameterizedTypeName.get(
                ClassName.get(Map.class), ClassName.get(String.class), ClassName.get(String.class));
        ClassName handlerContext = ClassName.get("eu.cloudhopper.mc.runtime", "HandlerContext");

        // handleRequest method
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("handleRequest")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addParameter(bodyType, "_body")
                .addParameter(stringMap, "_path")
                .addParameter(stringMap, "_query")
                .addParameter(stringMap, "_headers")
                .addParameter(handlerContext, "_ctx");

        // the variant without headers delegates with an empty map
        classBuilder.addMethod(MethodSpec.methodBuilder("handleRequest")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addParameter(bodyType, "_body")
                .addParameter(stringMap, "_path")
                .addParameter(stringMap, "_query")
                .addParameter(handlerContext, "_ctx")
                .addStatement("return handleRequest(_body, _path, _query, $T.emptyMap(), _ctx)", Collections.class)
                .build());

        // Parameter extraction
        for (ParamInfo p : info.getParams()) {
            String typeName = p.getTypeName();
            switch (p.getType()) {
                case BODY:
                    // already bound to _body
                    break;
                case PATH: {
                    if ("java.lang.String".equals(typeName)) {
                        // direct lookup for strings
                        methodBuilder.addStatement(
                                "$T $L = _path.get($S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                p.getName());
                    } else {
                        // use ParamConverters for primitives/boxed
                        String conv;
                        if ("java.lang.Integer".equals(typeName) || "int".equals(typeName)) {
                            conv = "toInteger";
                        } else if ("java.lang.Long".equals(typeName) || "long".equals(typeName)) {
                            conv = "toLong";
                        } else if ("java.lang.Double".equals(typeName) || "double".equals(typeName)) {
                            conv = "toDouble";
                        } else if ("java.lang.Boolean".equals(typeName) || "boolean".equals(typeName)) {
                            conv = "toBoolean";
                        } else {
                            throw new IllegalArgumentException(
                                    "Unsupported @PathParam type: " + typeName);
                        }
                        methodBuilder.addStatement(
                                "$T $L = $T.$L(_path, $S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                ClassName.get("eu.cloudhopper.mc.runtime", "ParamConverters"),
                                conv,
                                p.getName());
                    }
                    break;
                }

                case QUERY: {
                    if ("java.lang.String".equals(typeName)) {
                        methodBuilder.addStatement(
                                "$T $L = _query.get($S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                p.getName());
                    } else {
                        String conv;
                        if ("java.lang.Integer".equals(typeName) || "int".equals(typeName)) {
                            conv = "toInteger";
                        } else if ("java.lang.Long".equals(typeName) || "long".equals(typeName)) {
                            conv = "toLong";
                        } else if ("java.lang.Double".equals(typeName) || "double".equals(typeName)) {
                            conv = "toDouble";
                        } else if ("java.lang.Boolean".equals(typeName) || "boolean".equals(typeName)) {
                            conv = "toBoolean";
                        } else {
                            throw new IllegalArgumentException(
                                    "Unsupported @QueryParam type: " + typeName);
                        }
                        methodBuilder.addStatement(
                                "$T $L = $T.$L(_query, $S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                ClassName.get("eu.cloudhopper.mc.runtime", "ParamConverters"),
                                conv,
                                p.getName());
                    }
                    break;
                }

                case HEADER: {
                    if ("java.lang.String".equals(typeName)) {
                        methodBuilder.addStatement(
                                "$T $L = _headers.get($S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                p.getName());
                    } else {
                        String conv;
                        if ("java.lang.Integer".equals(typeName) || "int".equals(typeName)) {
                            conv = "toInteger";
                        } else if ("java.lang.Long".equals(typeName) || "long".equals(typeName)) {
                            conv = "toLong";
                        } else if ("java.lang.Double".equals(typeName) || "double".equals(typeName)) {
                            conv = "toDouble";
                        } else if ("java.lang.Boolean".equals(typeName) || "boolean".equals(typeName)) {
                            conv = "toBoolean";
                        } else {
                            throw new IllegalArgumentException(
                                    "Unsupported @HeaderParam type: " + typeName);
                        }
                        methodBuilder.addStatement(
                                "$T $L = $T.$L(_headers, $S)",
                                toTypeName(typeName),
                                p.getVarName(),
                                ClassName.get("eu.cloudhopper.mc.runtime", "ParamConverters"),
                                conv,
                                p.getName());
                    }
                    break;
                }

                case CONTEXT:
                    methodBuilder.addStatement("$T $L = _ctx",
                            toTypeName(p.getTypeName()), p.getVarName());
                    break;

                case EVENT_METADATA:
                    // null when the invocation is not an event (HTTP, schedule, direct call)
                    methodBuilder.addStatement("$T $L = _ctx == null ? null : _ctx.getEventMetadata()",
                            toTypeName(p.getTypeName()), p.getVarName());
                    break;

                case CONFIG: {
                    // read on every call: the configuration caches its sources, and a value can come
                    // from a source registered after the handler was created
                    ClassName configParams = ClassName.get("eu.cloudhopper.mc.runtime", "ConfigParams");
                    String method = "value";
                    String elementType = typeName;
                    if (typeName.startsWith("java.util.Optional<")) {
                        method = "optional";
                        elementType = typeName.substring("java.util.Optional<".length(), typeName.length() - 1);
                    } else if (typeName.startsWith("java.util.List<")) {
                        method = "values";
                        elementType = typeName.substring("java.util.List<".length(), typeName.length() - 1);
                    }
                    methodBuilder.addStatement("$T $L = $T.$L($S, $T.class, $S)",
                            toTypeName(typeName), p.getVarName(), configParams, method, p.getName(),
                            rawClassName(elementType),
                            "org.eclipse.microprofile.config.configproperty.unconfigureddvalue".equals(p.getDefaultValue())
                                    ? null : p.getDefaultValue());
                    break;
                }
            }
        }

        String args = info.getParams().stream()
                .map(p -> p.getType() == ParamInfo.ParamType.BODY ? "_body" : p.getVarName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        // CloudRequestHandler declares no checked exceptions: wrap those the method declares
        if (info.isDeclaresExceptions()) {
            methodBuilder.beginControlFlow("try");
        }
        if ("void".equals(info.getReturnType())) {
            methodBuilder.addStatement("delegate.$L($L)", info.getMethodName(), args);
            methodBuilder.addStatement("return null");
        } else {
            methodBuilder.addStatement("return delegate.$L($L)", info.getMethodName(), args);
        }
        if (info.isDeclaresExceptions()) {
            methodBuilder.nextControlFlow("catch ($T e)", RuntimeException.class)
                    .addStatement("throw e")
                    .nextControlFlow("catch ($T e)", Exception.class)
                    .addStatement("throw new $T(e)", ClassName.get("eu.cloudhopper.mc.runtime", "FunctionException"))
                    .endControlFlow();
        }
        classBuilder.addMethod(methodBuilder.build());

        return JavaFile.builder(info.getPackageName(), classBuilder.build())
                .build();
    }

    /**
     * Generate and write the adapter class into the provided Filer.
     */
    public static void generateHandler(MethodHandlerInfo info, Filer filer) throws Exception {
        JavaFile javaFile = generateHandlerFile(info);
        javaFile.writeTo(filer);
    }
}
