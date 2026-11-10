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

import eu.cloudhopper.mc.annotations.EventSource;
import eu.cloudhopper.mc.annotations.EventTrigger;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.test.domain.Player;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives players from a queue and logs them; the TCK sends a player with a unique name and
 * looks for that name in the logs.
 */
public class QueueMessageFunction {

    @Function(name = "queuemessage")
    @EventTrigger(source = EventSource.QUEUE, name = "cloudhopper-tck-players")
    public void onPlayer(Player player, EventMetadata meta) {
        // plain concatenation: not every platform's log capture applies java.util.logging parameters
        Logger.getLogger(QueueMessageFunction.class.getName()).log(Level.INFO,
                "Queue message received: " + player.getName()
                + " (attempt " + meta.getDeliveryAttempt() + ", id " + meta.getId() + ")");
    }
}
