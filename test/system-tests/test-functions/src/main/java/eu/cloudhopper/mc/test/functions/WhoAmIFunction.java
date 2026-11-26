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

import eu.cloudhopper.mc.annotations.ClaimParam;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.runtime.Claims;

public class WhoAmIFunction {

    // shares an identity, so that the TCK deployment on GCP stays within the quota of service
    // accounts created per minute
    @HttpTrigger(
            summary = "Caller of a protected route",
            description = "Answers with claims of the caller's token; needs the scope tck:read.",
            operationId = "whoAmI",
            path = "/whoami",
            method = "GET",
            authenticated = true,
            scopes = {"tck:read"}
    )
    @Function(name = "whoAmI", identity = "tck-shared")
    public String whoAmI(@ClaimParam("sub") String subject, @ClaimParam("level") Integer level, Claims claims) {
        return subject + ":" + level + ":" + claims.getString("custom:tenant");
    }
}
