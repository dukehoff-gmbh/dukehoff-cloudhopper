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
import java.nio.file.Path;
import java.util.*;

public class TestContextGcp implements TestContext {

    private final Path terraformDir = Path.of(System.getProperty("user.dir"), "target", "deployment", "gcp-terraform");
    private final Set<String> usedFunctions = new HashSet<>();

    @Override
    public void deployTestFunctions() {
        try {
            System.out.println("🌍 CWD (user.dir): " + System.getProperty("user.dir"));
            System.out.println("📁 Terraform dir: " + terraformDir.toAbsolutePath());
            TerraformDeployer deployer = new TerraformDeployer(terraformDir);
            deployer.init();
            deployer.apply();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deploy functions via Terraform", e);
        }
    }

    @Override
    public void cleanupTestFunctions() {
        try {
            new TerraformDeployer(terraformDir).destroy();
        } catch (Exception e) {
            throw new RuntimeException("Failed to destroy deployed functions", e);
        }
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
            ObjectNode payloadJson = mapper.createObjectNode();
            if (input != null) {
                payloadJson.set("data", mapper.valueToTree(input));
            } else {
                payloadJson.putNull("data");
            }

            HttpRequest request;
            if (input == null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(functionUrl))
                        .header("Authorization", "Bearer " + idToken)
                        .GET()
                        .build();
            } else {
                String payload = mapper.writeValueAsString(payloadJson);
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

    private String functionNameToOutputKey(String functionName) {
        return functionName.toLowerCase().replace("function", "") + "_url";
    }
}
