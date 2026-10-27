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
import eu.cloudhopper.mc.generator.api.GeneratorFeatureInfo;
import eu.cloudhopper.mc.generator.generic.GenericDeploymentConfigGenerator;
import eu.cloudhopper.mc.generator.api.spi.DeploymentConfigGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 *
 * @author antonepple
 */
public abstract class BaseDeploymentInfoProcessor extends AbstractProcessor {

    protected List<DeploymentConfigGenerator> generators;
    protected DeploymentConfigGenerator deploymentGenerator;
    protected String configOutputDir = "target/generated-config";
    protected String artifactId;
    protected String classifier;
    protected String version;
    protected String targetDir;
    protected GeneratorFeatureInfo generatorFeatureInfo;
    protected String generatorId;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        Messager messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, "Initializing BaseDeploymentInfoProcessor");

        super.init(processingEnv);
        
        ServiceLoader<DeploymentConfigGenerator> loader = ServiceLoader.load(DeploymentConfigGenerator.class, ServerlessFunctionProcessor.class.getClassLoader());
        generators = new ArrayList<>();
        for (DeploymentConfigGenerator generator : loader) {
            generators.add(generator);
            System.err.println("Found generator "+ generator);
        }
        generatorId = processingEnv.getOptions().getOrDefault("generatorId", "").trim();
        configOutputDir = processingEnv.getOptions().getOrDefault("configOutputDir", "target/generated-config");
        artifactId = processingEnv.getOptions().getOrDefault("artifactId", "default-artifactId");
        classifier = processingEnv.getOptions().getOrDefault("classifier", "default-classifier");
        version = processingEnv.getOptions().getOrDefault("version", "default-version");
        targetDir = processingEnv.getOptions().getOrDefault("targetDir", "./target");

        if ("default-artifactId".equals(artifactId) || "default-classifier".equals(classifier) || "default-version".equals(version)) {
            messager.printMessage(Diagnostic.Kind.NOTE, "One or more required compiler arguments (artifactId, classifier, version) are using default values. Please ensure these are provided.");
        }
        deploymentGenerator = getDeploymentGenerator();
        generatorFeatureInfo = GeneratorFeatureInfo.Loader.loadFeaturesFor(deploymentGenerator.getGeneratorID());
    }

    protected DeploymentConfigGenerator getDeploymentGenerator() {
        DeploymentConfigGenerator deploymentGenerator = null;
        for (DeploymentConfigGenerator generator : generators) {
            if (generator.supportsGenerator(generatorId)) {
                deploymentGenerator = generator;
                break;
            }
        }
        // If no registered generator is found, try the GenericDeploymentConfigGenerator
        if (deploymentGenerator == null) {
            System.err.println("No custom generator found for " + generatorId);
            GenericDeploymentConfigGenerator genericDeploymentConfigGenerator = new GenericDeploymentConfigGenerator(processingEnv);
//            if (genericDeploymentConfigGenerator.supportsGenerator(cloudProvider)) {
//                System.err.println("GenericDeploymentConfigGenerator supports " + cloudProvider);
            deploymentGenerator = genericDeploymentConfigGenerator;
//            System.err.println("Using GenericDeploymentConfigGenerator");
//            } else {
//                System.err.println("GenericDeploymentConfigGenerator doesn't support " + cloudProvider);
//                processingEnv.getMessager().printError("No generator found for provider " + cloudProvider);
//            }
        }

        return deploymentGenerator;
    }

    protected boolean implementsInterface(TypeElement classElement, String interfaceName) {
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeElement requiredInterfaceElement = elementUtils.getTypeElement(interfaceName);
        if (requiredInterfaceElement == null) {
            System.err.println("Required interface not found: " + interfaceName);
            return false;
        }

        TypeMirror requiredInterface = requiredInterfaceElement.asType();
        TypeMirror rawRequiredInterface = typeUtils.erasure(requiredInterface);
        System.err.println("Created raw TypeMirror " + rawRequiredInterface.toString());

        for (TypeMirror implementedInterface : classElement.getInterfaces()) {
            TypeMirror rawImplementedInterface = typeUtils.erasure(implementedInterface);
            if (typeUtils.isSameType(rawImplementedInterface, rawRequiredInterface)) {
                return true;
            }
        }

        // Recursively check superclasses
        TypeMirror superclass = classElement.getSuperclass();
        if (superclass.getKind() != javax.lang.model.type.TypeKind.NONE) {
            return implementsInterface((TypeElement) typeUtils.asElement(superclass), interfaceName);
        }
        return false;
    }

    protected String getPackageName(TypeElement classElement) {
        String qualifiedName = classElement.getQualifiedName().toString();
        return qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

}
