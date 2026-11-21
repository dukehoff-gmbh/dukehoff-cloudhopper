package eu.cloudhopper.mc.generator.api;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratorFeatureInfo {

    private String generatorId;
    private List<FeatureEntry> features = new ArrayList<>();

    public String getGeneratorId() {
        return generatorId;
    }

    public void setGeneratorId(String generatorId) {
        this.generatorId = generatorId;
    }

    public List<FeatureEntry> getFeatures() {
        return features;
    }

    public void setFeatures(List<FeatureEntry> features) {
        this.features = features;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeatureEntry {
        private String supportedAnnotation;
        private List<String> supportedAttributes;
        private Map<String, List<String>> supportedValues = new LinkedHashMap<>();
        private String runtimeModule;
        private String runtimeClass;

        /**
         * @return a class of the runtime module, to check that it is on the classpath, or {@code null}
         */
        public String getRuntimeClass() {
            return runtimeClass;
        }

        /**
         * @param runtimeClass a class of the runtime module, or {@code null}
         */
        public void setRuntimeClass(String runtimeClass) {
            this.runtimeClass = runtimeClass;
        }

        /**
         * @return the runtime module the feature needs ({@code groupId:artifactId}), or {@code null}
         */
        public String getRuntimeModule() {
            return runtimeModule;
        }

        /**
         * @param runtimeModule the runtime module the feature needs, or {@code null}
         */
        public void setRuntimeModule(String runtimeModule) {
            this.runtimeModule = runtimeModule;
        }

        public String getSupportedAnnotation() {
            return supportedAnnotation;
        }

        public void setSupportedAnnotation(String supportedAnnotation) {
            this.supportedAnnotation = supportedAnnotation;
        }

        public List<String> getSupportedAttributes() {
            return supportedAttributes;
        }

        public void setSupportedAttributes(List<String> supportedAttributes) {
            this.supportedAttributes = supportedAttributes;
        }

        /**
         * The supported values of attributes that are restricted to some values; an attribute
         * without an entry supports every value.
         *
         * @return attribute name to supported values
         */
        public Map<String, List<String>> getSupportedValues() {
            return supportedValues;
        }

        public void setSupportedValues(Map<String, List<String>> supportedValues) {
            this.supportedValues = supportedValues == null ? new LinkedHashMap<>() : supportedValues;
        }

        /**
         * Whether a value of a supported attribute is supported.
         *
         * @param attribute the attribute name
         * @param value     the value, for enums the constant name
         * @return {@code true} if the attribute is supported and has no restriction, or the restriction lists the value
         */
        public boolean supportsValue(String attribute, String value) {
            if (supportedAttributes == null || !supportedAttributes.contains(attribute)) {
                return false;
            }
            List<String> values = supportedValues.get(attribute);
            return values == null || values.contains(value);
        }
    }

    public static class Loader {
        public static GeneratorFeatureInfo loadFeaturesFor(String generatorId) {
            String filename = "META-INF/cloudhopper/" + generatorId + "-features.json";
            System.err.println("Load Features for " + generatorId);

            try (InputStream in = GeneratorFeatureInfo.class.getClassLoader().getResourceAsStream(filename)) {
                if (in == null) {
                    System.err.println("Could not find " + filename);
                    return null;
                }
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(in, GeneratorFeatureInfo.class);
            } catch (IOException e) {
                System.err.println("Could not read " + filename);
                e.printStackTrace();
                return null;
            }
        }
    }
}
