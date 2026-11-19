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
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A request body and a result of parameterized types, and a declared checked exception: both made
 * the generated code fail to compile on some targets (#65, #66).
 */
public class SubmitFormFunction {

    @HttpTrigger(operationId = "submitForm", path = "/form", method = "POST")
    @Function(name = "submitForm")
    public Map<String, Object> submit(Map<String, List<String>> form) throws IOException {
        if (form == null || form.containsKey("fail")) {
            throw new IOException("form rejected");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", form.size());
        result.put("tags", form.get("tags"));
        return result;
    }
}
