package eu.cloudhopper.mc.test.support;

/*-
 * #%L
 * test-support - a library from the "Cloudhopper" project.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the resources a generated Terraform configuration expects to exist: for
 * {@code @EventTrigger(create = false)} the generators read the queue or bucket with a
 * {@code data} source instead of creating it, and a TCK binding creates it beforehand.
 */
public final class TerraformDataSources {

    private TerraformDataSources() {
    }

    /**
     * The values of {@code attribute} in all {@code data "<type>"} blocks of the {@code *.tf}
     * files in {@code dir}, e.g. {@code names(dir, "aws_sqs_queue", "name")}.
     *
     * @param dir       the directory with the generated Terraform
     * @param type      the data source type
     * @param attribute the attribute that names the resource
     * @return the names, in file order, without duplicates
     * @throws IOException if a file cannot be read
     */
    public static List<String> names(Path dir, String type, String attribute) throws IOException {
        Pattern block = Pattern.compile("data\\s+\"" + Pattern.quote(type) + "\"\\s+\"[^\"]+\"\\s*\\{([^}]*)\\}");
        Pattern value = Pattern.compile("(?m)^\\s*" + Pattern.quote(attribute) + "\\s*=\\s*\"([^\"$]+)\"");
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".tf")).sorted().toList();
        }
        for (Path file : files) {
            Matcher blocks = block.matcher(Files.readString(file));
            while (blocks.find()) {
                Matcher name = value.matcher(blocks.group(1));
                if (name.find() && !names.contains(name.group(1))) {
                    names.add(name.group(1));
                }
            }
        }
        return names;
    }
}
