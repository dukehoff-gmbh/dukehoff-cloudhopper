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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;

/**
 * The types a function reads and writes as JSON: its body, its result, the messages of its
 * queues ({@code Publisher<T>}), and the types these contain (fields, record components, getters, type arguments,
 * superclasses). A native image has to register them for reflection, since Jackson binds them
 * reflectively. Types of the JDK and of Cloudhopper itself are not included, but their type
 * arguments are ({@code List<Item>}, {@code Publisher<Order>}).
 */
final class BindingTypes {

    // the JDK, and the API of Cloudhopper (HandlerContext, Publisher, HttpResponse, ...); not its users' types,
    // which may live in packages below eu.cloudhopper.mc too
    private static final List<String> EXCLUDED = List.of("java.", "javax.", "jdk.", "sun.",
            "eu.cloudhopper.mc.runtime.", "eu.cloudhopper.mc.annotations.");

    private final Elements elements;
    private final Set<String> names = new LinkedHashSet<>();
    private final Set<String> visited = new java.util.HashSet<>();

    BindingTypes(Elements elements) {
        this.elements = elements;
    }

    /**
     * @param type a type the function binds
     * @return this
     */
    BindingTypes add(TypeMirror type) {
        walk(type);
        return this;
    }

    /**
     * @return the binary names (e.g. {@code com.example.Order$Item}) of the types found
     */
    List<String> names() {
        return new ArrayList<>(names);
    }

    private void walk(TypeMirror type) {
        if (type == null) {
            return;
        }
        switch (type.getKind()) {
            case ARRAY -> walk(((ArrayType) type).getComponentType());
            case WILDCARD -> {
                walk(((WildcardType) type).getExtendsBound());
                walk(((WildcardType) type).getSuperBound());
            }
            case TYPEVAR -> walk(((TypeVariable) type).getUpperBound());
            case DECLARED -> declared((DeclaredType) type);
            default -> {
                // primitives, void, errors: nothing to bind
            }
        }
    }

    private void declared(DeclaredType type) {
        type.getTypeArguments().forEach(this::walk);
        TypeElement element = (TypeElement) type.asElement();
        String name = element.getQualifiedName().toString();
        if (!visited.add(name) || EXCLUDED.stream().anyMatch(name::startsWith)) {
            return;
        }
        names.add(elements.getBinaryName(element).toString());
        walk(element.getSuperclass());
        for (Element member : element.getEnclosedElements()) {
            if (member.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (member.getKind() == ElementKind.FIELD) {
                walk(member.asType());
            } else if (member.getKind() == ElementKind.RECORD_COMPONENT) {
                walk(((RecordComponentElement) member).getAccessor().getReturnType());
            } else if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.PUBLIC)) {
                ExecutableElement method = (ExecutableElement) member;
                String methodName = method.getSimpleName().toString();
                if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID
                        && (methodName.startsWith("get") || methodName.startsWith("is"))) {
                    walk(method.getReturnType());
                } else if (method.getParameters().size() == 1 && methodName.startsWith("set")) {
                    walk(method.getParameters().get(0).asType());
                }
            }
        }
    }
}
