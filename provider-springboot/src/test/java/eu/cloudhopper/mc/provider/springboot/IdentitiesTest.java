package eu.cloudhopper.mc.provider.springboot;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentitiesTest {

    @Test
    void withoutAnIdentityTheInstanceServesAllFunctions() {
        assertTrue(Identities.serves(null, ""));
        assertTrue(Identities.serves(null, "connector"));
    }

    @Test
    void anInstanceServesOnlyTheFunctionsOfItsIdentity() {
        assertTrue(Identities.serves("connector", "connector"));
        assertFalse(Identities.serves("connector", ""));
        assertFalse(Identities.serves("connector", "reader"));
        // the default unit: the functions without an identity
        assertTrue(Identities.serves("", ""));
        assertTrue(Identities.serves("", null));
        assertFalse(Identities.serves("", "connector"));
    }

    @Test
    void aFunctionOfAnotherIdentityIsNotFound() {
        assertEquals(404, Identities.notServed().getStatusCode().value());
    }
}
