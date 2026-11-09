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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

            // All traces of the app, oldest first. The function's own output (java.util.logging
            // via the worker's console) carries no operation name, so it cannot be filtered by
            // function; the caller looks for its line and fetches again while it is missing.
            String query = "traces | where timestamp > ago(15m) | order by timestamp asc | project message";

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

    /**
     * Puts a message on a queue of the function app's storage account (Queue service REST API,
     * Shared Key authorization). The Functions host expects Base64-encoded messages.
     */
    @Override
    public void sendQueueMessage(String queueName, String message) {
        try {
            // read the outputs directly: TerraformUtil.getOutputString would print the account key
            JsonNode outputs = TerraformUtil.readTerraformOutput(terraformDir);
            String account = outputs.path("function_storage_name").path("value").asText();
            String key = outputs.path("function_storage_primary_access_key").path("value").asText();

            String encoded = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
            byte[] body = ("<QueueMessage><MessageText>" + encoded + "</MessageText></QueueMessage>")
                    .getBytes(StandardCharsets.UTF_8);
            String date = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .format(ZonedDateTime.now(ZoneOffset.UTC));
            String version = "2021-08-06";
            String contentType = "application/xml";

            String stringToSign = "POST\n"
                    + "\n" // Content-Encoding
                    + "\n" // Content-Language
                    + body.length + "\n"
                    + "\n" // Content-MD5
                    + contentType + "\n"
                    + "\n" // Date (x-ms-date is used)
                    + "\n\n\n\n" // If-Modified-Since, If-Match, If-None-Match, If-Unmodified-Since
                    + "\n" // Range
                    + "x-ms-date:" + date + "\n"
                    + "x-ms-version:" + version + "\n"
                    + "/" + account + "/" + queueName + "/messages";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + account + ".queue.core.windows.net/" + queueName + "/messages"))
                    .header("x-ms-date", date)
                    .header("x-ms-version", version)
                    .header("Content-Type", contentType)
                    .header("Authorization", "SharedKey " + account + ":" + signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new IllegalStateException("Put Message returned " + response.statusCode() + ": " + response.body());
            }
            System.out.println("📨 Sent message to storage queue " + queueName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send a message to queue " + queueName, e);
        }
    }

    private String functionNameToOutputKey(String functionName) {
        return functionName.toLowerCase() + "_url";
    }
}
