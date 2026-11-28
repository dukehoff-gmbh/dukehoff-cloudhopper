/*-
 * #%L
 * cloudhopper-maven-plugin - a library from the "Cloudhopper" project.
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
package eu.cloudhopper.mc.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs the native image of the aws-terraform functions locally before a deployment: starts the
 * {@code bootstrap} in the Lambda base image {@code public.ecr.aws/lambda/provided:al2023}, whose
 * Runtime Interface Emulator invokes it like Lambda, once per function with the function's
 * handler. Errors that exist only in a native image, such as missing reflection metadata, show up
 * here instead of after the deployment.
 *
 * <ul>
 *   <li>For every function, the smoke event runs the request path as the SnapStart priming does
 *       (the conversion of an API Gateway event, Jackson for the body type, the responses),
 *       without the function's code, so it needs no cloud access. The container runs with
 *       {@code CLOUDHOPPER_SMOKE_TEST=true}, which enables the event.</li>
 *   <li>Events of your own, in {@code src/test/native-smoke/events.json}, run the functions
 *       themselves: a list of {@code {"function": "<name>", "event": {...}, "environment":
 *       {...}}}, e.g. an API Gateway event of a route that writes records as JSON. An event fails
 *       the build when the invocation fails or an HTTP function answers 5xx.</li>
 * </ul>
 *
 * Needs Docker. Runs in {@code verify}, after {@code cloudhopper:native-image}.
 */
