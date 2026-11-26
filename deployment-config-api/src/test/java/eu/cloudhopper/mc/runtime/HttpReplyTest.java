package eu.cloudhopper.mc.runtime;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class HttpReplyTest {

    private static final Function<Object, String> JSON = o -> o instanceof String s ? "\"" + s + "\"" : String.valueOf(o);

    @Test
    void plainResultIsJsonWithStatus200() {
        HttpReply reply = HttpReply.of("pong", JSON);
        assertEquals(200, reply.status());
        assertEquals("\"pong\"", reply.body());
        assertEquals("application/json", reply.headers().get("content-type"));
        assertFalse(reply.isUnexpected());
    }

    @Test
    void responseKeepsStatusAndHeaders() {
        HttpReply reply = HttpReply.of(HttpResponse.status(201, Map.of("id", 7)).header("Location", "/items/7"), JSON);
        assertEquals(201, reply.status());
        assertEquals("/items/7", reply.headers().get("location"));
        assertEquals("application/json", reply.contentType());
        assertEquals("{id=7}", reply.body());
    }

    @Test
    void responseWithoutBodyHasNoContent() {
        HttpReply reply = HttpReply.of(HttpResponse.noContent(), JSON);
        assertEquals(204, reply.status());
        assertNull(reply.body());
        assertNull(reply.contentType());
    }

    @Test
    void textOfAnotherMediaTypeIsSentAsItIs() {
        HttpReply reply = HttpReply.of(HttpResponse.ok("a,b\n1,2\n").header("Content-Type", "text/csv"), JSON);
        assertEquals("a,b\n1,2\n", reply.body());
        assertEquals("text/csv", reply.contentType());
        HttpReply problem = HttpReply.of(HttpResponse.status(409, "x")
                .header("content-type", "application/problem+json; charset=utf-8"), JSON);
        assertEquals("\"x\"", problem.body());
        assertEquals("application/problem+json; charset=utf-8", problem.contentType());
    }

    @Test
    void statusExceptionKeepsStatusHeadersAndMessage() {
        HttpReply reply = HttpReply.of(new HttpStatusException(401, "token \"x\" expired")
                .header("WWW-Authenticate", "Bearer"));
        assertEquals(401, reply.status());
        assertEquals("Bearer", reply.headers().get("www-authenticate"));
        assertEquals("{\"error\":\"token \\\"x\\\" expired\"}", reply.body());
        assertFalse(reply.isUnexpected());
    }

    @Test
    void otherExceptionsHideTheirMessage() {
        HttpReply reply = HttpReply.of(new IllegalStateException("password=secret"));
        assertEquals(500, reply.status());
        assertEquals("{\"error\":\"Internal Server Error\"}", reply.body());
        assertTrue(reply.isUnexpected());
    }

    @Test
    void invalidBodyIs400() {
        assertEquals(400, HttpReply.invalidBody().status());
    }

    @Test
    void invalidStatusesAndHeadersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new HttpStatusException(302, "x"));
        assertThrows(IllegalArgumentException.class, () -> HttpResponse.status(100));
        assertThrows(IllegalArgumentException.class, () -> HttpResponse.ok("x").header("X-A", "a\r\nX-B: b"));
        assertThrows(IllegalArgumentException.class, () -> HttpResponse.ok("x").header("Bad Name", "a"));
    }
}
