package eu.cloudhopper.mc.provider.springboot.publisher;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.PublishException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Message bodies as the queue triggers read them: a String as it is, anything else as JSON. */
final class MessageBodies {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageBodies() {
    }

    static String of(Object message, String queue) {
        if (message instanceof String text) {
            return text;
        }
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new PublishException("Cannot serialize the message for queue " + queue, e);
        }
    }
}
