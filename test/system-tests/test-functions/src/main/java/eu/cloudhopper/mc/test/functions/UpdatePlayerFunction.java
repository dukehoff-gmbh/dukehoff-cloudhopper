package eu.cloudhopper.mc.test.functions;

/*-
 * #%L
 * test-functions - a library from the "Cloudhopper" project.
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


import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.test.domain.Player;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.PathParam;

public class UpdatePlayerFunction  {

    @HttpTrigger(
            summary = "Update an existing player",
            description = "Updates an existing player's information in the ping pong tournament database.",
            operationId = "updatePlayer",
            path = "/player/{id}",
            method = "PUT"
    )
    @Function(name = "updatePlayer")
    public Player handleRequest(Player input, @PathParam Integer id) {
        if (input == null || id == null) {
            throw new IllegalArgumentException("Player input and id must not be null");
        }

        Player updatedPlayer = new Player(
                id != 0 ? id : input.getId(), // If no id in path, fallback to input id
                input.getName() != null ? input.getName() : "Unknown",
                input.getRanking()
        );

        System.out.println("Updated player: " + updatedPlayer);
        return updatedPlayer;
    }
}
