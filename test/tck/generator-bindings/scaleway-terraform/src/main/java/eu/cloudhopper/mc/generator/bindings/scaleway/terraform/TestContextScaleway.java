package eu.cloudhopper.mc.generator.bindings.scaleway.terraform;

/*-
 * #%L
 * generator-bindings-scaleway-terraform - a library from the "Cloudhopper" project.
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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * TCK binding for the {@code scaleway-terraform} target. It deploys the way a user would: it
 * creates a registry namespace, builds the image from the generated Dockerfile for linux/amd64,
 * pushes it and applies the generated Terraform with the TCK's {@code main.tf} (provider,
 * registry, Cockpit token). Logs come from Cockpit (Loki), queue messages go to Scaleway Queues
 * through the SQS API.
 *
 * <p>Credentials and the project come from {@code ~/.config/scw/config.yaml} ({@code scw init})
 * or from {@code SCW_ACCESS_KEY}, {@code SCW_SECRET_KEY} and {@code SCW_DEFAULT_PROJECT_ID}. The
 * environment variables below change the defaults.
 *
 * <ul>
 *   <li>{@code CLOUDHOPPER_TCK_SCW_CONTAINER} (cloudhopper-tck): name of the container and its namespace</li>
 *   <li>{@code CLOUDHOPPER_TCK_SCW_REGISTRY} (dukehoff-cloudhopper-tck): registry namespace, unique across Scaleway</li>
 *   <li>{@code CLOUDHOPPER_TCK_NAME_PREFIX} (tck-): prefix of the queue and bucket names</li>
 *   <li>{@code CLOUDHOPPER_TCK_SCW_BLOBSTORE_ACCESS_KEY} and {@code CLOUDHOPPER_TCK_SCW_BLOBSTORE_SECRET_KEY}
 *       (required): the key the container uses for the object storage, passed to the Terraform
 *       variable {@code cloudhopper_blobstore_credentials}; never printed</li>
 * </ul>
 */
public class TestContextScaleway implements TestContext {

    private static final String JAR_CLASSIFIER = "scaleway";
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration LOG_WINDOW = Duration.ofMinutes(60);
    private static final Pattern SECRET_KEY = Pattern.compile("^\\s*secret_key:\\s*['\"]?([^'\"\\s]+)", Pattern.MULTILINE);

    private final Path moduleDir = Path.of(System.getProperty("user.dir"));
    private final Path deploymentDir = moduleDir.resolve("target").resolve("deployment").resolve("scaleway-terraform");
    private final Path dockerConfig = moduleDir.resolve("target").resolve("docker-config");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = env("CLOUDHOPPER_TCK_SCW_CONTAINER", "cloudhopper-tck");
    private final String registryNamespace = env("CLOUDHOPPER_TCK_SCW_REGISTRY", "dukehoff-cloudhopper-tck");
    private final String namePrefix = env("CLOUDHOPPER_TCK_NAME_PREFIX", "tck-");

    private String baseUrl;
    private JsonNode outputs;

    @Override
    public java.util.Set<String> supportedCapabilities() {
        return java.util.Set.of(eu.cloudhopper.mc.test.tck.api.CapabilityAwareTest.SECRETS,
                eu.cloudhopper.mc.test.tck.api.CapabilityAwareTest.PROJECT_TERRAFORM_CONFIG);
    }

