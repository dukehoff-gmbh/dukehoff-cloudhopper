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
import eu.cloudhopper.mc.runtime.CloudRequestHandler;
import eu.cloudhopper.mc.runtime.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // The tests below compile the generated handler and call it: a check of the generated text
    // alone did not notice that @HeaderParam produced code that does not compile (#13).

    @Test
    public void headerParamsCompileAndAreMatchedCaseInsensitively() throws Exception {
        CloudRequestHandler<Void, String> handler = compileAndLoad("com.example.HeaderService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class HeaderService {\n"
                + "  @Function(name=\"headers\")\n"
                + "  @HttpTrigger(method=\"GET\", path=\"/headers\")\n"
                + "  public String echo(@HeaderParam(\"X-Test-Header\") String text,\n"
                + "                     @HeaderParam(\"X-Count\") Integer count) {\n"
                + "    return text + \":\" + count;\n"
                + "  }\n"
                + "}\n",
                "com.example.HeaderService_echo_Handler");

        Map<String, String> headers = Headers.of(Map.of("x-test-header", "hello", "X-COUNT", "3"));
        assertEquals("hello:3", handler.handleRequest(null, Map.of(), Map.of(), headers, null));
        // the variant without headers still works and passes none
        assertEquals("null:null", handler.handleRequest(null, Map.of(), Map.of(), null));
    }

    @Test
    public void allParameterKindsCompileAndAreBound() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.AllService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.HandlerContext;\n"
                + "public class AllService {\n"
                + "  @Function(name=\"all\")\n"
                + "  @HttpTrigger(method=\"POST\", path=\"/items/{id}\")\n"
                + "  public String all(@PathParam(\"id\") Long id, @QueryParam(\"flag\") boolean flag,\n"
                + "                    @QueryParam String q, @HeaderParam(\"X-Ratio\") Double ratio,\n"
                + "                    HandlerContext ctx, String body) {\n"
                + "    return id + \"|\" + flag + \"|\" + q + \"|\" + ratio + \"|\" + (ctx == null) + \"|\" + body;\n"
                + "  }\n"
                + "}\n",
                "com.example.AllService_all_Handler");

        String result = handler.handleRequest("payload", Map.of("id", "42"), Map.of("flag", "true", "q", "x"),
                Headers.of(Map.of("x-ratio", "0.5")), null);
        assertEquals("42|true|x|0.5|true|payload", result);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CloudRequestHandler<I, O> compileAndLoad(String sourceName, String source,
                                                                   String handlerClass) throws Exception {
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
                .compile(JavaFileObjects.forSourceString(sourceName, source));
        assertThat(compilation).succeeded();

        Map<String, byte[]> classes = new HashMap<>();
        for (JavaFileObject file : compilation.generatedFiles()) {
            if (file.getKind() == JavaFileObject.Kind.CLASS) {
                String name = file.toUri().getPath();
                name = name.substring(name.indexOf(StandardLocation.CLASS_OUTPUT.getName()) + StandardLocation.CLASS_OUTPUT.getName().length() + 1);
                name = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                try (InputStream in = file.openInputStream()) {
                    classes.put(name, in.readAllBytes());
                }
            }
        }
        ClassLoader loader = new ClassLoader(ServerlessFunctionProcessorTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return (CloudRequestHandler<I, O>) loader.loadClass(handlerClass).getDeclaredConstructor().newInstance();
    }
}
