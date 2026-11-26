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
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.PathParam;
import eu.cloudhopper.mc.runtime.HttpResponse;
import eu.cloudhopper.mc.runtime.HttpStatusException;
import java.util.Map;

public class HttpStatusFunction {

    // shares an identity, so that the TCK deployment on GCP stays within the quota of service
    // accounts created per minute
    @HttpTrigger(
            summary = "Answer with a status",
            description = "Answers with the status, headers and body the case names.",
            operationId = "httpStatus",
            path = "/http-status/{case}",
            method = "GET"
    )
    @Function(name = "httpStatus", identity = "tck-shared")
    public HttpResponse<Object> answer(@PathParam("case") String which) {
        if ("not-found".equals(which)) {
            throw new HttpStatusException(404, "no such item \"x\"");
        }
        if ("unauthorized".equals(which)) {
            throw new HttpStatusException(401, "token expired").header("WWW-Authenticate", "Bearer");
        }
        if ("fail".equals(which)) {
            throw new IllegalStateException("internal detail 42");
        }
        if ("created".equals(which)) {
            return HttpResponse.<Object>status(201, Map.of("id", "x"))
                    .header("Location", "/items/x")
                    .header("X-Cloudhopper-Test", "created");
        }
        if ("empty".equals(which)) {
            return HttpResponse.noContent();
        }
        if ("csv".equals(which)) {
            return HttpResponse.<Object>ok("a,b\n1,2\n").header("Content-Type", "text/csv");
        }
        return HttpResponse.ok(Map.of("case", which));
    }
}
