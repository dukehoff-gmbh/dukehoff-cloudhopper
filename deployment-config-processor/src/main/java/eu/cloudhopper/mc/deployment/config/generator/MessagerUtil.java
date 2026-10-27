/*-
 * #%L
 * deployment-config-processor - a library from the "Cloudhopper" project.
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
package eu.cloudhopper.mc.deployment.config.generator;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 *
 * @author antonepple
 */
public class MessagerUtil {
    
        static void printExceptionStackTrace(Messager messager, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        // Use Messager to print the stack trace as an error
        messager.printMessage(Diagnostic.Kind.ERROR, stackTrace);
    }
    
}
