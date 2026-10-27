package eu.cloudhopper.mc.generator.generic.internal;

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
import eu.cloudhopper.mc.generator.generic.internal.GeneratorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class GeneratorConfigLoader {

    public static GeneratorConfig loadGeneratorConfig(ProcessingEnvironment processingEnv, String fileName) {
        System.err.println("Load generator config");

        Messager messager = processingEnv.getMessager();

        String expectedId = processingEnv.getOptions().getOrDefault("generatorId", "").trim();
        if (expectedId.isEmpty()) {
            System.err.println("No generatorId specified");

            messager.printMessage(Diagnostic.Kind.WARNING, "Missing required compiler arg: -AgeneratorId=<id>");
            return null;
        }
        System.err.println("Required generatorId "+expectedId);
        Enumeration<URL> resources;
        try {
            resources = GeneratorConfigLoader.class.getClassLoader().getResources(fileName);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to read resources named " + fileName + ": " + e.getMessage());
            return null;
        }
        System.err.println("Processing resources");
        List<GeneratorConfig> matchingConfigs = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream in = url.openStream()) {
                GeneratorConfig config = objectMapper.readValue(in, GeneratorConfig.class);
                System.err.println("Found generator config for "+config.getGeneratorId());
                if (expectedId.equals(config.getGeneratorId())) {
                    matchingConfigs.add(config);
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Error deserializing " + url + ": " + e.getMessage());
            }
        }

        if (matchingConfigs.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "No " + fileName + " found with generatorId=" + expectedId);
            return null;
        }
        if (matchingConfigs.size() > 1) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Multiple " + fileName + " files found with generatorId=" + expectedId + ":\n"
                    + matchingConfigs.stream()
                            .map(GeneratorConfig::toString)
                            .collect(Collectors.joining("\n")));
            return null;
        }

        GeneratorConfig selectedConfig = matchingConfigs.get(0);
        messager.printMessage(Diagnostic.Kind.NOTE,
                "Using " + fileName + " with generatorId=" + selectedConfig.getGeneratorId());
        return selectedConfig;
    }
}
