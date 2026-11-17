package eu.cloudhopper.mc.test.tck.api;

/*-
 * #%L
 * test-tck-api - a library from the "Cloudhopper" project.
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

import java.util.List;
import java.util.Map;

/**
 * What a TCK test needs from a generator: an annotation, some of its attributes and, for
 * attributes a generator may restrict to some values, the values the test uses.
 *
 * @param annotationFqcn     the annotation type
 * @param requiredAttributes the attributes the test sets
 * @param requiredValues     attribute name to the values the test uses, e.g. {@code source} to {@code QUEUE}
 */
public record RequiredFeature(String annotationFqcn, List<String> requiredAttributes, Map<String, List<String>> requiredValues) {

    public RequiredFeature {
        requiredValues = requiredValues == null ? Map.of() : Map.copyOf(requiredValues);
    }

    /**
     * A feature without value requirements.
     *
     * @param annotationFqcn     the annotation type
     * @param requiredAttributes the attributes the test sets
     */
    public RequiredFeature(String annotationFqcn, List<String> requiredAttributes) {
        this(annotationFqcn, requiredAttributes, Map.of());
    }
}
