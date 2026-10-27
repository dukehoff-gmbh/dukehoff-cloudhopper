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
import java.util.Map;
import static javassist.bytecode.stackmap.TypeData.ArrayType.typeName;
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
            BODY, PATH, QUERY, HEADER, CONTEXT
        }

        private final ParamType type;
        private final String name;      // e.g. path/query/header key
        private final String typeName;  // fully-qualified Java type
        private final String varName;   // local variable name in generated code

        public ParamInfo(ParamType type, String name, String typeName, String varName) {
            this.type = type;
            this.name = name;
            this.typeName = typeName;
            this.varName = varName;
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

        public MethodHandlerInfo(String packageName,
                String handlerClassName,
                String serviceClassName,
                String methodName,
                String bodyType,
                String returnType,
                List<ParamInfo> params) {
            this.packageName = packageName;
            this.handlerClassName = handlerClassName;
            this.serviceClassName = serviceClassName;
            this.methodName = methodName;
            this.bodyType = bodyType;
            this.returnType = returnType;
            this.params = params;
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
            String[] args = inner.split(",");
            ClassName rawName = ClassName.bestGuess(raw.trim());
            TypeName[] typeArgs = new TypeName[args.length];
            for (int i = 0; i < args.length; i++) {
                typeArgs[i] = toTypeName(args[i].trim());
            }
            return ParameterizedTypeName.get(rawName, typeArgs);
        }

        // fall back to reference type lookup (including boxed types)
        return ClassName.bestGuess(fqn);
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

        // handleRequest method
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("handleRequest")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addParameter(bodyType, "_body")
                .addParameter(ParameterizedTypeName.get(
                        ClassName.get(Map.class), ClassName.get(String.class), ClassName.get(String.class)
                ), "_path")
                .addParameter(ParameterizedTypeName.get(
                        ClassName.get(Map.class), ClassName.get(String.class), ClassName.get(String.class)
                ), "_query")
                .addParameter(ClassName.bestGuess("eu.cloudhopper.mc.runtime.HandlerContext"), "_ctx");

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
            }
        }

        String args = info.getParams().stream()
                .map(p -> p.getType() == ParamInfo.ParamType.BODY ? "_body" : p.getVarName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if ("void".equals(info.getReturnType())) {
            methodBuilder.addStatement("delegate.$L($L)", info.getMethodName(), args);
            methodBuilder.addStatement("return null");
        } else {
            methodBuilder.addStatement("return delegate.$L($L)", info.getMethodName(), args);
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
