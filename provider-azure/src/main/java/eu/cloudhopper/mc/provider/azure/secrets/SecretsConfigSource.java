package eu.cloudhopper.mc.provider.azure.secrets;

/*-
 * #%L
 * provider-azure - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * The secrets of a function, ordinal 250: the deployment passes the references of the secrets the
 * function reads in the environment variable {@code CLOUDHOPPER_SECRETS}, a JSON object from
 * property name to the URI of a Key Vault secret (Terraform variable
 * {@code cloudhopper_secrets}). A value is read on first access and kept for the life of the
 * instance. Environment variables and system properties come first, e.g. for local runs.
 */
public class SecretsConfigSource implements ConfigSource {

    /** The environment variable with the references. */
    public static final String ENVIRONMENT = "CLOUDHOPPER_SECRETS";

    private static final int ORDINAL = 250;

    private final Map<String, String> references;
    private final Function<String, String> reader;
    private final Map<String, String> values = new ConcurrentHashMap<>();

    /** Reads the references from {@code CLOUDHOPPER_SECRETS} and the values from Key Vault. */
    public SecretsConfigSource() {
        this(parse(System.getenv(ENVIRONMENT)), id -> KeyVaultClient.fromEnvironment().getSecret(id));
    }

    SecretsConfigSource(Map<String, String> references, Function<String, String> reader) {
        this.references = references;
        this.reader = reader;
    }

    static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return Map.copyOf(new ObjectMapper().readValue(json, new TypeReference<Map<String, String>>() { }));
        } catch (IOException e) {
            throw new IllegalStateException(ENVIRONMENT + " is not a JSON object of property names and secrets", e);
        }
    }

    @Override
    public Set<String> getPropertyNames() {
        return references.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        String reference = references.get(propertyName);
        if (reference == null) {
            return null;
        }
        return values.computeIfAbsent(propertyName, name -> reader.apply(reference));
    }

    @Override
    public String getName() {
        return "Azure Key Vault";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