@Mojo(name = "native-smoke", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class NativeSmokeMojo extends AbstractMojo {

    private static final Pattern FUNCTION_NAME = Pattern.compile("function_name\\s*=\\s*\"\\$\\{var\\.cloudhopper_name_prefix}([^\"]+)\"");
    private static final Pattern HANDLER = Pattern.compile("handler\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern RUNTIME = Pattern.compile("runtime\\s*=\\s*\"([^\"]+)\"");

    /** The Lambda base image with the Runtime Interface Emulator. */
    @Parameter(property = "native.smoke.image", defaultValue = "public.ecr.aws/lambda/provided:al2023")
    private String image;

    /** The architecture of the executable: arm64 or x86_64. */
    @Parameter(property = "native.architecture", defaultValue = "arm64")
    private String architecture;

    /** The directory of the executable bootstrap. */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private java.io.File targetDirectory;

    /** The generated Terraform of the functions. */
    @Parameter(property = "native.smoke.deployment", defaultValue = "${project.build.directory}/deployment/aws-terraform")
    private java.io.File deploymentDirectory;

    /** Events of your own (optional). */
    @Parameter(property = "native.smoke.events", defaultValue = "${project.basedir}/src/test/native-smoke/events.json")
    private java.io.File events;

    /** Runs only this function (name as in @Function), e.g. -Dnative.smoke.function=price. */
    @Parameter(property = "native.smoke.function")
    private String function;

    /** Skips the goal. */
    @Parameter(property = "native.smoke.skip", defaultValue = "false")
    private boolean skip;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the native smoke test");
            return;
        }
        Path bootstrap = targetDirectory.toPath().toAbsolutePath().resolve("bootstrap");
        if (!Files.isRegularFile(bootstrap)) {
            throw new MojoExecutionException("No native image " + bootstrap + ": run cloudhopper:native-image first");
        }
        Map<String, String> handlers = handlers(deploymentDirectory.toPath());
        if (function != null && !function.isBlank()) {
            String id = function.toLowerCase();
            if (!handlers.containsKey(id)) {
                throw new MojoExecutionException("No function " + function + " in " + deploymentDirectory);
            }
            handlers = Map.of(id, handlers.get(id));
        }
        Map<String, List<JsonNode>> own = ownEvents();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> fn : handlers.entrySet()) {
            List<JsonNode> cases = own.getOrDefault(fn.getKey(), List.of());
            failures.addAll(test(bootstrap, fn.getKey(), fn.getValue(), cases));
        }
        if (!failures.isEmpty()) {
            throw new MojoFailureException("The native image failed for " + failures.size() + " event(s):\n"
                    + String.join("\n\n", failures));
        }
        getLog().info("Native smoke test passed for " + handlers.size() + " function(s)");
    }

    /** The handler of every function of the generated Terraform, by function ID. */
    Map<String, String> handlers(Path deployment) throws MojoExecutionException {
        Map<String, String> handlers = new TreeMap<>();
        try (Stream<Path> files = Files.list(deployment)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith("_function.tf")).sorted().toList()) {
                String tf = Files.readString(file);
                Matcher name = FUNCTION_NAME.matcher(tf);
                Matcher handler = HANDLER.matcher(tf);
                Matcher runtime = RUNTIME.matcher(tf);
                if (name.find() && handler.find()) {
                    if (runtime.find() && !runtime.group(1).startsWith("provided")) {
                        throw new MojoExecutionException("The deployment in " + deployment + " is for the Java runtime ("
                                + runtime.group(1) + "): build with the native profile (-Aruntime=native)");
                    }
                    handlers.put(name.group(1), handler.group(1));
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read the generated Terraform in " + deployment
                    + ": build with -Paws-terraform first", e);
        }
        if (handlers.isEmpty()) {
            throw new MojoExecutionException("No functions in " + deployment);
        }
        return handlers;
    }

    private Map<String, List<JsonNode>> ownEvents() throws MojoExecutionException {
        Map<String, List<JsonNode>> byFunction = new LinkedHashMap<>();
        if (events == null || !events.isFile()) {
            return byFunction;
        }
        try {
            for (JsonNode entry : mapper.readTree(events)) {
                String name = entry.path("function").asText("").toLowerCase();
                if (name.isEmpty() || !entry.has("event")) {
                    throw new MojoExecutionException(events + ": every entry needs \"function\" and \"event\"");
                }
                byFunction.computeIfAbsent(name, n -> new ArrayList<>()).add(entry);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read " + events + ": " + e.getMessage(), e);
        }
        return byFunction;
    }

    /** Starts the function's container, sends the smoke event and the function's own events. */
    private List<String> test(Path bootstrap, String id, String handler, List<JsonNode> cases) throws MojoExecutionException {
        List<String> failures = new ArrayList<>();
        Map<String, String> environment = new TreeMap<>();
        cases.forEach(c -> c.path("environment").fields().forEachRemaining(e -> environment.put(e.getKey(), e.getValue().asText())));
        int port = freePort();
        List<String> run = new ArrayList<>(List.of("docker", "run", "-d", "--rm", "--platform", "linux/" + architecture,
                "-p", "127.0.0.1:" + port + ":8080", "-e", "CLOUDHOPPER_SMOKE_TEST=true",
                "-e", "AWS_LAMBDA_FUNCTION_NAME=" + id,
                "-v", bootstrap + ":/var/runtime/bootstrap:ro"));
        environment.forEach((name, value) -> run.addAll(List.of("-e", name + "=" + value)));
        run.add(image);
        run.add(handler);
        String container = Docker.run(getLog(), bootstrap.getParent(), run).trim();
        try {
            URI uri = URI.create("http://127.0.0.1:" + port + "/2015-03-31/functions/function/invocations");
            String smoke = invoke(uri, "{\"cloudhopper-smoke-test\": true}", true);
            if (smoke == null || !smoke.contains("\"primed\"")) {
                failures.add(id + ": the smoke event failed: " + smoke + "\n" + logs(container));
            } else {
                getLog().info("✔ " + id + ": request path");
            }
            for (JsonNode c : cases) {
                String body = invoke(uri, mapper.writeValueAsString(c.get("event")), false);
                String problem = problem(body);
                if (problem != null) {
                    failures.add(id + ": " + problem + "\n" + logs(container));
                } else {
                    getLog().info("✔ " + id + ": " + abbreviate(c.get("event").toString()));
                }
            }
        } catch (IOException e) {
            failures.add(id + ": " + e.getMessage() + "\n" + logs(container));
        } finally {
            try {
                Docker.run(getLog(), bootstrap.getParent(), List.of("docker", "rm", "-f", container));
            } catch (MojoExecutionException e) {
                getLog().warn("Cannot remove the container " + container + ": " + e.getMessage());
            }
        }
        return failures;
    }

    /** Why an answer is a failure: an invocation error or an HTTP status 5xx; null if it is none. */
    String problem(String body) throws IOException {
        if (body == null) {
            return "no answer";
        }
        JsonNode answer;
        try {
            answer = mapper.readTree(body);
        } catch (IOException e) {
            return null; // not JSON: a plain result
        }
        if (answer != null && answer.has("errorType")) {
            return "the invocation failed: " + answer.path("errorType").asText() + ": " + answer.path("errorMessage").asText();
        }
        if (answer != null && answer.path("statusCode").asInt(0) >= 500) {
            return "the function answered " + answer.path("statusCode").asInt() + ": " + answer.path("body").asText();
        }
        return null;
    }

    /** Posts an event; the first one waits until the emulator answers. */
    private String invoke(URI uri, String event, boolean waitForStart) throws IOException {
        Instant deadline = Instant.now().plus(waitForStart ? Duration.ofSeconds(30) : Duration.ZERO);
        while (true) {
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(event)).build(), HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (IOException e) {
                if (Instant.now().isAfter(deadline)) {
                    throw e;
                }
                sleep();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
    }

    private String logs(String container) {
        try {
            return "Log of the function:\n" + Docker.tail(Docker.run(getLog(), targetDirectory.toPath(),
                    List.of("docker", "logs", container)));
        } catch (MojoExecutionException e) {
            return "(no log: " + e.getMessage() + ")";
        }
    }

    private static int freePort() throws MojoExecutionException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new MojoExecutionException("No free port", e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
