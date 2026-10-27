package eu.cloudhopper.mc.generator.bindings.azure.terraform;

/*-
 * #%L
 * generator-bindings-azure-terraform - a library from the "Cloudhopper" project.
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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class TestContextAzure implements TestContext {

    private final Path terraformDir = Path.of(System.getProperty("user.dir"), "target", "deployment", "azure-terraform");
    private final Set<String> usedFunctions = new HashSet<>();
    private final String azureFunctionBaseUrl;

    public TestContextAzure(String azureFunctionBaseUrl) {
        this.azureFunctionBaseUrl = azureFunctionBaseUrl;  // e.g., "https://my-shared-function-app.azurewebsites.net/api/"
    }

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
    public String getHttpUrl(String functionId) {
        String url = null;
        usedFunctions.add(functionId);
        try {
            String key = functionNameToOutputKey(functionId);
            url = TerraformUtil.getOutputString(terraformDir, key);
            System.err.println("🌍 Resolved URL for function [" + functionId + "] key [" + key + "] → " + url);
        } catch (IOException | InterruptedException ex) {
            System.err.println("❗ Failed to resolve function URL: " + ex.getMessage());
        }
        return url;
    }

    public Object invokeFunctionDirect(String functionName, Object input) {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        try {
            String fullUrl = azureFunctionBaseUrl.endsWith("/")
                    ? azureFunctionBaseUrl + functionName + "_http_function_trigger"
                    : azureFunctionBaseUrl + "/" + functionName + "_http_function_trigger";
            System.err.println("full URL " + fullUrl);
            HttpRequest request;

            if (input == null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .GET()
                        .build();
            } else {
                String payload = mapper.writeValueAsString(input);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
            }

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            if (response.statusCode() >= 400) {
                System.err.println("❗ Azure Function returned error code " + response.statusCode());
                System.err.println("Response: " + body);
                throw new RuntimeException("Azure Function error: " + body);
            }

            JsonNode root = mapper.readTree(body);
            if (root.isObject() && root.has("result")) {
                return mapper.treeToValue(root.get("result"), Object.class);
            } else if (root.isValueNode()) {
                return mapper.treeToValue(root, Object.class);
            } else {
                return root;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke Azure Function: " + functionName, e);
        }
    }

    @Override
    public List<String> fetchLogs(String functionName) {
        try {
            String subscriptionId = System.getenv("ARM_SUBSCRIPTION_ID");
            String resourceGroup = TerraformUtil.getOutputString(terraformDir, "resource_group_name");
            String appInsightsName = TerraformUtil.getOutputString(terraformDir, "application_insights_name");
            String accessToken = getAzureAccessToken(); // OAuth2 token

            String query = "traces | where timestamp > ago(10m) | project message";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(
                            "https://management.azure.com/subscriptions/%s/resourceGroups/%s/providers/microsoft.insights/components/%s/query?api-version=2018-04-20",
                            subscriptionId, resourceGroup, appInsightsName)))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\": \"" + query + "\"}"))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // Retry loop
            long start = System.currentTimeMillis();
            long timeoutMs = 240_000; // 240 seconds timeout
            List<String> logs = new ArrayList<>();

            while ((System.currentTimeMillis() - start) < timeoutMs) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("🌐 Raw Azure REST Response:");
                System.out.println(response.body());
                JsonNode root = mapper.readTree(response.body());
                JsonNode tables = root.path("tables");

                logs.clear();
                if (tables.isArray() && tables.size() > 0) {
                    JsonNode rows = tables.get(0).path("rows");
                    for (JsonNode row : rows) {
                        logs.add(row.get(0).asText());
                    }
                }

                if (!logs.isEmpty()) {
                    System.out.println("✅ Found logs after " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
                    break;
                } else {
                    System.out.println("🔄 No logs yet, retrying...");
                    Thread.sleep(5000); // wait 5 seconds before retry
                }
            }

            if (logs.isEmpty()) {
                System.out.println("⚠ No logs found after waiting " + (timeoutMs / 1000) + " seconds.");
            }

            return logs;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Azure Function logs", e);
        }
    }

    public static String getAzureAccessToken() {
        try {
            String tenantId = System.getenv("ARM_TENANT_ID");
            String clientId = System.getenv("ARM_CLIENT_ID");
            String clientSecret = System.getenv("ARM_CLIENT_SECRET");

            if (tenantId == null || clientId == null || clientSecret == null) {
                throw new IllegalStateException("Azure credentials are not set (ARM_TENANT_ID, ARM_CLIENT_ID, ARM_CLIENT_SECRET)");
            }

            String body = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&scope=https%3A%2F%2Fmanagement.azure.com%2F.default";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Failed to fetch Azure access token: " + response.body());
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            return root.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain Azure access token", e);
        }
    }

    private String functionNameToOutputKey(String functionName) {
        return functionName.toLowerCase() + "_url";
    }
}
