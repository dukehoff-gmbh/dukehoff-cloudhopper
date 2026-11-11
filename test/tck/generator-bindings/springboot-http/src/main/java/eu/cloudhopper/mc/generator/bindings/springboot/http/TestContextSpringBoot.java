package eu.cloudhopper.mc.generator.bindings.springboot.http;

/*-
 * #%L
 * generator-bindings-springboot-http - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * TCK binding for the {@code springboot-http} target.
 *
 * <p>"Deploying" starts the generated service the way a user runs it: the executable jar built by
 * the {@code spring-boot-maven-plugin} ({@code java -jar target/<artifactId>-<version>-springboot.jar}),
 * on a free local port. HTTP functions are called through their generated routes, functions without
 * a trigger through {@code /functions/<name>}, and events are posted to {@code /events/<name>},
 * which is where a broker or a platform trigger delivers them. The service's console output is
 * the log.</p>
 *
 * <p>Routes come from the generator's output in {@code target/deployment/springboot-http}:
 * {@code META-INF/handler-info.properties} for HTTP functions and
 * {@code <function>_event_eventRoute.properties} for event-triggered functions.</p>
 */
public class TestContextSpringBoot implements TestContext {

    private static final String JAR_CLASSIFIER = "springboot";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private final Path moduleDir = Path.of(System.getProperty("user.dir"));
    private final Path deploymentDir = moduleDir.resolve("target").resolve("deployment").resolve("springboot-http");
    private final Path logFile = moduleDir.resolve("target").resolve("tck-springboot.log");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private Process process;
    private int port;

    @Override
    public void deployTestFunctions() {
        try {
            Path jar = findExecutableJar();
            port = freePort();
            Files.deleteIfExists(logFile);
            System.out.println("🚀 Starting " + moduleDir.relativize(jar) + " on port " + port);
            process = new ProcessBuilder("java", "-jar", jar.toString(), "--server.port=" + port)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            waitForPort();
            System.out.println("✅ Service is up");
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the Spring Boot service", e);
        }
    }

    @Override
    public void cleanupTestFunctions() {
        if (process == null) {
            return; // nothing running; also the case for the runner's pre-deploy cleanup
        }
        process.destroy();
        try {
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
    }

    @Override
    public String getHttpUrl(String functionName) {
        Properties handlers = load(deploymentDir.resolve("META-INF").resolve("handler-info.properties"));
        for (String key : handlers.stringPropertyNames()) {
            if (key.endsWith("_functionId") && handlers.getProperty(key).equalsIgnoreCase(functionName)) {
                String base = key.substring(0, key.length() - "_functionId".length());
                return baseUrl() + handlers.getProperty(base + "_path");
            }
        }
        throw new IllegalArgumentException("No HTTP route generated for function " + functionName);
    }

    @Override
    public Object invokeFunctionDirect(String functionName, Object input) {
        try {
            URI uri = URI.create(baseUrl() + "/functions/" + functionName.toLowerCase());
            HttpRequest request = input == null
                    ? HttpRequest.newBuilder(uri).GET().build()
                    : HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input)))
                            .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(uri + " returned " + response.statusCode() + ": " + response.body());
            }
            String body = response.body();
            return body == null || body.isBlank() ? null : mapper.readValue(body, Object.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to invoke function " + functionName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> fetchLogs(String functionName) {
        try {
            // one service hosts all functions, so the log is shared; the tests look for their own lines
            return Files.exists(logFile) ? Files.readAllLines(logFile, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + logFile, e);
        }
    }

    @Override
    public void sendQueueMessage(String queueName, String message) {
        postEvent(route("QUEUE", queueName), message);
        System.out.println("📨 Posted message for queue " + queueName);
    }

    @Override
    public void uploadObject(String bucket, String key, String content) {
        // there is no storage on this target: post the event a storage notification would deliver
        Map<String, Object> event = Map.of("bucket", bucket, "key", key,
                "size", content.getBytes(StandardCharsets.UTF_8).length);
        try {
            postEvent(route("STORAGE", bucket), mapper.writeValueAsString(event));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("📤 Posted storage event for " + bucket + "/" + key);
    }

    @Override
    public void deleteObject(String bucket, String key) {
        // nothing was stored
    }

    private String route(String source, String name) {
        try (Stream<Path> files = Files.list(deploymentDir)) {
            List<Path> routes = files.filter(p -> p.getFileName().toString().endsWith("_eventRoute.properties")).toList();
            for (Path file : routes) {
                Properties route = load(file);
                if (source.equals(route.getProperty("source")) && name.equals(route.getProperty("name"))) {
                    return route.getProperty("path");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the event routes in " + deploymentDir, e);
        }
        throw new IllegalArgumentException("No function listens to " + source + " " + name);
    }

    private void postEvent(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(path + " returned " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to post an event to " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Path findExecutableJar() throws IOException {
        try (Stream<Path> files = Files.list(moduleDir.resolve("target"))) {
            List<Path> jars = files.filter(p -> p.getFileName().toString().endsWith("-" + JAR_CLASSIFIER + ".jar")).toList();
            if (jars.size() != 1) {
                throw new IllegalStateException("Expected one executable jar *-" + JAR_CLASSIFIER + ".jar in target, found "
                        + jars + "; build the module first (mvn package)");
            }
            return jars.get(0);
        }
    }

    private void waitForPort() {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The service exited with " + process.exitValue() + "; see " + logFile);
            }
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException notYet) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalStateException("The service did not open port " + port + " within " + STARTUP_TIMEOUT + "; see " + logFile);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
        return properties;
    }
}
