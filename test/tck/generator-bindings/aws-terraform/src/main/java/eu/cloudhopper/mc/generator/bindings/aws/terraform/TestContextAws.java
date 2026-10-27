package eu.cloudhopper.mc.generator.bindings.aws.terraform;

/*-
 * #%L
 * generator-bindings-aws-terraform - a library from the "Cloudhopper" project.
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
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class TestContextAws implements TestContext {

    private final CloudWatchLogsClient logsClient = CloudWatchLogsClient.create();
    private final Path terraformDir = Path.of(System.getProperty("user.dir"), "target", "deployment", "aws-terraform");
    private final Set<String> usedFunctions = new HashSet<>();
    // Same value Terraform reads as var.cloudhopper_name_prefix, so the TCK
    // resolves the prefixed Lambda names that were actually deployed.
    private final String namePrefix = System.getenv().getOrDefault("TF_VAR_cloudhopper_name_prefix", "");

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

        try (CloudWatchLogsClient logsClient = CloudWatchLogsClient.create()) {
            // 🧹 First, clean up log groups for used functions
            for (String functionName : usedFunctions) {
                cleanupLogGroup(logsClient, functionName);
            }
        } catch (Exception e) {
            System.out.println("❗ Error during CloudWatch cleanup: " + e.getMessage());
        }
        // Do not close the shared logsClient field here: the runner also calls this
        // method as a pre-deploy cleanup, and fetchLogs() still needs the client.

        // Then destroy infrastructure
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
        } catch (IOException ex) {
            Logger.getLogger(TestContextAws.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(TestContextAws.class.getName()).log(Level.SEVERE, null, ex);
        }
        return url;
    }

    @Override
    public Object invokeFunctionDirect(String functionName, Object input) {
        ObjectMapper mapper = new ObjectMapper();
        String sanitizedFunctionName = functionName.toLowerCase();
        usedFunctions.add(sanitizedFunctionName);
        try (LambdaClient lambdaClient = LambdaClient.create()) {
            String payload = input != null
                    ? new ObjectMapper().writeValueAsString(input)
                    : "{}";

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(namePrefix + sanitizedFunctionName)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);

            String responseString = response.payload().asUtf8String();
            // Try to parse as JSON
            JsonNode json = mapper.readTree(responseString);

            if (json.has("errorMessage")) {
                String error = json.get("errorMessage").asText();
                System.err.println("❗ Lambda returned error: " + error);

                try {
                    // Try to fetch logs (optional)
                    System.err.println("Fetch logs, wait 20s to stablize log streams");
                    Thread.sleep(20000);
                    List<String> logs = fetchLogs(functionName);
                    System.err.println("📜 Logs for " + functionName + ":");
                    logs.forEach(line -> System.err.println("  " + line));
                } catch (Exception logEx) {
                    System.err.println("⚠️ Could not fetch logs: " + logEx.getMessage());
                }

                throw new RuntimeException("Lambda error: " + error);
            }

            return mapper.treeToValue(json, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke Lambda function: " + namePrefix + sanitizedFunctionName, e);
        }
    }

    @Override
    public List<String> fetchLogs(String functionName) {
        String sanitizedFunctionName = functionName.toLowerCase();
        usedFunctions.add(sanitizedFunctionName);
        try {
            String logGroupName = "/aws/lambda/" + namePrefix + functionName;

            DescribeLogStreamsResponse streamsResponse = logsClient.describeLogStreams(DescribeLogStreamsRequest.builder()
                    .logGroupName(logGroupName)
                    .orderBy("LastEventTime")
                    .descending(true)
                    .limit(1)
                    .build());

            if (streamsResponse.logStreams().isEmpty()) {
                return List.of("[No log streams found]");
            }

            String streamName = streamsResponse.logStreams().get(0).logStreamName();

            GetLogEventsResponse events = logsClient.getLogEvents(GetLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(streamName)
                    .limit(50)
                    .startFromHead(false)
                    .build());

            return events.events().stream()
                    .map(OutputLogEvent::message)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of("[Failed to fetch logs: " + e.getMessage() + "]");
        }
    }

    private String functionNameToOutputKey(String functionName) {
        return functionName.toLowerCase() + "_url";
    }

    private void cleanupLogGroup(CloudWatchLogsClient logsClient, String functionName) {
        String logGroupName = "/aws/lambda/" + namePrefix + functionName.toLowerCase();
        try {
            DeleteLogGroupRequest deleteRequest = DeleteLogGroupRequest.builder()
                    .logGroupName(logGroupName)
                    .build();
            logsClient.deleteLogGroup(deleteRequest);
            System.out.println("✅ Successfully deleted log group: " + logGroupName);
        } catch (ResourceNotFoundException e) {
            System.out.println("ℹ️ Log group already deleted: " + logGroupName);
        } catch (Exception e) {
            System.out.println("❗ Error deleting log group " + logGroupName + ": " + e.getMessage());
        }
    }
}
