package eu.cloudhopper.mc.generator.api;

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

/**
 * Represents the distinct phases of the deployment configuration generation process.
 * <p>
 * Each phase defines a step in the overall workflow, where specific parts of the configuration
 * are generated. Generators can use these phases to organize their processing logic and
 * ensure that configuration artifacts are created in the correct order.
 * <p>
 * The available phases are:
 * <ul>
 *   <li><b>FUNCTION</b> – Generates the serverless function configuration.</li>
 *   <li><b>API</b> – Generates the API resources and integrations.</li>
 *   <li><b>SCHEDULE</b> – Generates scheduled/cron triggers.</li>
 *   <li><b>FINALIZE</b> – Runs final configuration steps.</li>
 *   <li><b>SHARED</b> – Generates shared resources during the function phase.</li>
 * </ul>
 */
public enum GenerationPhase {
    FUNCTION("function", "Generates the serverless function configuration"), 
    API("api", "Generates the API resources and integrations"), 
    SCHEDULE("schedule", "Generates scheduled/cron triggers"),
    FINALIZE("finalize", "Runs final configuration steps"),
    SHARED("shared", "Generates shared resources in the \"function\" phase");

    private final String id;
    private final String description;

    /**
     * Creates a new generation phase with the specified identifier and description.
     *
     * @param id the unique identifier for the phase
     * @param description a brief description of the phase's purpose
     */
    GenerationPhase(String id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Returns the unique identifier for the generation phase.
     *
     * @return the phase identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Returns a brief description of the generation phase.
     *
     * @return the phase description
     */
    public String getDescription() {
        return description;
    }
}
