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
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.MethodHandlerInfo;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.BODY;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.CONTEXT;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.HEADER;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.QUERY;
import java.util.HashSet;
import javax.annotation.processing.Messager;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
@SupportedAnnotationTypes("eu.cloudhopper.mc.annotations.Function")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ServerlessFunctionProcessor extends BaseDeploymentInfoProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private TypeMirror contextType;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.typeUtils = env.getTypeUtils();
        this.elementUtils = env.getElementUtils();
        this.contextType = elementUtils
                .getTypeElement("eu.cloudhopper.mc.runtime.HandlerContext")
                .asType();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annoTypes, RoundEnvironment round) {
        if (deploymentGenerator == null) {
            return true;
        }
        for (Element e : round.getElementsAnnotatedWith(Function.class)) {

            ExecutableElement m = (ExecutableElement) e;
            TypeElement cls = (TypeElement) m.getEnclosingElement();
            Function fn = m.getAnnotation(Function.class);
            HttpTrigger http = m.getAnnotation(HttpTrigger.class);
            ScheduledTrigger sched = m.getAnnotation(ScheduledTrigger.class);

            List<ParamInfo> params = new ArrayList<>();
            for (VariableElement p : m.getParameters()) {
                TypeMirror tm = p.asType();
                String name = p.getSimpleName().toString();
                if (typeUtils.isSameType(tm, contextType)) {
                    params.add(new ParamInfo(CONTEXT, null, tm.toString(), name));
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
                } else if (p.getAnnotation(HeaderParam.class) != null) {
                    params.add(new ParamInfo(HEADER,
                            p.getAnnotation(HeaderParam.class).value(),
                            tm.toString(), name));
                } else {
                    params.add(new ParamInfo(BODY, null, tm.toString(), name));
                }
            }

            if (!validateParams(m, params)) {
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
                    bodyType, returnType, params
            );
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
                    fn.name(), fn.memory(), fn.timeout(), fn.minInstances(), fn.extensions(),
                    adapterSimpleName,
                    adapterFQN, pkg,
                    m.getSimpleName().toString(),
                    bodyType, returnType,
                    artifactId, version, classifier, targetDir
            );
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
            } catch (ConfigGenerationException cex) {
                processingEnv.getMessager().printMessage(ERROR,
                        cex.getMessage(), m);
            }
        }

        if (round.processingOver()) {
            try {
                deploymentGenerator.finalizeConfig(generatorId, configOutputDir);
            } catch (ConfigGenerationException e) {
                processingEnv.getMessager().printMessage(ERROR, e.getMessage());
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
