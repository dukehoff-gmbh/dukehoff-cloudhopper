---
title: Modules
sidebar_label: Modules
---

# Modules

All modules are published on Maven Central under the group `eu.cloudhopper.mc`,
with the same version. A project needs the API, one generator per target in
`provided` scope, and the runtime modules of that target; the
[archetype](maven-archetype.md) sets this up with a Maven profile per target.

## API and code generation

| Module | Scope | Contains |
|---|---|---|
| `deployment-config-api` | compile | The annotations (`@Function`, the triggers, `@Queue`, `@Extension`), `CloudRequestHandler`, `Publisher`, MicroProfile Config and its implementation; see [deployment-config-api](deployment-config-api.md) |
| `deployment-config-processor` | `provided` | The annotation processor `ServerlessFunctionProcessor`, which reads the annotations and calls the generator of the target |
| `deployment-config-generator-api` | `provided` | The interfaces of generators and the template registration; see [deployment-config-generator-api](deployment-config-generator-api.md) |
| `deployment-config-generator-generic` | `provided` | The FreeMarker-based generator that the generators below configure; see [deployment-config-generator-generic](deployment-config-generator-generic.md) |
| `deployment-config-processor-test-support` | test | A no-op generator for testing annotation processing |
| `cloudhopper-maven-archetype-core` | – | The [Maven archetype](maven-archetype.md) for new projects |

## Generators

A generator only runs in the annotation processor: add it in `provided` scope so it
stays out of the function jar. Each one registers its templates and declares the
annotations it supports; [Customizing targets](../guides/customizing-targets.md)
shows how to override templates or add a target.

| Module | Target |
|---|---|
| `generator-aws-terraform` | [AWS Lambda](../platforms/aws.md) |
| `generator-azure-terraform` | [Azure Functions](../platforms/azure.md) |
| `generator-gcp-terraform` | [Google Cloud Functions](../platforms/gcp.md) |
| `generator-springboot` | [Spring Boot](../platforms/springboot.md) |
| `generator-knative` | [Knative](../platforms/knative.md), on top of `generator-springboot` |
| `generator-scaleway-terraform` | [Scaleway](../platforms/scaleway.md), on top of `generator-springboot` |

## Runtime modules

The runtime modules end up in the function jar or the application. They contain the
base classes the generated handlers extend, and the clients that send messages.
Which ones a target needs is listed on its page, under *Runtime modules*.

| Module | Used by | Contains |
|---|---|---|
| `provider-aws` | AWS | `ApiGatewayEventRouter`, the Lambda entry point that routes API Gateway, SQS, S3, schedule and direct calls; `SqsEventHandler`, `S3EventHandler`; `SigV4Signer` and `AwsCredentials` for the AWS APIs the runtime calls; `SecretsManagerClient` and the `ConfigSource` for secrets |
| `provider-aws-publisher` | AWS, Scaleway | `@Queue` publishers for the SQS API |
| `provider-aws-blobstore` | AWS | `@Bucket` stores for S3 and S3-compatible storage |
| `provider-aws-keyvalue` | AWS | `@Table` stores for DynamoDB |
| `provider-azure` | Azure | `AzureBaseFunctionWrapper`, the base class of the generated function classes; `StorageAccount` for SharedKey requests to the function app's storage account; `KeyVaultClient` and the `ConfigSource` for secrets |
| `provider-azure-publisher` | Azure | `@Queue` publishers for Storage queues |
| `provider-azure-blobstore` | Azure | `@Bucket` stores for blob containers of the function app's storage account |
| `provider-azure-keyvalue` | Azure | `@Table` stores for Table Storage of the function app's storage account |
| `provider-gcp` | GCP | `GcpCloudFunctionRequestHandler`, the base class of the generated `HttpFunction`s; `GcpMetadata` for the project and access tokens |
| `provider-gcp-publisher` | GCP | `@Queue` publishers for Pub/Sub |
| `provider-gcp-blobstore` | GCP | `@Bucket` stores for Cloud Storage |
| `provider-gcp-keyvalue` | GCP | `@Table` stores for Firestore |
| `provider-jdbc-keyvalue` | Knative, Scaleway | `@Table` stores for PostgreSQL through JDBC |
| `provider-springboot` | Spring Boot, Knative, Scaleway | `SpringBootRequestHandler`, `SpringBootEventHandler`, the in-process and Kafka publishers and the Kafka delivery |
| `provider-http` | Spring Boot, Knative, Scaleway | `LocalHandlerContext`, the `HandlerContext` of HTTP services |

The publisher modules use small HTTP clients of their own instead of the cloud
SDKs, and register themselves through the service interface
`eu.cloudhopper.mc.runtime.spi.PublisherFactory`; an implementation of your own can
replace them. See [Sending messages](../guides/writing-functions.md#sending-messages).

The Javadoc is under **API reference** in the navigation bar.
