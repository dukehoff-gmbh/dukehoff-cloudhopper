package eu.cloudhopper.mc.demo;

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
import eu.cloudhopper.mc.annotations.Extension;
import eu.cloudhopper.mc.annotations.ExtensionKeys;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.RequestBody;

public class ApiFunction {

    @HttpTrigger(
            description = "dummy description",
            operationId = "helloworld2",
            method = "POST",
            path = "/hello2/{id}",
            summary = "bla"
    )
    @Function(
            name = "hello_world_2",
            extensions = {
                @Extension(
                        name = ExtensionKeys.Aws.Architecture.KEY,
                        value = ExtensionKeys.Aws.Architecture.Values.ARM64
                )
            })
    public String handleRequest(@RequestBody Integer input) {
        return "Hallo " + input;
    }

}
