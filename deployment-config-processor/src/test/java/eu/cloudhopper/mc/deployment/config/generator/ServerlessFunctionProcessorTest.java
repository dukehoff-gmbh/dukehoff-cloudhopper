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
import eu.cloudhopper.mc.runtime.EventHandlerContext;
import eu.cloudhopper.mc.runtime.EventMetadata;
import eu.cloudhopper.mc.runtime.FunctionException;
import eu.cloudhopper.mc.runtime.HandlerContext;
import eu.cloudhopper.mc.runtime.StorageEvent;
import java.time.Instant;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void configPropertiesAreReadFromTheConfiguration() throws Exception {
        CloudRequestHandler<Void, String> handler = compileAndLoad("com.example.ConfigService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import java.util.*;\n"
                + "import org.eclipse.microprofile.config.inject.ConfigProperty;\n"
                + "public class ConfigService {\n"
                + "  @Function(name=\"config\")\n"
                + "  public String config(@ConfigProperty(name=\"svc.bucket\") String bucket,\n"
                + "      @ConfigProperty(name=\"svc.retries\", defaultValue=\"3\") int retries,\n"
                + "      @ConfigProperty(name=\"svc.unset\") Optional<String> unset,\n"
                + "      @ConfigProperty(name=\"svc.hosts\", defaultValue=\"a,b\") List<String> hosts) {\n"
                + "    return bucket + \"|\" + retries + \"|\" + unset.orElse(\"none\") + \"|\" + hosts;\n"
                + "  }\n"
                + "}\n",
                "com.example.ConfigService_config_Handler");

        System.setProperty("svc.bucket", "reports");
        try {
            assertEquals("reports|3|none|[a, b]", handler.handleRequest(null, Map.of(), Map.of(), null));
        } finally {
            System.clearProperty("svc.bucket");
        }
        java.util.NoSuchElementException missing = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class, () -> handler.handleRequest(null, Map.of(), Map.of(), null));
        assertTrue(missing.getMessage().contains("SVC_BUCKET"), missing.getMessage());
    }

    @Test
    public void queueParametersSendThroughThePublisherFactory() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.PublishService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.Publisher;\n"
                + "public class PublishService {\n"
                + "  @Function(name=\"publish\")\n"
                + "  public String publish(String item, @Queue(name=\"orders\") Publisher<String> orders) {\n"
                + "    orders.send(item); return \"sent\";\n"
                + "  }\n"
                + "  @Function(name=\"consume\")\n"
                + "  @EventTrigger(source=EventSource.QUEUE, name=\"orders\")\n"
                + "  public void consume(String item) { }\n"
                + "}\n",
                "com.example.PublishService_publish_Handler");

        java.util.List<String> sent = new java.util.ArrayList<>();
        eu.cloudhopper.mc.runtime.Publishers.setFactory(new eu.cloudhopper.mc.runtime.spi.PublisherFactory() {
            @Override
            public <T> eu.cloudhopper.mc.runtime.Publisher<T> queue(String name, boolean existing) {
                return new eu.cloudhopper.mc.runtime.Publisher<>() {
                    @Override
                    public void send(T message) {
                        sent.add(name + "|" + existing + "|" + message);
                    }

                    @Override
                    public void sendAll(java.util.Collection<? extends T> messages) {
                        messages.forEach(this::send);
                    }
                };
            }
        });
        try {
            assertEquals("sent", handler.handleRequest("book", Map.of(), Map.of(), null));
            assertEquals(java.util.List.of("orders|false|book"), sent);
        } finally {
            eu.cloudhopper.mc.runtime.Publishers.setFactory(null);
        }
    }

    @Test
    public void bucketParametersUseTheBlobStoreFactoryWithTheDeclaredAccess() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.BucketService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.annotations.Bucket.Access;\n"
                + "import eu.cloudhopper.mc.runtime.BlobStore;\n"
                + "import eu.cloudhopper.mc.runtime.BlobStoreException;\n"
                + "public class BucketService {\n"
                + "  @Function(name=\"copy\")\n"
                + "  public String copy(String key, @Bucket(name=\"reports\") BlobStore in,\n"
                + "      @Bucket(name=\"archive\", access=Access.READ_WRITE, existing=true) BlobStore out) {\n"
                + "    out.put(key, in.get(key).orElseThrow(), \"text/plain\");\n"
                + "    try { in.delete(key); return \"deleted\"; } catch (BlobStoreException e) { return e.getMessage(); }\n"
                + "  }\n"
                + "}\n",
                "com.example.BucketService_copy_Handler");

        java.util.Map<String, byte[]> objects = new java.util.LinkedHashMap<>();
        java.util.List<String> created = new java.util.ArrayList<>();
        eu.cloudhopper.mc.runtime.BlobStores.setFactory((name, existing) -> {
            created.add(name + "|" + existing);
            return new eu.cloudhopper.mc.runtime.BlobStore() {
                @Override
                public java.util.Optional<byte[]> get(String key) {
                    return java.util.Optional.ofNullable(objects.get(name + "/" + key));
                }

                @Override
                public void put(String key, byte[] content, String contentType) {
                    objects.put(name + "/" + key, content);
                }

                @Override
                public void delete(String key) {
                    objects.remove(name + "/" + key);
                }

                @Override
                public java.util.List<String> list(String prefix) {
                    return java.util.List.of();
                }
            };
        });
        try {
            objects.put("reports/a.txt", "A".getBytes());
            assertEquals("delete on bucket reports needs @Bucket(access = WRITE or READ_WRITE), the parameter declares READ",
                    handler.handleRequest("a.txt", Map.of(), Map.of(), null));
            assertEquals("A", new String(objects.get("archive/a.txt")));
            assertTrue(objects.containsKey("reports/a.txt"));
            assertEquals(java.util.List.of("reports|false", "archive|true"), created);
        } finally {
            eu.cloudhopper.mc.runtime.BlobStores.setFactory(null);
        }
    }

    @Test
    public void bucketParametersNeedTheBlobStoreTypeAndAValidName() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class WrongBucket {\n"
                + "  @Function(name=\"wrong\")\n"
                + "  public void f(@Bucket(name=NAME) TYPE b) { }\n"
                + "}\n";
        Compilation type = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongBucket",
                        source.replace("NAME", "\"reports\"").replace("TYPE", "String")));
        assertThat(type).hadErrorContaining("@Bucket on parameter 'b' of f needs the type BlobStore");

        Compilation name = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongBucket",
                        source.replace("NAME", "\"My_Reports\"").replace("TYPE", "eu.cloudhopper.mc.runtime.BlobStore")));
        assertThat(name).hadErrorContaining("@Bucket(name = \"My_Reports\") on f: use 3 to 63 lowercase letters");

        Compilation existing = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongBucket",
                        source.replace("NAME", "\"My_Reports\", existing=true").replace("TYPE", "eu.cloudhopper.mc.runtime.BlobStore")));
        assertThat(existing).succeeded();
    }

    @Test
    public void claimsNeedAnAuthenticatedRouteAndASupportedType() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class Claimed {\n"
                + "  @Function(name=\"me\")\n"
                + "  @HttpTrigger(method=\"GET\", path=\"/me\"AUTH)\n"
                + "  public String me(@ClaimParam(\"sub\") TYPE sub) { return null; }\n"
                + "}\n";
        Compilation public_ = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Claimed",
                        source.replace("AUTH", "").replace("TYPE", "String")));
        assertThat(public_).hadErrorContaining("reads the caller's token: the route needs @HttpTrigger(authenticated = true)");

        Compilation type = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Claimed",
                        source.replace("AUTH", ", authenticated=true").replace("TYPE", "java.util.Date")));
        assertThat(type).hadErrorContaining("@ClaimParam on parameter 'sub' of me needs the type String");

        Compilation ok = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Claimed",
                        source.replace("AUTH", ", scopes={\"a\", \"b\"}").replace("TYPE", "String")));
        assertThat(ok).succeeded();
        assertThat(ok).generatedSourceFile("com.example.Claimed_me_Handler")
                .contentsAsUtf8String().contains("Authenticator.authenticate(_headers, \"a\", \"b\")");
    }

    @Test
    public void aNativeImageHasOneArchitectureAndNoSnapStart() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class Arch {\n"
                + "  @Function(name=\"hello\", extensions=@Extension(name=ExtensionKeys.Aws.Architecture.KEY, value=\"x86_64\"))\n"
                + "  public String hello() { return null; }\n"
                + "  @Function(name=\"snap\", extensions=@Extension(name=ExtensionKeys.Aws.SnapStart.KEY, value=\"PublishedVersions\"))\n"
                + "  public String snap() { return null; }\n"
                + "}\n";
        Compilation arm = javac().withProcessors(new ServerlessFunctionProcessor())
                .withOptions("-Aruntime=native")
                .compile(JavaFileObjects.forSourceString("com.example.Arch", source));
        assertThat(arm).hadErrorContaining("Function hello asks for the architecture x86_64, but the native image is built for arm64");
        assertThat(arm).hadWarningContaining("Function snap: SnapStart is only available for the Java runtime");

        Compilation x86 = javac().withProcessors(new ServerlessFunctionProcessor())
                .withOptions("-Aruntime=native", "-AnativeArchitecture=x86_64")
                .compile(JavaFileObjects.forSourceString("com.example.Arch", source));
        assertThat(x86).succeeded();

        Compilation jvm = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Arch", source));
        assertThat(jvm).succeeded();
    }

    @Test
    public void bindingTypesAreTheTypesTheFunctionReadsAndWrites() {
        String source = ""
                + "package com.example;\n"
                + "import java.util.*;\n"
                + "public class Shop {\n"
                + "  public record Order(String customer, List<Item> items, Map<String, Note> notes) { }\n"
                + "  public record Item(String sku, Price price) { }\n"
                + "  public static class Price { private Currency currency; public Currency getCurrency() { return currency; } }\n"
                + "  public enum Currency { EUR }\n"
                + "  public static class Note { public Note[] replies; }\n"
                + "  public record Receipt(double total, Optional<Order> order) { }\n"
                + "  public static class Unused { }\n"
                + "  public Receipt order(Order order, eu.cloudhopper.mc.runtime.Publisher<Stock> stock) { return null; }\n"
                + "  public static class Stock { public int count; }\n"
                + "}\n";
        List<String> found = new java.util.ArrayList<>();
        javax.annotation.processing.AbstractProcessor collector = new javax.annotation.processing.AbstractProcessor() {
            @Override
            public java.util.Set<String> getSupportedAnnotationTypes() {
                return java.util.Set.of("*");
            }

            @Override
            public javax.lang.model.SourceVersion getSupportedSourceVersion() {
                return javax.lang.model.SourceVersion.latestSupported();
            }

            @Override
            public boolean process(java.util.Set<? extends javax.lang.model.element.TypeElement> annotations,
                    javax.annotation.processing.RoundEnvironment round) {
                javax.lang.model.element.TypeElement shop = processingEnv.getElementUtils().getTypeElement("com.example.Shop");
                if (shop != null && found.isEmpty()) {
                    javax.lang.model.element.ExecutableElement method = javax.lang.model.util.ElementFilter
                            .methodsIn(shop.getEnclosedElements()).get(0);
                    BindingTypes types = new BindingTypes(processingEnv.getElementUtils()).add(method.getReturnType());
                    method.getParameters().forEach(p -> types.add(p.asType()));
                    found.addAll(types.names());
                }
                return false;
            }
        };
        Compilation compilation = javac().withProcessors(collector)
                .compile(JavaFileObjects.forSourceString("com.example.Shop", source));
        assertThat(compilation).succeeded();
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("com.example.Shop$Receipt", "com.example.Shop$Order",
                "com.example.Shop$Item", "com.example.Shop$Price", "com.example.Shop$Currency", "com.example.Shop$Note",
                "com.example.Shop$Stock"), java.util.Set.copyOf(found));
    }

    @Test
    public void bucketParametersOfOneNameAgreeOnExisting() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.BlobStore;\n"
                + "public class TwoBuckets {\n"
                + "  @Function(name=\"writer\")\n"
                + "  public void w(@Bucket(name=\"reports\", access=Bucket.Access.WRITE) BlobStore b) { }\n"
                + "  @Function(name=\"reader\")\n"
                + "  public void r(@Bucket(name=\"reports\"EXISTING) BlobStore b) { }\n"
                + "}\n";
        Compilation conflict = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.TwoBuckets", source.replace("EXISTING", ", existing=true")));
        assertThat(conflict).hadErrorContaining("@Bucket(name = \"reports\") on r declares existing = true, but on w existing = false");

        Compilation same = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.TwoBuckets", source.replace("EXISTING", "")));
        assertThat(same).succeeded();
    }

    @Test
    public void tableParametersUseTheKeyValueStoreFactoryWithTheDeclaredAccess() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.TableService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.annotations.Table.Access;\n"
                + "import eu.cloudhopper.mc.runtime.KeyValueStore;\n"
                + "import eu.cloudhopper.mc.runtime.KeyValueStore.*;\n"
                + "import eu.cloudhopper.mc.runtime.KeyValueStoreException;\n"
                + "public class TableService {\n"
                + "  @Function(name=\"copy\")\n"
                + "  public String copy(String id, @Table(name=\"accounts\") KeyValueStore in,\n"
                + "      @Table(name=\"archive\", access=Access.READ_WRITE, existing=true) KeyValueStore out) {\n"
                + "    Key key = new Key(\"account:\" + id, \"profile\");\n"
                + "    out.put(in.get(key).orElseThrow());\n"
                + "    try { in.delete(key); return \"deleted\"; } catch (KeyValueStoreException e) { return e.getMessage(); }\n"
                + "  }\n"
                + "}\n",
                "com.example.TableService_copy_Handler");

        java.util.Map<String, java.util.Map<String, Object>> items = new java.util.LinkedHashMap<>();
        java.util.List<String> created = new java.util.ArrayList<>();
        eu.cloudhopper.mc.runtime.KeyValueStores.setFactory((name, existing) -> {
            created.add(name + "|" + existing);
            return new eu.cloudhopper.mc.runtime.KeyValueStore() {
                private String id(Key key) {
                    return name + "/" + key.partition() + "/" + key.sort();
                }

                @Override
                public java.util.Optional<Item> get(Key key) {
                    return java.util.Optional.ofNullable(items.get(id(key))).map(a -> new Item(key, a));
                }

                @Override
                public void put(Item item) {
                    items.put(id(item.key()), item.attributes());
                }

                @Override
                public boolean putIfAbsent(Item item) {
                    return items.putIfAbsent(id(item.key()), item.attributes()) == null;
                }

                @Override
                public boolean update(Key key, java.util.Map<String, Object> set, java.util.Map<String, Object> expected) {
                    return false;
                }

                @Override
                public void delete(Key key) {
                    items.remove(id(key));
                }

                @Override
                public Page query(String partition, String sortKeyPrefix, int limit, String pageToken) {
                    return new Page(java.util.List.of(), null);
                }

                @Override
                public void batchPut(java.util.Collection<Item> batch) {
                }

                @Override
                public void batchDelete(java.util.Collection<Key> keys) {
                }
            };
        });
        try {
            items.put("accounts/account:1/profile", Map.of("name", "Ada"));
            assertEquals("delete on table accounts needs @Table(access = WRITE or READ_WRITE), the parameter declares READ",
                    handler.handleRequest("1", Map.of(), Map.of(), null));
            assertEquals(Map.of("name", "Ada"), items.get("archive/account:1/profile"));
            assertTrue(items.containsKey("accounts/account:1/profile"));
            assertEquals(java.util.List.of("accounts|false", "archive|true"), created);
        } finally {
            eu.cloudhopper.mc.runtime.KeyValueStores.setFactory(null);
        }
    }

    @Test
    public void tableParametersNeedTheKeyValueStoreTypeAndAValidName() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class WrongTable {\n"
                + "  @Function(name=\"wrong\")\n"
                + "  public void f(@Table(name=NAME) TYPE t) { }\n"
                + "}\n";
        Compilation type = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongTable",
                        source.replace("NAME", "\"accounts\"").replace("TYPE", "String")));
        assertThat(type).hadErrorContaining("@Table on parameter 't' of f needs the type KeyValueStore");

        for (String invalid : java.util.List.of("user-accounts", "Accounts", "ab", "1accounts")) {
            Compilation name = javac().withProcessors(new ServerlessFunctionProcessor())
                    .compile(JavaFileObjects.forSourceString("com.example.WrongTable",
                            source.replace("NAME", "\"" + invalid + "\"").replace("TYPE", "eu.cloudhopper.mc.runtime.KeyValueStore")));
            assertThat(name).hadErrorContaining("@Table(name = \"" + invalid + "\") on f: use 3 to 63 lowercase letters and digits");
        }

        Compilation existing = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongTable",
                        source.replace("NAME", "\"user-accounts\", existing=true").replace("TYPE", "eu.cloudhopper.mc.runtime.KeyValueStore")));
        assertThat(existing).succeeded();
    }

    @Test
    public void tableParametersOfOneNameAgreeOnExisting() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.KeyValueStore;\n"
                + "public class TwoTables {\n"
                + "  @Function(name=\"writer\")\n"
                + "  public void w(@Table(name=\"accounts\", access=Table.Access.WRITE) KeyValueStore t) { }\n"
                + "  @Function(name=\"reader\")\n"
                + "  public void r(@Table(name=\"accounts\"EXISTING) KeyValueStore t) { }\n"
                + "}\n";
        Compilation conflict = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.TwoTables", source.replace("EXISTING", ", existing=true")));
        assertThat(conflict).hadErrorContaining("@Table(name = \"accounts\") on r declares existing = true, but on w existing = false");

        Compilation same = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.TwoTables", source.replace("EXISTING", "")));
        assertThat(same).succeeded();
    }

    @Test
    public void queuesNeedAConsumerInTheProjectOrExisting() {
        String source = ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.Publisher;\n"
                + "public class Lonely {\n"
                + "  @Function(name=\"lonely\")\n"
                + "  public void f(@Queue(name=\"nowhere\"EXISTING) Publisher<String> q) { }\n"
                + "}\n";
        Compilation missing = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Lonely", source.replace("EXISTING", "")));
        assertThat(missing).hadErrorContaining("@Queue(name = \"nowhere\") on f: no function of the project consumes this queue");

        Compilation existing = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Lonely", source.replace("EXISTING", ", existing=true")));
        assertThat(existing).succeeded();
    }

    @Test
    public void queueParametersNeedThePublisherType() {
        Compilation compilation = javac().withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.WrongType", ""
                        + "package com.example;\n"
                        + "import eu.cloudhopper.mc.annotations.*;\n"
                        + "public class WrongType {\n"
                        + "  @Function(name=\"wrong\")\n"
                        + "  public void f(@Queue(name=\"orders\", existing=true) String q) { }\n"
                        + "}\n"));
        assertThat(compilation).hadErrorContaining("@Queue on parameter 'q' of f needs the type Publisher<T>");
    }

    @Test
    public void configPropertyParametersNeedAName() {
        Compilation compilation = javac()
                .withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.NoName", ""
                        + "package com.example;\n"
                        + "import eu.cloudhopper.mc.annotations.*;\n"
                        + "import org.eclipse.microprofile.config.inject.ConfigProperty;\n"
                        + "public class NoName {\n"
                        + "  @Function(name=\"noname\")\n"
                        + "  public String f(@ConfigProperty String bucket) { return bucket; }\n"
                        + "}\n"));
        assertThat(compilation).hadErrorContaining("@ConfigProperty on parameter 'bucket' of f needs a name");
    }

    @Test
    public void eventMetadataIsTakenFromTheContext() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.QueueService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.EventMetadata;\n"
                + "public class QueueService {\n"
                + "  @Function(name=\"orders\")\n"
                + "  @EventTrigger(source=EventSource.QUEUE, name=\"orders\")\n"
                + "  public String onOrder(String order, EventMetadata meta) {\n"
                + "    return order + \"|\" + (meta == null ? \"none\" : meta.getId() + \"/\" + meta.getDeliveryAttempt());\n"
                + "  }\n"
                + "}\n",
                "com.example.QueueService_onOrder_Handler");

        EventMetadata meta = new EventMetadata("m-1", "orders", Instant.EPOCH, 2, Map.of("k", "v"));
        assertEquals("o1|m-1/2", handler.handleRequest("o1", new EventHandlerContext(new StubContext(), meta)));
        // not an event: no metadata
        assertEquals("o2|none", handler.handleRequest("o2", new StubContext()));
        assertEquals("o3|none", handler.handleRequest("o3", null));
    }

    @Test
    public void identityNamesMustBeValidOnEveryTarget() {
        for (String name : new String[]{"Connector", "connector_1", "connector--x", "-connector", "1connector",
            "connector-", "a23456789012345678901234567890123"}) {
            Compilation compilation = javac()
                    .withProcessors(new ServerlessFunctionProcessor())
                    .compile(JavaFileObjects.forSourceString("com.example.BadIdentity", ""
                            + "package com.example;\n"
                            + "import eu.cloudhopper.mc.annotations.*;\n"
                            + "public class BadIdentity {\n"
                            + "  @Function(name=\"bad\", identity=\"" + name + "\")\n"
                            + "  public String bad() { return \"\"; }\n"
                            + "}\n"));
            assertThat(compilation).hadErrorContaining("@Function identity '" + name + "' is not valid on every target");
        }
    }

    @Test
    public void explicitIdentityMustNotBeTheImplicitIdentityOfAnotherFunction() {
        for (boolean implicitFirst : new boolean[]{true, false}) {
            String implicit = "  @Function(name=\"connector\")\n  public String connect() { return \"\"; }\n";
            String explicit = "  @Function(name=\"report\", identity=\"connector\")\n  public String report() { return \"\"; }\n";
            Compilation compilation = javac()
                    .withProcessors(new ServerlessFunctionProcessor())
                    .compile(JavaFileObjects.forSourceString("com.example.Clash", ""
                            + "package com.example;\n"
                            + "import eu.cloudhopper.mc.annotations.*;\n"
                            + "public class Clash {\n"
                            + (implicitFirst ? implicit + explicit : explicit + implicit)
                            + "}\n"));
            assertThat(compilation).hadErrorContaining(
                    "@Function identity 'connector' is also the identity of function 'connector'");
        }
    }

    @Test
    public void functionsCanShareAnIdentityDeclaredOnAll() {
        Compilation compilation = javac()
                .withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Shared", ""
                        + "package com.example;\n"
                        + "import eu.cloudhopper.mc.annotations.*;\n"
                        + "public class Shared {\n"
                        + "  @Function(name=\"connector\", identity=\"connector\")\n"
                        + "  public String connect() { return \"\"; }\n"
                        + "  @Function(name=\"report\", identity=\"connector\")\n"
                        + "  public String report() { return \"\"; }\n"
                        + "}\n"));
        assertThat(compilation).succeeded();
    }

    @Test
    public void eventSourceNamesMustBeValidOnEveryTarget() {
        for (String name : new String[]{"Orders", "or", "orders_in", "orders--in", "-orders", "1orders"}) {
            Compilation compilation = javac()
                    .withProcessors(new ServerlessFunctionProcessor())
                    .compile(JavaFileObjects.forSourceString("com.example.BadQueue", ""
                            + "package com.example;\n"
                            + "import eu.cloudhopper.mc.annotations.*;\n"
                            + "public class BadQueue {\n"
                            + "  @Function(name=\"bad\")\n"
                            + "  @EventTrigger(source=EventSource.QUEUE, name=\"" + name + "\")\n"
                            + "  public void on(String message) { }\n"
                            + "}\n"));
            assertThat(compilation).hadErrorContaining("@EventTrigger name '" + name + "' is not valid on every target");
        }
    }

    @Test
    public void storageFunctionsReceiveAStorageEvent() throws Exception {
        CloudRequestHandler<StorageEvent, String> handler = compileAndLoad("com.example.UploadService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import eu.cloudhopper.mc.runtime.StorageEvent;\n"
                + "public class UploadService {\n"
                + "  @Function(name=\"uploads\")\n"
                + "  @EventTrigger(source=EventSource.STORAGE, name=\"uploads\")\n"
                + "  public String onUpload(StorageEvent event) {\n"
                + "    return event.getBucket() + \"/\" + event.getKey() + \":\" + event.getSize();\n"
                + "  }\n"
                + "}\n",
                "com.example.UploadService_onUpload_Handler");
        assertEquals("uploads/a/b.txt:42",
                handler.handleRequest(new StorageEvent("uploads", "a/b.txt", 42, StorageEvent.Kind.CREATED), null));
    }

    @Test
    public void storageFunctionsCannotTakeOtherBodies() {
        Compilation compilation = javac()
                .withProcessors(new ServerlessFunctionProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.BadUpload", ""
                        + "package com.example;\n"
                        + "import eu.cloudhopper.mc.annotations.*;\n"
                        + "public class BadUpload {\n"
                        + "  @Function(name=\"bad\")\n"
                        + "  @EventTrigger(source=EventSource.STORAGE, name=\"uploads\")\n"
                        + "  public void on(String content) { }\n"
                        + "}\n"));
        assertThat(compilation).hadErrorContaining("receives a eu.cloudhopper.mc.runtime.StorageEvent");
    }

    /** A platform context with fixed values. */
    private static final class StubContext implements HandlerContext {
        @Override public String getRequestId() { return "r"; }
        @Override public String getFunctionName() { return "f"; }
        @Override public String getFunctionVersion() { return "1"; }
        @Override public String getInvokedFunctionArn() { return null; }
        @Override public String getLogGroupName() { return null; }
        @Override public String getLogStreamName() { return null; }
        @Override public long getRemainingTimeInMillis() { return 0; }
        @Override public int getMemoryLimitInMB() { return 0; }
    }

    @SuppressWarnings("unchecked")
    // #66: CloudRequestHandler declares no checked exceptions, so the handler wraps them
    @Test
    public void checkedExceptionsAreWrappedAndRuntimeExceptionsPassThrough() throws Exception {
        CloudRequestHandler<String, String> handler = compileAndLoad("com.example.CheckedService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class CheckedService {\n"
                + "  @Function(name=\"checked\")\n"
                + "  @HttpTrigger(method=\"POST\", path=\"/checked\")\n"
                + "  public String read(String body) throws java.io.IOException {\n"
                + "    if (\"io\".equals(body)) throw new java.io.IOException(\"disk\");\n"
                + "    if (\"state\".equals(body)) throw new IllegalStateException(\"state\");\n"
                + "    return \"ok:\" + body;\n"
                + "  }\n"
                + "}\n",
                "com.example.CheckedService_read_Handler");
        assertEquals("ok:x", handler.handleRequest("x", Map.of(), Map.of(), null));
        FunctionException wrapped = assertThrows(FunctionException.class,
                () -> handler.handleRequest("io", Map.of(), Map.of(), null));
        assertTrue(wrapped.getCause() instanceof IOException, "cause: " + wrapped.getCause());
        assertEquals("disk", wrapped.getMessage());
        assertThrows(IllegalStateException.class, () -> handler.handleRequest("state", Map.of(), Map.of(), null));
    }

    // #65: parameterized body and return types, nested ones included
    @Test
    public void parameterizedBodyAndReturnTypesCompile() throws Exception {
        CloudRequestHandler<Map<String, List<String>>, Map<String, Object>> handler = compileAndLoad(
                "com.example.FormService", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "import java.util.*;\n"
                + "public class FormService {\n"
                + "  @Function(name=\"form\")\n"
                + "  @HttpTrigger(method=\"POST\", path=\"/form\")\n"
                + "  public Map<String, Object> submit(Map<String, List<String>> form) {\n"
                + "    Map<String, Object> result = new LinkedHashMap<>();\n"
                + "    result.put(\"fields\", form.size());\n"
                + "    result.put(\"tags\", form.get(\"tags\"));\n"
                + "    return result;\n"
                + "  }\n"
                + "}\n",
                "com.example.FormService_submit_Handler");
        Map<String, Object> result = handler.handleRequest(Map.of("tags", List.of("a", "b")), Map.of(), Map.of(), null);
        assertEquals(1, result.get("fields"));
        assertEquals(List.of("a", "b"), result.get("tags"));
    }

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
