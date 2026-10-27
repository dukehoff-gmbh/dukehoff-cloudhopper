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


import java.util.Map;

/**
 * Utility methods for converting String parameters from path, query, or headers
 * into typed values, with validation.
 */
public final class ParamConverters {
    private ParamConverters() {}

    public static String toString(Map<String, String> map, String name) {
        return map.get(name);
    }

    public static Integer toInteger(Map<String, String> map, String name) {
        String raw = map.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "Invalid integer parameter '" + name + "': '" + raw + "'", ex);
        }
    }

    public static Long toLong(Map<String, String> map, String name) {
        String raw = map.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "Invalid long parameter '" + name + "': '" + raw + "'", ex);
        }
    }

    public static Double toDouble(Map<String, String> map, String name) {
        String raw = map.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "Invalid double parameter '" + name + "': '" + raw + "'", ex);
        }
    }

    public static Boolean toBoolean(Map<String, String> map, String name) {
        String raw = map.get(name);
        if (raw == null) {
            return null;
        }
        // Boolean.valueOf handles any case, no exception
        return Boolean.valueOf(raw);
    }
}
