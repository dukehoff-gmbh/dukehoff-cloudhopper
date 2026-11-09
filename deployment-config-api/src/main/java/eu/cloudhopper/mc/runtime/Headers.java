package eu.cloudhopper.mc.runtime;

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


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the header map passed to {@link CloudRequestHandler}: HTTP header names are
 * case-insensitive, so lookups such as {@code headers.get("x-request-id")} must match
 * {@code X-Request-Id} whatever spelling the platform delivers.
 */
public final class Headers {

    private Headers() {
    }

    /**
     * Returns a case-insensitive, read-only copy of single-valued headers.
     *
     * @param headers the headers as delivered by the platform, may be {@code null}
     * @return a case-insensitive map, empty if {@code headers} is {@code null}
     */
    public static Map<String, String> of(Map<String, String> headers) {
        TreeMap<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name != null && value != null) {
                    result.putIfAbsent(name, value);
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns a case-insensitive, read-only copy of multi-valued headers, keeping the first value
     * of each header.
     *
     * @param headers the headers as delivered by the platform, may be {@code null}
     * @return a case-insensitive map, empty if {@code headers} is {@code null}
     */
    public static Map<String, String> ofFirstValues(Map<String, List<String>> headers) {
        TreeMap<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (name != null && values != null && !values.isEmpty() && values.get(0) != null) {
                    result.putIfAbsent(name, values.get(0));
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }
}
