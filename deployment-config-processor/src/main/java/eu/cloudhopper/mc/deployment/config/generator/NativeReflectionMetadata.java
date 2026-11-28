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

import eu.cloudhopper.mc.annotations.Reflective;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * The reachability metadata of a native image for the types the application reads or writes as
 * JSON itself, beyond the functions' own types: the types marked with {@link Reflective} (and
 * their {@code types}), with the types they contain, and the classes of the packages named in
 * {@code -AnativeReflectionPackages} (only these classes). Each type gets a file of its own,
 * {@code META-INF/native-image/cloudhopper/types/<binary name>/reachability-metadata.json}, so
 * that a compilation of some sources leaves the files of the others as they are.
 */
final class NativeReflectionMetadata {

    static final String OPTION = "nativeReflectionPackages";

    private final ProcessingEnvironment env;
    private final Set<String> written = new HashSet<>();
    private boolean packagesDone;

    NativeReflectionMetadata(ProcessingEnvironment env) {
        this.env = env;
    }

    void process(RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(Reflective.class)) {
            if (element instanceof TypeElement type) {
                write(type, new BindingTypes(env.getElementUtils()).add(type.asType()).names());
                for (TypeMirror extra : declaredTypes(type)) {
                    if (extra instanceof DeclaredType declared && declared.asElement() instanceof TypeElement other) {
                        // named explicitly: registered even where the walk leaves types out (e.g. the JDK's)
                        List<String> types = new java.util.ArrayList<>(List.of(binaryName(other)));
                        new BindingTypes(env.getElementUtils()).add(other.asType()).names().stream()
                                .filter(t -> !types.contains(t)).forEach(types::add);
                        write(other, types);
                    }
                }
            }
        }
        if (!packagesDone) {
            // the first round only: later rounds contain the generated handlers
            packagesDone = true;
            List<String> packages = Arrays.stream(env.getOptions().getOrDefault(OPTION, "").split(","))
                    .map(String::trim).filter(p -> !p.isEmpty()).toList();
            if (!packages.isEmpty()) {
                Deque<Element> pending = new ArrayDeque<>(round.getRootElements());
                while (!pending.isEmpty()) {
                    if (!(pending.pop() instanceof TypeElement type)) {
                        continue;
                    }
                    String pkg = env.getElementUtils().getPackageOf(type).getQualifiedName().toString();
                    if (packages.stream().anyMatch(p -> pkg.equals(p) || pkg.startsWith(p + "."))) {
                        // only the class itself: the option is for types no signature reveals
                        write(type, List.of(binaryName(type)));
                    }
                    type.getEnclosedElements().stream().filter(e -> e instanceof TypeElement).forEach(pending::push);
                }
            }
        }
    }

    /** The {@code types} of the {@link Reflective} annotation of a type. */
    private List<TypeMirror> declaredTypes(TypeElement type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            if (!annotation.getAnnotationType().toString().equals(Reflective.class.getName())) {
                continue;
            }
            for (var entry : annotation.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("types")) {
                    @SuppressWarnings("unchecked")
                    List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) entry.getValue().getValue();
                    return values.stream().map(v -> (TypeMirror) v.getValue()).toList();
                }
            }
        }
        return List.of();
    }

    private String binaryName(TypeElement type) {
        return env.getElementUtils().getBinaryName(type).toString();
    }

    private void write(TypeElement type, List<String> types) {
        String name = binaryName(type);
        if (!written.add(name) || types.isEmpty()) {
            return;
        }
        StringBuilder json = new StringBuilder("{\n  \"comment\": \"Generated by Cloudhopper for ")
                .append(name).append(" (@Reflective or -A").append(OPTION).append(")\",\n  \"reflection\": [");
        String separator = "\n";
        for (String t : types) {
            json.append(separator).append("    {\"type\": \"").append(t)
                    .append("\", \"allDeclaredFields\": true, \"allDeclaredMethods\": true, \"allDeclaredConstructors\": true},\n")
                    .append("    {\"type\": \"").append(t).append("[]\"}");
            separator = ",\n";
        }
        json.append("\n  ]\n}\n");
        try {
            FileObject file = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/native-image/cloudhopper/types/" + name + "/reachability-metadata.json", type);
            try (Writer out = file.openWriter()) {
                out.write(json.toString());
            }
        } catch (IOException e) {
            env.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Cannot write the native image metadata of " + name + ": " + e.getMessage(), type);
        }
    }
}
