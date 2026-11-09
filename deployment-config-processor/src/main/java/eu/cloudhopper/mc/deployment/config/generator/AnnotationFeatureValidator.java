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
import eu.cloudhopper.mc.annotations.Extension;
import eu.cloudhopper.mc.annotations.Extensions;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.generator.api.GeneratorFeatureInfo;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reports Cloudhopper annotations and attributes on a function that the selected generator does
 * not support, as declared in its {@code <generatorId>-features.json}. Without this, a target
 * silently drops what it cannot handle, e.g. a {@code @ScheduledTrigger} on {@code springboot-http}.
 *
 * <p>Findings are warnings; with {@code -Acloudhopper.strictFeatureCheck=true} they are errors.
 * Vendor extensions ({@code @Function(extensions = ...)}, {@code @Extension}) are exempt: they are
 * meant for one target and ignored by the others.</p>
 */
public class AnnotationFeatureValidator {

    /** Processor option that turns findings into errors. */
    public static final String STRICT_OPTION = "cloudhopper.strictFeatureCheck";

    private static final String ANNOTATIONS_PACKAGE = Function.class.getPackageName() + ".";
    private static final Set<String> EXEMPT_ANNOTATIONS = Set.of(
            Extension.class.getName(), Extensions.class.getName());
    private static final String EXTENSIONS_ATTRIBUTE = "extensions";

    private final Messager messager;
    private final boolean strictMode;
    private final String generatorId;
    private final Map<String, Set<String>> supportedFeatures;

    public AnnotationFeatureValidator(ProcessingEnvironment processingEnv, GeneratorFeatureInfo generatorFeatureInfo) {
        this.messager = processingEnv.getMessager();
        this.strictMode = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault(STRICT_OPTION, "false"));
        this.generatorId = generatorFeatureInfo.getGeneratorId();
        this.supportedFeatures = generatorFeatureInfo.getFeatures().stream().collect(Collectors.toMap(
                GeneratorFeatureInfo.FeatureEntry::getSupportedAnnotation,
                f -> new HashSet<>(f.getSupportedAttributes())
        ));
    }

    /**
     * Checks the Cloudhopper annotations on a function method.
     *
     * @param element the method annotated with {@code @Function}
     * @return {@code true} if everything on it is supported
     */
    public boolean validate(Element element) {
        boolean supported = true;
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String annotation = mirror.getAnnotationType().asElement().toString();
            if (!annotation.startsWith(ANNOTATIONS_PACKAGE) || EXEMPT_ANNOTATIONS.contains(annotation)) {
                continue;
            }
            String simpleName = annotation.substring(ANNOTATIONS_PACKAGE.length());
            Set<String> supportedAttributes = supportedFeatures.get(annotation);

            if (supportedAttributes == null) {
                report(String.format("@%s is not supported by generator '%s'; it is ignored for this target.",
                        simpleName, generatorId), element, mirror, null);
                supported = false;
                continue;
            }

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                String attribute = entry.getKey().getSimpleName().toString();
                if (!EXTENSIONS_ATTRIBUTE.equals(attribute) && !supportedAttributes.contains(attribute)) {
                    report(String.format("Attribute '%s' of @%s is not supported by generator '%s'; it is ignored for this target.",
                            attribute, simpleName, generatorId), element, mirror, entry.getValue());
                    supported = false;
                }
            }
        }
        return supported;
    }

    private void report(String message, Element element, AnnotationMirror mirror, AnnotationValue value) {
        Diagnostic.Kind kind = strictMode ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
        if (value != null) {
            messager.printMessage(kind, message, element, mirror, value);
        } else {
            messager.printMessage(kind, message, element, mirror);
        }
    }
}
