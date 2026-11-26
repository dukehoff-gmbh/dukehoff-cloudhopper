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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON into {@code Map}, {@code List}, {@code String}, {@code Long}, {@code Double},
 * {@code Boolean} and {@code null}: enough for tokens, key sets and discovery documents, without a
 * JSON library on the classpath of every function.
 */
final class JsonReader {

    private static final int MAX_DEPTH = 32;

    private final String text;
    private int pos;

    private JsonReader(String text) {
        this.text = text;
    }

    /**
     * @param text JSON text
     * @return the value
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    static Object read(String text) {
        JsonReader reader = new JsonReader(text);
        Object value = reader.value(0);
        reader.whitespace();
        if (reader.pos != text.length()) {
            throw reader.error("unexpected content after the value");
        }
        return value;
    }

    /**
     * @param text JSON text of an object
     * @return the object
     * @throws IllegalArgumentException if the text is not a JSON object
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(String text) {
        Object value = read(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Not a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("nested too deeply");
        }
        whitespace();
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("unexpected character");
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        pos++;
        whitespace();
        if (peek('}')) {
            pos++;
            return Collections.unmodifiableMap(result);
        }
        while (true) {
            whitespace();
            if (!peek('"')) {
                throw error("expected a name");
            }
            String name = string();
            whitespace();
            expect(':');
            if (result.containsKey(name)) {
                // a token with two "exp" or "aud" claims is ambiguous
                throw error("duplicate name " + name);
            }
            result.put(name, value(depth + 1));
            whitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                return Collections.unmodifiableMap(result);
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> result = new ArrayList<>();
        pos++;
        whitespace();
        if (peek(']')) {
            pos++;
            return Collections.unmodifiableList(result);
        }
        while (true) {
            result.add(value(depth + 1));
            whitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                return Collections.unmodifiableList(result);
            }
        }
    }

    private String string() {
        pos++;
        StringBuilder result = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return result.toString();
            }
            if (c < 0x20) {
                throw error("control character in a string");
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char escape = text.charAt(pos++);
            switch (escape) {
                case '"', '\\', '/' -> result.append(escape);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("incomplete escape");
                    }
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = Character.digit(text.charAt(pos + i), 16);
                        if (digit < 0) {
                            throw error("invalid escape");
                        }
                        code = code * 16 + digit;
                    }
                    result.append((char) code);
                    pos += 4;
                }
                default -> throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private static final java.util.regex.Pattern NUMBER =
            java.util.regex.Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    private Object number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String number = text.substring(start, pos);
        java.util.regex.Matcher matcher = NUMBER.matcher(number);
        if (!matcher.matches()) {
            throw error("invalid number");
        }
        if (matcher.group(2) == null && matcher.group(3) == null) {
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException e) {
                // larger than a long
            }
        }
        return Double.parseDouble(number);
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("unexpected character");
        }
        pos += word.length();
        return value;
    }

    private void whitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid JSON at " + pos + ": " + message);
    }
}
