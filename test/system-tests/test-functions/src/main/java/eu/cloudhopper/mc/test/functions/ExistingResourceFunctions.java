package eu.cloudhopper.mc.test.functions;

/*-
 * #%L
 * demo - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.annotations.EventTrigger;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.runtime.StorageEvent;
import eu.cloudhopper.mc.test.domain.Player;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event functions on a queue and a bucket that exist before the deployment
 * ({@code create = false}). TCK bindings of targets that claim {@code create} create them
 * beforehand; the other targets ignore the attribute and create them with the function.
 */
public class ExistingResourceFunctions {

    public static final String NAME = "dukehoff-cloudhopper-tck-existing";

    @Function(name = "existingqueue")
    @EventTrigger(source = EventSource.QUEUE, name = NAME, create = false)
    public void onPlayer(Player player) {
        // plain concatenation: not every platform's log capture applies java.util.logging parameters
        Logger.getLogger(ExistingResourceFunctions.class.getName()).log(Level.INFO,
                "Existing queue message received: " + player.getName());
    }

    @Function(name = "existingupload")
    @EventTrigger(source = EventSource.STORAGE, name = NAME, create = false)
    public void onUpload(StorageEvent event) {
        Logger.getLogger(ExistingResourceFunctions.class.getName()).log(Level.INFO,
                "Existing bucket object: " + event.getBucket() + "/" + event.getKey());
    }
}
