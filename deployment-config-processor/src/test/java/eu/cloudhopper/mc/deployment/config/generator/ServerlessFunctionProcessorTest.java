package eu.cloudhopper.mc.deployment.config.generator;

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
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerlessFunctionProcessorTest {

    @Test
    public void generatesAdapterAndConfig() throws IOException {
        JavaFileObject src = JavaFileObjects.forSourceString(
                "com.example.MyService",
                ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class MyService {\n"
                + "@Function(name=\"hello\")\n"
                + "@HttpTrigger(method=\"GET\", path=\"/hello/{id}\")\n"
                + "  public String greet(@PathParam(\"id\") String id, String body) {\n"
                + "    return id + \":\" + body;\n"
                + "  }\n"
                + "}\n"
        );

        // Tell Javac to export com.sun.tools.javac.* packages
        Compilation compilation = javac()
                .withOptions(
                        "--add-modules", "jdk.compiler",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                )
                .withProcessors(new ServerlessFunctionProcessor())
                .compile(src);

        assertThat(compilation).succeededWithoutWarnings();

        Optional<JavaFileObject> gen = compilation.generatedSourceFile(
                "com.example.MyService_greet_Handler"
        );
        assertTrue(gen.isPresent(), "Expected handler class to be generated");

        String generated = gen.get().getCharContent(true).toString();
        // basic sanity checks on the generated text:
        assertTrue(generated.contains("class MyService_greet_Handler"),
                "Generated class declaration missing:\n" + generated);
        assertTrue(generated.contains("implements CloudRequestHandler<String, String>"),
                "Missing interface implementation:\n" + generated);
        assertTrue(generated.contains("String id = _path.get(\"id\")"),
                "Path extraction missing:\n" + generated);
        assertTrue(generated.contains("return delegate.greet(id, _body)"),
                "Invocation mismatch:\n" + generated);
    }
}