    @Override
    public void deployTestFunctions() {
        try {
            Path jar = findExecutableJar();
            terraform("init", "-input=false");

            System.out.println("📦 Creating the registry namespace " + registryNamespace);
            terraform(variables("pending", "apply", "-auto-approve", "-input=false",
                    "-target=scaleway_registry_namespace.tck"));
            String repository = output("registry_endpoint") + "/" + moduleDir.getFileName().toString().toLowerCase();
            String image = repository + ":tck-" + System.currentTimeMillis();

            System.out.println("🐳 Building and pushing " + image + " from " + moduleDir.relativize(jar));
            dockerLogin(image.substring(0, image.indexOf('/')));
            docker("build", "-q", "--platform", "linux/amd64", "-t", image,
                    "-f", deploymentDir.resolve("shared-resources_dockerfile..dockerfile").toString(), ".");
            docker("push", "-q", image);

            System.out.println("☁️ Applying the Terraform in " + moduleDir.relativize(deploymentDir));
            System.out.println(terraform(variables(image, "apply", "-auto-approve", "-input=false")));
            outputs = null;
            baseUrl = output("url").replaceAll("/+$", "");
            waitUntilUp();
            System.out.println("✅ Container is up at " + baseUrl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deploy the Scaleway container", e);
        }
    }

    @Override
    public void cleanupTestFunctions() {
        try {
            if (Files.isDirectory(deploymentDir)) {
                terraform("init", "-input=false");
                System.out.println(terraform(variables("unused", "destroy", "-auto-approve", "-input=false")));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to destroy the Scaleway resources", e);
        } finally {
            deleteDockerConfig();
            baseUrl = null;
            outputs = null;
        }
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
        // one container hosts all functions, so the log is shared; the tests look for their own lines.
        // Cockpit names the container's log stream <namespace><id>-<container>; container names
        // consist of lowercase letters, digits and hyphens, so the name needs no escaping
        String query = "{resource_type=\"serverless_container\", resource_name=~\".+-" + containerName + "\"}";
        long now = Instant.now().toEpochMilli() * 1_000_000L;
        long start = now - LOG_WINDOW.toNanos();
        String uri = output("logs_url") + "/loki/api/v1/query_range"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&start=" + start + "&end=" + now + "&limit=5000&direction=backward";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .header("Authorization", "Bearer " + output("logs_token"))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Cockpit returned " + response.statusCode() + ": " + response.body());
            }
            List<long[]> order = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            for (JsonNode stream : mapper.readTree(response.body()).path("data").path("result")) {
                for (JsonNode value : stream.path("values")) {
                    order.add(new long[] {Long.parseLong(value.get(0).asText()), lines.size()});
                    lines.add(message(value.get(1).asText()));
                }
            }
            order.sort((a, b) -> Long.compare(a[0], b[0]));
            List<String> sorted = new ArrayList<>(lines.size());
            order.forEach(o -> sorted.add(lines.get((int) o[1])));
            return Collections.unmodifiableList(sorted);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the logs of " + containerName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendQueueMessage(String queueName, String message) {
        String function = route("QUEUE", queueName).getProperty("function");
        String queueUrl = output(function.toLowerCase().replaceAll("[^a-z0-9_]", "_") + "_queue_url");
        try (SqsClient sqs = SqsClient.builder()
                .endpointOverride(URI.create(output("queue_endpoint")))
                .region(Region.of("fr-par"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(output("queue_access_key"), output("queue_secret_key"))))
                .build()) {
            String id = sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build()).messageId();
            System.out.println("📨 Sent message " + id + " to the queue " + namePrefix + queueName);
        }
    }

    @Override
    public void uploadObject(String bucket, String key, String content) {
        // no storage trigger on this target: post the event a storage notification would deliver
        Map<String, Object> event = Map.of("bucket", bucket, "key", key,
                "size", content.getBytes(StandardCharsets.UTF_8).length);
        try {
            postEvent(route("STORAGE", bucket).getProperty("path"), mapper.writeValueAsString(event));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("📤 Posted storage event for " + bucket + "/" + key);
    }

    @Override
    public void deleteObject(String bucket, String key) {
        // nothing was stored
    }

    @Override
    public String getDeployedScheduleTimezone(String functionName) {
        // read back from the cron trigger after apply
        return output(functionName.toLowerCase().replaceAll("[^a-z0-9_]", "_") + "_schedule_timezone");
    }

    /** The Terraform arguments with the TCK's variables. */
    private String[] variables(String image, String... command) {
        List<String> args = new ArrayList<>(List.of(command));
        args.addAll(List.of(
                "-var", "image=" + image,
                "-var", "container_name=" + containerName,
                "-var", "registry_namespace=" + registryNamespace,
                "-var", "cloudhopper_name_prefix=" + namePrefix,
                "-var", "cloudhopper_config=" + eu.cloudhopper.mc.test.tck.api.TckConfiguration.terraformVariable(),
                "-var", "cloudhopper_secrets={\"" + eu.cloudhopper.mc.test.tck.api.TckConfiguration.SECRET_PROPERTY
                        + "\" = \"" + eu.cloudhopper.mc.test.tck.api.TckConfiguration.SECRET + "\"}",
                // one instance stays up, so that the cron triggers and the logs do not wait for cold starts
                "-var", "min_scale=1",
                "-var", "max_scale=1"));
        return args.toArray(String[]::new);
    }

    private String output(String key) {
        try {
            if (outputs == null) {
                outputs = mapper.readTree(run(deploymentDir, Map.of(), null, "terraform", "output", "-json", "-no-color"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the Terraform outputs", e);
        }
        JsonNode value = outputs.path(key).path("value");
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalStateException("No Terraform output " + key);
        }
        return value.asText();
    }

    private void waitUntilUp() {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        int last = -1;
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                        .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.discarding());
                last = response.statusCode();
                if (last < 500) {
                    return; // Spring Boot answers, even if with 404
                }
            } catch (IOException e) {
                // not reachable yet
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            sleep(Duration.ofSeconds(5));
        }
        throw new IllegalStateException(baseUrl + " did not come up within " + STARTUP_TIMEOUT + " (last status " + last + ")");
    }

    private void dockerLogin(String registry) throws IOException {
        // a config of its own keeps the login out of the user's credential store
        Files.createDirectories(dockerConfig);
        Files.writeString(dockerConfig.resolve("config.json"), "{}");
        run(moduleDir, Map.of("DOCKER_CONFIG", dockerConfig.toString()), secretKey(),
                "docker", "login", registry, "-u", "nologin", "--password-stdin");
    }

    private void docker(String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(args));
        run(moduleDir, Map.of("DOCKER_CONFIG", dockerConfig.toString()), null, command.toArray(String[]::new));
    }

    private void deleteDockerConfig() {
        try (Stream<Path> files = Files.exists(dockerConfig) ? Files.walk(dockerConfig) : Stream.empty()) {
            files.sorted(Collections.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            System.out.println("ℹ️ Could not delete " + dockerConfig + ": " + e.getMessage());
        }
    }

    private String terraform(String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("terraform"));
        command.addAll(List.of(args));
        command.add("-no-color");
        return run(deploymentDir, blobstoreCredentials(), null, command.toArray(String[]::new));
    }

    /** The key of the object storage as the Terraform variable cloudhopper_blobstore_credentials, in the environment. */
    private static Map<String, String> blobstoreCredentials() {
        String accessKey = System.getenv("CLOUDHOPPER_TCK_SCW_BLOBSTORE_ACCESS_KEY");
        String secretKey = System.getenv("CLOUDHOPPER_TCK_SCW_BLOBSTORE_SECRET_KEY");
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return Map.of();
        }
        return Map.of("TF_VAR_cloudhopper_blobstore_credentials",
                "{\"access_key\":\"" + accessKey.trim() + "\",\"secret_key\":\"" + secretKey.trim() + "\"}");
    }

    /** The Scaleway secret key from SCW_SECRET_KEY or the scw configuration; never printed. */
    private static String secretKey() throws IOException {
        String key = System.getenv("SCW_SECRET_KEY");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String configPath = System.getenv("SCW_CONFIG_PATH");
        Path config = configPath != null && !configPath.isBlank()
                ? Path.of(configPath)
                : Path.of(System.getProperty("user.home"), ".config", "scw", "config.yaml");
        if (Files.isRegularFile(config)) {
            Matcher matcher = SECRET_KEY.matcher(Files.readString(config));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("No Scaleway secret key: set SCW_SECRET_KEY or run scw init");
    }

    /** A Cockpit log line is JSON with the application's output in "message". */
    private String message(String line) {
        try {
            JsonNode json = mapper.readTree(line);
            return json.hasNonNull("message") ? json.get("message").asText() : line;
        } catch (IOException e) {
            return line;
        }
    }

    private Properties route(String source, String name) {
        try (Stream<Path> files = Files.list(deploymentDir)) {
            List<Path> routes = files.filter(p -> p.getFileName().toString().endsWith("_eventRoute.properties")).toList();
            for (Path file : routes) {
                Properties route = load(file);
                if (source.equals(route.getProperty("source")) && name.equals(route.getProperty("name"))) {
                    return route;
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

    private String baseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("The container is not deployed");
        }
        return baseUrl;
    }

    /**
     * Runs a command and returns its standard output; fails with its output if it exits with an
     * error. {@code input}, if given, goes to the command's standard input and is never printed.
     */
    private static String run(Path dir, Map<String, String> environment, String input, String... command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        try (OutputStream in = process.getOutputStream()) {
            if (input != null) {
                in.write(input.getBytes(StandardCharsets.UTF_8));
            }
        }
        String output;
        try (InputStream out = process.getInputStream()) {
            output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(command[0] + " " + command[1] + " did not finish within " + COMMAND_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (process.exitValue() != 0) {
            throw new IOException(String.join(" ", command) + " exited with " + process.exitValue() + ":\n" + output);
        }
        return output;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
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
