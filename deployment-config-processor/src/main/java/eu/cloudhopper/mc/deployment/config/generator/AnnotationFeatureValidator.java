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
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationFeatureValidator {

    private final Messager messager;
    private final boolean strictMode;
    private final Map<String, Set<String>> supportedFeatures;

    public AnnotationFeatureValidator(ProcessingEnvironment processingEnv, GeneratorFeatureInfo generatorFeatureInfo) {
        this.messager = processingEnv.getMessager();
        this.strictMode = Boolean.parseBoolean(
                processingEnv.getOptions().getOrDefault("cloudhopper.strictFeatureCheck", "false")
        );

        // Build map: annotation FQN → allowed attributes
        this.supportedFeatures = generatorFeatureInfo.getFeatures().stream().collect(Collectors.toMap(
                f -> f.getSupportedAnnotation(),
                f -> new HashSet<>(f.getSupportedAttributes())
        ));
    }

    public void validate(Element element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String annotationFQN = mirror.getAnnotationType().asElement().toString();
            Set<String> supportedAttributes = supportedFeatures.get(annotationFQN);

            if (supportedAttributes == null) continue;

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                String usedAttribute = entry.getKey().getSimpleName().toString();

                if (!supportedAttributes.contains(usedAttribute)) {
                    String msg = String.format(
                        "Attribute '%s' in annotation %s is not supported by generator '%s'.",
                        usedAttribute, annotationFQN, getGeneratorId()
                    );

                    if (strictMode) {
                        messager.printMessage(Diagnostic.Kind.ERROR, msg, element, mirror, entry.getValue());
                    } else {
                        messager.printMessage(Diagnostic.Kind.WARNING, msg, element, mirror, entry.getValue());
                    }
                }
            }
        }
    }

    private String getGeneratorId() {
        return supportedFeatures.keySet().stream().findFirst().map(k -> {
            int dash = k.lastIndexOf('.');
            return dash > 0 ? k.substring(dash + 1) : k;
        }).orElse("unknown");
    }
}
