package eu.cloudhopper.mc.generator.bindings.knative;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * TCK binding for the {@code knative} target. It builds the image from the generated Dockerfile,
 * loads it into a kind cluster and installs the generated Helm chart, as a user would with a
 * registry. The cluster comes from {@code test/knative/kind-setup.sh}; the environment variables
 * below change its defaults.
 *
 * <ul>
 *   <li>{@code CLOUDHOPPER_TCK_KIND_CLUSTER} (cloudhopper): the kind cluster the image is loaded into</li>
 *   <li>{@code CLOUDHOPPER_TCK_NAMESPACE} (default): the namespace of the Helm release</li>
 *   <li>{@code CLOUDHOPPER_TCK_INGRESS_PORT} (31080): the host port of the Knative ingress</li>
 *   <li>{@code CLOUDHOPPER_TCK_KAFKA} (localhost:31092): Kafka as the TCK reaches it</li>
 *   <li>{@code CLOUDHOPPER_TCK_CLUSTER_KAFKA} (kafka.kafka.svc:9092): Kafka as the cluster reaches it</li>
 * </ul>
 */
public class TestContextKnative implements TestContext {

    private static final String RELEASE = "cloudhopper-tck";
    private static final String JAR_CLASSIFIER = "knative";
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(10);

    private final Path moduleDir = Path.of(System.getProperty("user.dir"));
    private final Path deploymentDir = moduleDir.resolve("target").resolve("deployment").resolve("knative");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String kindCluster = env("CLOUDHOPPER_TCK_KIND_CLUSTER", "cloudhopper");
    private final String namespace = env("CLOUDHOPPER_TCK_NAMESPACE", "default");
    private final String ingressPort = env("CLOUDHOPPER_TCK_INGRESS_PORT", "31080");
    private final String kafka = env("CLOUDHOPPER_TCK_KAFKA", "localhost:31092");
    private final String clusterKafka = env("CLOUDHOPPER_TCK_CLUSTER_KAFKA", "kafka.kafka.svc:9092");

    private String baseUrl;

    @Override
    public void deployTestFunctions() {
        try {
            Path jar = findExecutableJar();
            // a new tag per run, so that Knative rolls out a new revision
            String repository = "dev.local/" + moduleDir.getFileName().toString().toLowerCase();
            String tag = "tck-" + System.currentTimeMillis();
            System.out.println("🐳 Building " + repository + ":" + tag + " from " + moduleDir.relativize(jar));
            run(moduleDir, "docker", "build", "-q", "-t", repository + ":" + tag,
                    "-f", deploymentDir.resolve("shared-resources_dockerfile..dockerfile").toString(), ".");
            run(moduleDir, "kind", "load", "docker-image", repository + ":" + tag, "--name", kindCluster);

            System.out.println("⎈ Installing the chart as release " + RELEASE + " in namespace " + namespace);
            run(moduleDir, "helm", "upgrade", "--install", RELEASE, deploymentDir.resolve("chart").toString(),
                    "--namespace", namespace,
                    "--set", "image.repository=" + repository,
                    "--set", "image.tag=" + tag,
                    "--set", "kafka.bootstrapServers={" + clusterKafka + "}",
                    // one instance stays up, so that its log holds every line the tests look for
                    "--set", "scaling.minScale=1",
                    // a single-node Kafka
                    "--set", "delivery.deadLetterTopicPartitions=1",
                    "--set", "delivery.deadLetterTopicReplicationFactor=1");
            run(moduleDir, "kubectl", "wait", "--namespace", namespace,
                    "ksvc,pingsource,kafkasource,kafkasink",
                    "--selector", "app.kubernetes.io/instance=" + RELEASE,
                    "--for=condition=Ready", "--timeout=300s");
            String url = run(moduleDir, "kubectl", "get", "ksvc", RELEASE, "--namespace", namespace,
                    "--output", "jsonpath={.status.url}").trim();
            baseUrl = url + ":" + ingressPort;
            System.out.println("✅ Service is up at " + baseUrl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deploy the Knative service", e);
        }
    }

    @Override
    public void cleanupTestFunctions() {
        try {
            run(moduleDir, "helm", "uninstall", RELEASE, "--namespace", namespace, "--ignore-not-found", "--wait");
        } catch (IOException e) {
            throw new RuntimeException("Failed to uninstall the release " + RELEASE, e);
        }
        baseUrl = null;
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
            String logs = run(moduleDir, "kubectl", "logs", "--namespace", namespace,
                    "--selector", "serving.knative.dev/service=" + RELEASE,
                    "--container", "user-container", "--tail=-1", "--max-log-requests=10");
            return Arrays.asList(logs.split("\\R"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the logs of " + RELEASE, e);
        }
    }

    @Override
    public void sendQueueMessage(String queueName, String message) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(queueName, message)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send a message to the Kafka topic " + queueName, e);
        }
        System.out.println("📨 Sent message to Kafka topic " + queueName);
    }

    @Override
    public void uploadObject(String bucket, String key, String content) {
        // no storage source on this target: post the event a storage notification would deliver
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

    private String baseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("The service is not deployed");
        }
        return baseUrl;
    }

    /** Runs a command and returns its standard output; fails with its output if it exits with an error. */
    private static String run(Path dir, String... command) throws IOException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(String.join(" ", command) + " did not finish within " + COMMAND_TIMEOUT);
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
