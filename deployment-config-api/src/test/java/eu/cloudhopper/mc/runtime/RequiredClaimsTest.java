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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequiredClaimsTest {

    private static final Claims ACCESS = new Claims(Map.of("sub", "u", "token_use", "access",
            "cognito:groups", List.of("admins", "users")));
    private static final Claims ID = new Claims(Map.of("sub", "u", "token_use", "id"));

    @Test
    void aTokenWithoutTheRequiredClaimIsRefused() {
        assertNull(Authenticator.missingRequiredClaim(ACCESS, "token_use=access"));
        assertEquals("claim token_use is not access", Authenticator.missingRequiredClaim(ID, "token_use=access"));
        assertEquals("claim tenant is not acme", Authenticator.missingRequiredClaim(ACCESS, "tenant=acme"));
    }

    @Test
    void aListClaimMustContainTheValue() {
        assertNull(Authenticator.missingRequiredClaim(ACCESS, " token_use = access , cognito:groups=admins"));
        assertEquals("claim cognito:groups is not ops", Authenticator.missingRequiredClaim(ACCESS, "cognito:groups=ops"));
    }

    @Test
    void nothingRequiredByDefault() {
        assertNull(Authenticator.missingRequiredClaim(ID, ""));
        assertThrows(IllegalStateException.class, () -> Authenticator.missingRequiredClaim(ID, "token_use"));
    }
}
