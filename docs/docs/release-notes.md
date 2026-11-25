---
id: release-notes
title: Release notes
---

# Release notes

What changed in each release. Releases are published on Maven Central and
announced on [GitHub](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/releases),
together with the full list of changes. What is being worked on is tracked in the
[issues](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/issues).

## 1.6

- Buckets: a function reads and writes objects through a `BlobStore` parameter
  annotated with `@Bucket`, declaring `READ`, `WRITE` or `READ_WRITE` access: S3 on
  AWS, Cloud Storage on GCP, Blob Storage on Azure, Object Storage on Scaleway,
  S3-compatible storage on Knative, and directories (or S3) on Spring Boot. The
  generator creates the buckets unless they are `existing`, and on AWS and GCP
  gives each identity exactly the declared permissions; see
  [Reading and writing buckets](guides/writing-functions.md#reading-and-writing-buckets)
- Tables: a function reads and writes items of a key-value table (partition and
  sort key, typed attributes, prefix queries, conditional updates) through a
  `KeyValueStore` parameter annotated with `@Table`: DynamoDB on AWS, Firestore
  on GCP, Table Storage on Azure, PostgreSQL on Knative (through JDBC) and on
  Scaleway (a Serverless SQL Database), in memory or PostgreSQL on Spring Boot.
  Reads are strongly consistent on every target; see
  [Reading and writing tables](guides/writing-functions.md#reading-and-writing-tables)
  and the Tables section of each [platform](platforms/generator-overview.md)
- Tables on GCP need the Firestore API and the Cloud Resource Manager API; on
  Scaleway the container connects as an IAM application with the permission set
  `ServerlessSQLDatabaseReadWrite`, passed in the variable
  `cloudhopper_keyvalue_credentials`; see
  [Scaleway](platforms/scaleway.md#tables)

## 1.5.1

- AWS: functions with [SnapStart](platforms/aws.md#snapstart) read secrets and send
  messages with `@Queue`; before, they failed with "No AWS credentials in the
  environment", because Lambda passes the credentials of SnapStart functions through
  the container credentials endpoint instead of environment variables

## 1.5

- Secrets: a property read with `@ConfigProperty` can reference a secret in the
  target's secret store instead of a value, with the Terraform variable
  `cloudhopper_secrets`: Secrets Manager on AWS, Key Vault on Azure, Secret
  Manager on GCP; on Knative a Kubernetes secret, on Scaleway a secret
  environment variable. A function's identity may read exactly the secrets its
  functions read; see [Secrets](guides/writing-functions.md#secrets)
- Values from your own Terraform, such as the ID of a resource created next to
  the functions, go into the locals `cloudhopper_extra_config` and
  `cloudhopper_extra_secrets` in an override file; see
  [Values from your own Terraform](platforms/generator-overview.md#values-from-your-own-terraform)
- GCP: every identity becomes a service account of its own, with
  `roles/pubsub.publisher` on exactly the topics its functions send to; see
  [Identities and permissions](platforms/gcp.md#identities-and-permissions)
- Coding agents: projects created with the archetype contain an `AGENTS.md` and
  the Agent Skill `cloudhopper`; existing projects can add the skill, and the
  documentation is listed in `/llms.txt`; see
  [Coding agents](modules/maven-archetype.md#coding-agents)
- Azure: routes match literal path segments regardless of case, so functions
  with upper-case letters in their name receive their path parameters

### Upgrading from 1.4

- **GCP:** the functions no longer run as the default compute service account.
  Grant what a function needs beyond its triggers and topics to the service
  account of its identity. Deploying needs `roles/iam.serviceAccountAdmin`,
  `roles/iam.serviceAccountUser` and `roles/pubsub.admin`; see
  [GCP](platforms/gcp.md#identities-and-permissions)

## 1.4

- `@Queue`: functions send messages to queues with a `Publisher<T>` parameter, on
  every target: SQS on AWS and Scaleway, Pub/Sub on GCP, Storage queues on
  Azure, Kafka on Knative and Spring Boot. The runtime modules use small HTTP
  clients of their own instead of the cloud SDKs; see
  [Sending messages](guides/writing-functions.md#sending-messages)
- Configuration with MicroProfile Config: `@ConfigProperty` on a parameter or
  `ConfigProvider.getConfig()`, with the values per deployment in the Terraform
  variable `cloudhopper_config`; see
  [Configuration](guides/writing-functions.md#configuration)
- AWS: SnapStart with the vendor extension `x-aws-snapstart`; see
  [SnapStart](platforms/aws.md#snapstart)
- AWS: a direct invocation of a function passes its input to the function

## 1.3.1

- `@Function(identity)`: on AWS every function runs as an execution role of its
  own, or functions share one by declaring the same identity. A role has logging
  and the permissions of its functions' triggers; the read access to all buckets
  of the account is gone. See
  [Identities and permissions](guides/writing-functions.md#identities-and-permissions)
- Numbers of 1000 and above, such as `memory = 1024`, are generated without a
  thousands separator; with 1.3.0 they depended on the build machine's locale

## 1.3

- Spring Boot 4.1 for the targets `springboot-http`, `knative` and
  `scaleway-terraform`
- Smaller function jars: the generator is a build-time dependency, and the
  function only needs the API and a small runtime library per cloud
  (`provider-aws`, `provider-azure`, `provider-gcp`). The AWS runtime no longer
  uses the AWS SDK v1
- AWS: the API is served on the `$default` stage, Terraform manages the log
  groups with a retention (`log_retention_days`), and new variables for the
  stage and the artifact bucket
- The generated Terraform declares the Terraform versions it supports; the
  archetype adds provider version constraints to `main.tf`
- Projects created with the archetype deploy without changes
- Generated handlers support parameterized types and methods that declare
  checked exceptions
- Request data is no longer written to the function log

### Upgrading from 1.2

- **Dependencies:** add the generator in `provided` scope and the runtime
  library of the cloud, e.g. `provider-aws`; see the
  [AWS generator](platforms/aws.md#runtime-modules)
- **Spring Boot targets:** the application needs Spring Boot 4: replace
  `spring-boot-starter-web` with `spring-boot-starter-webmvc`, and for Kafka add
  `spring-boot-starter-kafka`; see
  [Spring Boot](platforms/springboot.md)
- **AWS:** URLs no longer contain `/test`; set `api_stage_name = "test"` to keep
  them. Import the existing log groups once before the next `apply`; see
  [AWS](platforms/aws.md)
- **Terraform:** add provider version constraints to your `main.tf`; see
  [Terraform and provider versions](platforms/generator-overview.md#terraform-and-provider-versions)

## 1.2

- New target `knative`: a Helm chart for any Kubernetes cluster with Knative
  Serving and Eventing; schedules as `PingSource`, queues from Kafka with a
  dead-letter topic
- New target `scaleway-terraform`: Scaleway Serverless Containers via
  Terraform; cron triggers and Scaleway Queues with a dead-letter queue
- Both new targets run the Spring Boot application and are certified by the TCK
- GCP applies `@Function(minInstances)`
- The TCK now checks every attribute a target claims; claims without an
  implementation were removed, so the build warns about them
- Targets can claim single values of an attribute, such as
  `@EventTrigger(source = QUEUE)`; the support matrix shows them

## 1.1

- `@EventTrigger` for queues and storage buckets on all four targets, with
  `EventMetadata` and at-least-once delivery
- Spring Boot: executable jar from the archetype, a direct endpoint
  `/functions/<function name>` for every function, `@ScheduledTrigger`
  (in-process and `POST /schedules/<function name>`), queues from Kafka
- The Spring Boot target is certified by the TCK like the cloud targets
- Generated [support matrix](platforms/generator-overview.md#support-matrix) and
  build warnings for anything a target ignores
- [API reference](pathname:///api/index.html) (Javadoc) on this site

## 1.0

- First release on Maven Central: `@Function`, `@HttpTrigger` and
  `@ScheduledTrigger` with deployments generated for AWS, Azure and GCP
  (Terraform) and Spring Boot, and the Maven archetype
- Use 1.0.1: in 1.0.0 the archetype generates broken `springboot-http` and
  `azure-terraform` profiles
