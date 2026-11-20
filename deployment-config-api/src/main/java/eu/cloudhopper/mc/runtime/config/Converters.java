package eu.cloudhopper.mc.runtime.config;

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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * The converters of a configuration: registered ones by priority, then the built-in ones, then
 * the implicit ones MicroProfile Config defines (static {@code of}, {@code valueOf} or
 * {@code parse} method, or a constructor, taking a {@code String}).
 */
class Converters {

    private record Registered(int priority, Converter<?> converter) {
    }

    private final Map<Class<?>, Registered> registered = new ConcurrentHashMap<>();
    private final Map<Class<?>, Converter<?>> resolved = new ConcurrentHashMap<>();

    void add(Converter<?> converter) {
        Class<?> type = convertedType(converter.getClass());
        if (type == null) {
            throw new IllegalArgumentException("Cannot tell the type converter " + converter.getClass().getName()
                    + " converts to; register it with ConfigBuilder.withConverter(Class, int, Converter)");
        }
        add(type, priority(converter.getClass()), converter);
    }

    void add(Class<?> type, int priority, Converter<?> converter) {
        registered.merge(boxed(type), new Registered(priority, converter),
                (old, now) -> now.priority() >= old.priority() ? now : old);
        resolved.remove(boxed(type));
    }

    @SuppressWarnings("unchecked")
    <T> Optional<Converter<T>> get(Class<T> type) {
        Class<?> key = boxed(type);
        Converter<?> converter = resolved.computeIfAbsent(key, this::resolve);
        return converter == NONE ? Optional.empty() : Optional.of((Converter<T>) converter);
    }

    private static final Converter<Object> NONE = value -> null;

    private Converter<?> resolve(Class<?> type) {
        Registered custom = registered.get(type);
        if (custom != null) {
            return custom.converter();
        }
        Converter<?> builtIn = builtIn(type);
        if (builtIn != null) {
            return builtIn;
        }
        Converter<?> implicit = implicit(type);
        return implicit != null ? implicit : NONE;
    }

    private static Converter<?> builtIn(Class<?> type) {
        if (type == String.class) {
            return value -> value;
        }
        if (type == Boolean.class) {
            return value -> switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "true", "1", "yes", "y", "on" -> Boolean.TRUE;
                default -> Boolean.FALSE;
            };
        }
        if (type == Integer.class) {
            return value -> Integer.valueOf(value.trim());
        }
        if (type == Long.class) {
            return value -> Long.valueOf(value.trim());
        }
        if (type == Short.class) {
            return value -> Short.valueOf(value.trim());
        }
        if (type == Byte.class) {
            return value -> Byte.valueOf(value.trim());
        }
        if (type == Double.class) {
            return value -> Double.valueOf(value.trim());
        }
        if (type == Float.class) {
            return value -> Float.valueOf(value.trim());
        }
        if (type == Character.class) {
            return value -> {
                if (value.length() != 1) {
                    throw new IllegalArgumentException("Not a single character: " + value);
                }
                return value.charAt(0);
            };
        }
        if (type == Class.class) {
            return value -> {
                try {
                    return Class.forName(value.trim(), true, Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("No class " + value, e);
                }
            };
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Converter<?> implicit(Class<?> type) {
        if (type.isEnum()) {
            return value -> Enum.valueOf((Class) type, value.trim());
        }
        for (String name : List.of("of", "valueOf", "parse")) {
            try {
                Method method = type.getMethod(name, String.class);
                if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                    return value -> invoke(() -> method.invoke(null, value));
                }
            } catch (NoSuchMethodException e) {
                // next candidate
            }
            try {
                Method method = type.getMethod(name, CharSequence.class);
                if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                    return value -> invoke(() -> method.invoke(null, value));
                }
            } catch (NoSuchMethodException e) {
                // next candidate
            }
        }
        try {
            Constructor<?> constructor = type.getConstructor(String.class);
            return value -> invoke(() -> constructor.newInstance(value));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private interface Call {
        Object call() throws ReflectiveOperationException;
    }

    private static Object invoke(Call call) {
        try {
            return call.call();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Splits a list value at unescaped commas; {@code \,} is a literal comma.
     */
    static List<String> splitList(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length() && value.charAt(i + 1) == ',') {
                current.append(',');
                i++;
            } else if (c == ',') {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "boolean" -> Boolean.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    /**
     * The value of {@code jakarta.annotation.Priority} (or {@code javax.annotation.Priority}) on a
     * converter, 100 without it; read by name, so no annotation API is needed at runtime.
     */
    private static int priority(Class<?> type) {
        for (java.lang.annotation.Annotation annotation : type.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (name.equals("jakarta.annotation.Priority") || name.equals("javax.annotation.Priority")) {
                try {
                    return (int) annotation.annotationType().getMethod("value").invoke(annotation);
                } catch (ReflectiveOperationException e) {
                    return 100;
                }
            }
        }
        return 100;
    }

    private static Class<?> convertedType(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Type generic : c.getGenericInterfaces()) {
                if (generic instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == Converter.class
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> converted) {
                    return converted;
                }
            }
        }
        return null;
    }
}
