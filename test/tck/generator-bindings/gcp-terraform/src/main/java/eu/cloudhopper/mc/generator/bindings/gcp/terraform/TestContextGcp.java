package eu.cloudhopper.mc.generator.bindings.gcp.terraform;

/*-
 * #%L
 * generator-bindings-gcp-terraform - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.test.support.TerraformDataSources;
import eu.cloudhopper.mc.test.support.TerraformDeployer;
import eu.cloudhopper.mc.test.support.TerraformUtil;
import eu.cloudhopper.mc.test.tck.api.TestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.logging.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.google.cloud.ServiceOptions;
import java.util.*;

public class TestContextGcp implements TestContext {

    private final Path terraformDir = Path.of(System.getProperty("user.dir"), "target", "deployment", "gcp-terraform");
    private final Set<String> usedFunctions = new HashSet<>();

    @Override
    public void deployTestFunctions() {
        try {
            System.out.println("🌍 CWD (user.dir): " + System.getProperty("user.dir"));
            System.out.println("📁 Terraform dir: " + terraformDir.toAbsolutePath());
            createExistingResources();
            TerraformDeployer deployer = new TerraformDeployer(terraformDir, terraformEnvironment());
            deployer.init();
            deployer.apply();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deploy functions via Terraform", e);
        }
    }

    @Override
    public void cleanupTestFunctions() {
        try {
            new TerraformDeployer(terraformDir, terraformEnvironment()).destroy();
        } catch (Exception e) {
            throw new RuntimeException("Failed to destroy deployed functions", e);
        }
        deleteExistingResources();
    }

    /**
     * Creates the topics and buckets of {@code @EventTrigger(create = false)} functions, which the
     * generated Terraform reads with data sources. Buckets go to the region the functions use.
     */
    private void createExistingResources() throws Exception {
        String project = ServiceOptions.getDefaultProjectId();
        for (String topic : TerraformDataSources.names(terraformDir, "google_pubsub_topic", "name")) {
            HttpResponse<String> response = googleApi("PUT",
                    "https://pubsub.googleapis.com/v1/projects/" + project + "/topics/" + topic, "{}");
            if (response.statusCode() >= 300 && response.statusCode() != 409) {
                throw new IllegalStateException("Creating topic " + topic + " returned " + response.statusCode() + ": " + response.body());
            }
            System.out.println("📦 Created the existing topic " + topic);
        }
        String region = System.getenv().getOrDefault("GCP_REGION", "us-central1");
        for (String bucket : TerraformDataSources.names(terraformDir, "google_storage_bucket", "name")) {
            ObjectNode body = new ObjectMapper().createObjectNode().put("name", bucket).put("location", region);
            body.putObject("iamConfiguration").putObject("uniformBucketLevelAccess").put("enabled", true);
            HttpResponse<String> response = googleApi("POST",
                    "https://storage.googleapis.com/storage/v1/b?project=" + project, body.toString());
            if (response.statusCode() >= 300 && response.statusCode() != 409) {
                throw new IllegalStateException("Creating bucket " + bucket + " returned " + response.statusCode() + ": " + response.body());
            }
            System.out.println("📦 Created the existing bucket " + bucket);
        }
    }

    private void deleteExistingResources() {
        try {
            String project = ServiceOptions.getDefaultProjectId();
            for (String topic : TerraformDataSources.names(terraformDir, "google_pubsub_topic", "name")) {
                if (googleApi("DELETE", "https://pubsub.googleapis.com/v1/projects/" + project + "/topics/" + topic, null).statusCode() < 300) {
                    System.out.println("🧹 Deleted the existing topic " + topic);
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            for (String bucket : TerraformDataSources.names(terraformDir, "google_storage_bucket", "name")) {
                HttpResponse<String> objects = googleApi("GET", "https://storage.googleapis.com/storage/v1/b/" + bucket + "/o", null);
                if (objects.statusCode() == 200) {
                    for (JsonNode item : mapper.readTree(objects.body()).path("items")) {
                        googleApi("DELETE", "https://storage.googleapis.com/storage/v1/b/" + bucket + "/o/"
                                + java.net.URLEncoder.encode(item.path("name").asText(), StandardCharsets.UTF_8), null);
                    }
                }
                if (googleApi("DELETE", "https://storage.googleapis.com/storage/v1/b/" + bucket, null).statusCode() < 300) {
                    System.out.println("🧹 Deleted the existing bucket " + bucket);
                }
            }
        } catch (Exception e) {
            System.out.println("❗ Could not delete the existing resources: " + e.getMessage());
        }
    }

    private HttpResponse<String> googleApi(String method, String url, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + getAccessToken());
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public String getHttpUrl(String functionName) {
        try {
            usedFunctions.add(functionName);
            String key = functionNameToOutputKey(functionName);
            String url = TerraformUtil.getOutputString(terraformDir, key);
            return url;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch HTTP URL from Terraform output", e);
        }
    }

    @Override
    public int getDeployedMinInstances(String functionName) {
        usedFunctions.add(functionName);
        try {
            // Terraform reads the function back from the API after apply, so the output is
            // the setting GCP stores, not only the requested one
            return Integer.parseInt(TerraformUtil.getOutputString(terraformDir, functionName + "_min_instance_count").trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read min_instance_count of " + functionName + " from the Terraform output", e);
        }
    }

    @Override
    public String getDeployedScheduleTimezone(String functionName) {
        usedFunctions.add(functionName);
        try {
            // read back from the Cloud Scheduler job after apply
            return TerraformUtil.getOutputString(terraformDir, functionName + "_schedule_time_zone").trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read the time zone of " + functionName + " from the Terraform output", e);
        }
    }

    @Override
    public String getApiDescription() {
        try {
            // the OpenAPI description the API Gateway config was created from
            return TerraformUtil.getOutputString(terraformDir, "api_openapi_description");
        } catch (Exception e) {
            throw new RuntimeException("Failed to read the OpenAPI description from the Terraform output", e);
        }
    }

    private static String fetchIdToken(String targetUrl) throws IOException {
        IdTokenCredentials credentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider((IdTokenProvider) GoogleCredentials.getApplicationDefault())
                .setTargetAudience(targetUrl)
                .build();

        credentials.refresh();
        return credentials.getAccessToken().getTokenValue();
    }

    @Override
    public Object invokeFunctionDirect(String functionName, Object input) {
        usedFunctions.add(functionName);
        try {
            String outputKey = functionName + "_direct_url";
            String functionUrl = TerraformUtil.getOutputString(terraformDir, outputKey);

            if (functionUrl == null || functionUrl.isEmpty()) {
                throw new IllegalStateException("No function URL found in Terraform output for " + outputKey);
            }

            String idToken = fetchIdToken(functionUrl);

            System.out.println("Calling " + functionUrl);

            ObjectMapper mapper = new ObjectMapper();

            HttpRequest request;
            if (input == null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(functionUrl))
                        .header("Authorization", "Bearer " + idToken)
                        .GET()
                        .build();
            } else {
                // the function's input is the request body itself, as for every HTTP call of the function
                String payload = mapper.writeValueAsString(input);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(functionUrl))
                        .header("Authorization", "Bearer " + idToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
            }

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response " + response.body());

            JsonNode root = mapper.readTree(response.body());
            if (root.isObject() && root.has("result")) {
                return mapper.treeToValue(root.get("result"), Object.class);
            } else if (root.isValueNode()) {
                return mapper.treeToValue(root, Object.class);
            } else {
                return root;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke GCP function: " + functionName, e);
        }
    }

    @Override
    public List<String> fetchLogs(String functionName) {
        try (Logging logging = LoggingOptions.getDefaultInstance().getService()) {
            String filter = String.format(
                    "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"%s\"",
                    functionName
            );

            Page<LogEntry> entries = logging.listLogEntries(Logging.EntryListOption.filter(filter));

            List<String> logs = new ArrayList<>();
            for (LogEntry entry : entries.iterateAll()) {
                if (entry.getPayload() != null) {
                    switch (entry.getPayload().getType()) {
                        case STRING:
                            logs.add(entry.getPayload().getData().toString());
                            break;
                        case JSON:
                            logs.add(entry.getPayload().getData().toString());
                            break;
                        case PROTO:
                            logs.add("[ProtoPayload] " + entry.getPayload().toString());
                            break;
                        default:
                            logs.add("[UnknownPayload] " + entry.getPayload().toString());
                            break;
                    }
                } else {
                    logs.add("[NoPayload] Empty log entry");
                }
            }
            return logs;
        } catch (Exception e) {
            return List.of("❗ Failed to fetch logs: " + e.getMessage());
        }
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    /**
     * Publishes a message to the Pub/Sub topic of that name in the default project.
     */
    @Override
    public void sendQueueMessage(String queueName, String message) {
        try {
            String project = ServiceOptions.getDefaultProjectId();
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode body = mapper.createObjectNode();
            body.putArray("messages").addObject()
                    .put("data", Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://pubsub.googleapis.com/v1/projects/" + project + "/topics/" + queueName + ":publish"))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Publish returned " + response.statusCode() + ": " + response.body());
            }
            System.out.println("📨 Published to Pub/Sub topic " + queueName + ": " + response.body());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish a message to topic " + queueName, e);
        }
    }

    /**
     * Uploads an object with the Cloud Storage JSON API (simple upload).
     */
    @Override
    public void uploadObject(String bucket, String key, String content) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://storage.googleapis.com/upload/storage/v1/b/" + bucket
                            + "/o?uploadType=media&name=" + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(content))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Upload returned " + response.statusCode() + ": " + response.body());
            }
            System.out.println("📤 Uploaded " + key + " to Cloud Storage bucket " + bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload " + key + " to bucket " + bucket, e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://storage.googleapis.com/storage/v1/b/" + bucket
                            + "/o/" + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .DELETE()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300 && response.statusCode() != 404) {
                throw new IllegalStateException("Delete returned " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete " + key + " from bucket " + bucket, e);
        }
    }

    private String functionNameToOutputKey(String functionName) {
        return functionName.toLowerCase().replace("function", "") + "_url";
    }

    /**
     * The TCK's configuration, and fewer parallel operations: deploying and destroying all test
     * functions at once exceeded Cloud Run's quota of write requests per minute and region.
     */
    private static java.util.Map<String, String> terraformEnvironment() {
        java.util.Map<String, String> environment = new java.util.HashMap<>(
                eu.cloudhopper.mc.test.tck.api.TckConfiguration.terraformEnvironment());
        environment.put("TF_CLI_ARGS_apply", "-parallelism=4");
        environment.put("TF_CLI_ARGS_destroy", "-parallelism=4");
        return environment;
    }
}
