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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The claims of the caller's verified token on a route with
 * {@code @HttpTrigger(authenticated = true)}. Declare a parameter of this type to read them, or
 * bind single claims with {@link eu.cloudhopper.mc.annotations.ClaimParam}:
 *
 * <pre>{@code
 * public List<Item> list(Claims claims) {
 *     String tenant = claims.getString("custom:tenant");
 *     ...
 * }
 * }</pre>
 *
 * <p>Values are {@code String}, {@code Long}, {@code Double}, {@code Boolean}, {@code List} and
 * {@code Map}, as in the token.</p>
 */
public final class Claims {

    private final Map<String, Object> values;

    /**
     * @param values the claims of a verified token
     */
    public Claims(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    /**
     * @return the subject ({@code sub}), the caller's ID at the provider
     */
    public String getSubject() {
        return getString("sub");
    }

    /**
     * @return the issuer ({@code iss})
     */
    public String getIssuer() {
        return getString("iss");
    }

    /**
     * @param name the claim name
     * @return the value, or {@code null} if the token does not have the claim
     */
    public Object get(String name) {
        return values.get(name);
    }

    /**
     * @param name the claim name
     * @return the value as text, or {@code null}
     */
    public String getString(String name) {
        Object value = values.get(name);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * @param name the claim name
     * @return the value as a number, or {@code null}
     * @throws IllegalArgumentException if the value is not a whole number
     */
    public Long getLong(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                // reported below
            }
        }
        throw new IllegalArgumentException("Claim " + name + " is not a whole number");
    }

    /**
     * @param name the claim name
     * @return the value as a number, or {@code null}
     * @throws IllegalArgumentException if the value is not a whole number in the range of int
     */
    public Integer getInteger(String name) {
        Long value = getLong(name);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Claim " + name + " is out of the range of int");
        }
        return value.intValue();
    }

    /**
     * @param name the claim name
     * @return the value as a number, or {@code null}
     * @throws IllegalArgumentException if the value is not a number
     */
    public Double getDouble(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                // reported below
            }
        }
        throw new IllegalArgumentException("Claim " + name + " is not a number");
    }

    /**
     * @param name the claim name
     * @return the value as a boolean ({@code true} or the text {@code "true"}), or {@code null}
     */
    public Boolean getBoolean(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        return value instanceof Boolean b ? b : Boolean.valueOf("true".equalsIgnoreCase(String.valueOf(value)));
    }

    /**
     * Reads a claim with several values: a JSON array, or text separated by spaces (as in
     * {@code scope}).
     *
     * @param name the claim name
     * @return the values, empty if the token does not have the claim
     */
    public List<String> getStringList(String name) {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            list.forEach(v -> result.add(String.valueOf(v)));
            return Collections.unmodifiableList(result);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
    }

    /**
     * @return the scopes of the token, from {@code scope} or {@code scp}
     */
    public List<String> getScopes() {
        List<String> scopes = new ArrayList<>(getStringList("scope"));
        scopes.addAll(getStringList("scp"));
        return Collections.unmodifiableList(scopes);
    }

    /**
     * @return all claims
     */
    public Map<String, Object> asMap() {
        return values;
    }

    @Override
    public String toString() {
        return "Claims" + Arrays.asList(values.keySet().toArray());
    }
}
