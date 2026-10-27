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

import java.util.List;
import java.util.Map;

public class GeneratorConfig {
    private String generatorId;
    private Map<String, List<TemplateDescriptor>> phases;

    public GeneratorConfig() {
        // needed for Jackson
    }

    public String getGeneratorId() {
        return generatorId;
    }

    public void setGeneratorId(String generatorId) {
        this.generatorId = generatorId;
    }

    public Map<String, List<TemplateDescriptor>> getPhases() {
        return phases;
    }

    public void setPhases(Map<String, List<TemplateDescriptor>> phases) {
        this.phases = phases;
    }

    @Override
    public String toString() {
        return "GeneratorConfig{" +
                "generatorId='" + generatorId + '\'' +
                ", phases=" + phases +
                '}';
    }
}
