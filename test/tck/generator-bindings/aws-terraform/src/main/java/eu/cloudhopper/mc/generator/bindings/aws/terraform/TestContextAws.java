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
import eu.cloudhopper.mc.test.support.TerraformDataSources;
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
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class TestContextAws implements TestContext {

    private final CloudWatchLogsClient logsClient = CloudWatchLogsClient.create();
    private final Path terraformDir = Path.of(System.getProperty("user.dir"), "target", "deployment", "aws-terraform");
    private final Set<String> usedFunctions = new HashSet<>();
    // the tag the TCK's main.tf sets as default_tags: cost filter and daily leftover check
    private static final java.util.Map<String, String> TCK_TAGS = java.util.Map.of("cloudhopper-purpose", "tck");
    // Same value Terraform reads as var.cloudhopper_name_prefix, so the TCK
    // resolves the prefixed Lambda names that were actually deployed.
    private final String namePrefix = System.getenv().getOrDefault("TF_VAR_cloudhopper_name_prefix", "");

    @Override
    public void deployTestFunctions() {
        try {
            System.out.println("🌍 CWD (user.dir): " + System.getProperty("user.dir"));
            System.out.println("📁 Terraform dir: " + terraformDir.toAbsolutePath());
            createExistingResources();
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
        deleteExistingResources();
    }

    /**
     * Creates the queues and buckets of {@code @EventTrigger(create = false)} functions: the
     * generated Terraform reads them with data sources, under their names without prefix.
     */
    private void createExistingResources() throws IOException {
        try (SqsClient sqs = SqsClient.create()) {
            for (String queue : existingQueues()) {
                createQueue(sqs, CreateQueueRequest.builder().queueName(queue).tags(TCK_TAGS).build());
                System.out.println("📦 Created the existing queue " + queue);
            }
        }
        try (S3Client s3 = S3Client.create()) {
            String region = s3.serviceClientConfiguration().region().id();
            for (String bucket : existingBuckets()) {
                CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(bucket);
                if (!"us-east-1".equals(region)) {
                    request.createBucketConfiguration(CreateBucketConfiguration.builder().locationConstraint(region).build());
                }
                boolean created = true;
                try {
                    s3.createBucket(request.build());
                } catch (BucketAlreadyOwnedByYouException e) {
                    created = false; // left over from an earlier run
                }
                if (created) {
                    // A bucket created again shortly after a delete is not visible everywhere at
                    // once: Terraform's data source answered "couldn't find resource" seconds after
                    // the creation. Wait until it can be found, then give it a moment more.
                    s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucket).build());
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                s3.putBucketTagging(PutBucketTaggingRequest.builder().bucket(bucket)
                        .tagging(Tagging.builder().tagSet(TCK_TAGS.entrySet().stream()
                                .map(t -> Tag.builder().key(t.getKey()).value(t.getValue()).build()).toList()).build())
                        .build());
                System.out.println("📦 Created the existing bucket " + bucket);
            }
        }
    }

    /**
     * SQS refuses to recreate a queue within 60 seconds of deleting one with the same name,
     * which happens when the pre-deploy cleanup removed a queue left over from an aborted run.
     */
    private static void createQueue(SqsClient sqs, CreateQueueRequest request) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                sqs.createQueue(request);
                return;
            } catch (QueueDeletedRecentlyException e) {
                if (attempt == 3) {
                    throw e;
                }
                System.out.println("⏳ Queue " + request.queueName() + " was deleted recently; retrying in 65 s");
                try {
                    Thread.sleep(65_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to recreate queue " + request.queueName(), ie);
                }
            }
        }
    }

    private void deleteExistingResources() {
        try (SqsClient sqs = SqsClient.create(); S3Client s3 = S3Client.create()) {
            for (String queue : existingQueues()) {
                try {
                    String url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queue).build()).queueUrl();
                    sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(url).build());
                    System.out.println("🧹 Deleted the existing queue " + queue);
                } catch (QueueDoesNotExistException e) {
                    // nothing to delete
                }
            }
            for (String bucket : existingBuckets()) {
                try {
                    for (S3Object object : s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).build()).contents()) {
                        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build());
                    }
                    s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
                    System.out.println("🧹 Deleted the existing bucket " + bucket);
                } catch (NoSuchBucketException e) {
                    // nothing to delete
                }
            }
        } catch (IOException e) {
            System.out.println("❗ Could not read the existing resources: " + e.getMessage());
        }
    }

    private List<String> existingQueues() throws IOException {
        return TerraformDataSources.names(terraformDir, "aws_sqs_queue", "name");
    }

    private List<String> existingBuckets() throws IOException {
        return TerraformDataSources.names(terraformDir, "aws_s3_bucket", "bucket");
    }

    /** Existing resources have no prefix: the generated data sources look them up by their plain name. */
    private String deployedName(String name, List<String> existing) {
        return existing.contains(name) ? name : namePrefix + name;
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
    public java.util.Set<String> supportedExtensions() {
        return java.util.Set.of("x-aws-snapstart"); // ExtensionKeys.Aws.SnapStart.KEY
    }

    @Override
    public boolean isSnapStartActive(String functionName) {
        try (LambdaClient lambdaClient = LambdaClient.create()) {
            var configuration = lambdaClient.getFunctionConfiguration(GetFunctionConfigurationRequest.builder()
                    .functionName(namePrefix + functionName.toLowerCase())
                    .qualifier("live")
                    .build());
            System.out.println("📸 SnapStart of " + functionName + ":live (version " + configuration.version() + ", state "
                    + configuration.state() + ", last update " + configuration.lastUpdateStatus() + "): "
                    + configuration.snapStart());
            return configuration.state() == software.amazon.awssdk.services.lambda.model.State.ACTIVE
                    && configuration.snapStart() != null
                    && configuration.snapStart().applyOn() == software.amazon.awssdk.services.lambda.model.SnapStartApplyOn.PUBLISHED_VERSIONS
                    && configuration.snapStart().optimizationStatus() == software.amazon.awssdk.services.lambda.model.SnapStartOptimizationStatus.ON;
        }
    }

    @Override
    public String getDeployedIdentity(String functionName) {
        try (LambdaClient lambdaClient = LambdaClient.create()) {
            return lambdaClient.getFunctionConfiguration(GetFunctionConfigurationRequest.builder()
                    .functionName(namePrefix + functionName.toLowerCase())
                    .build()).role();
        }
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

    @Override
    public void sendQueueMessage(String queueName, String message) {
        try (SqsClient sqs = SqsClient.create()) {
            String name = deployedName(queueName, existingQueues());
            String url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
            String id = sqs.sendMessage(SendMessageRequest.builder().queueUrl(url).messageBody(message).build()).messageId();
            System.out.println("📨 Sent message " + id + " to SQS queue " + name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void uploadObject(String bucket, String key, String content) {
        try (S3Client s3 = S3Client.create()) {
            String name = deployedName(bucket, existingBuckets());
            s3.putObject(PutObjectRequest.builder().bucket(name).key(key).build(), RequestBody.fromString(content));
            System.out.println("📤 Uploaded " + key + " to S3 bucket " + name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteObject(String bucket, String key) {
        try (S3Client s3 = S3Client.create()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(deployedName(bucket, existingBuckets())).key(key).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
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
