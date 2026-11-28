---
title: AWS Lambda
sidebar_label: AWS Lambda
---

# AWS Lambda

The profile `aws-terraform` deploys every function as an AWS Lambda function, with
Terraform for the infrastructure: API Gateway (HTTP API) for HTTP triggers,
EventBridge for schedules, SQS and S3 for events. Set up the account and the tools
with the [AWS setup guide](../setup/aws-prerequisites.md).

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | Lambda handler classes; `Auto` routes API Gateway, SQS, S3, schedule and direct calls |
| `shared.ftl` | Terraform | Artifact bucket |
| `identity.ftl` | Terraform | Execution role per identity |
| `function.ftl` | Terraform | Lambda function |
| `integration.ftl` | Terraform | API Gateway integration and permission per HTTP function |
| `api.ftl` | Terraform | HTTP API with its OpenAPI description |
| `schedule.ftl` | Terraform | EventBridge rule |
| `queue.ftl` | Terraform | SQS queue and event source mapping |
| `storage.ftl` | Terraform | S3 bucket and event notification |

## Running a scheduled function by hand

A `@ScheduledTrigger` function can be invoked directly, e.g. after a deployment or to try a new
build. It accepts an EventBridge scheduled event, and an empty object as well; both run it
without input:

```bash
aws lambda invoke --function-name <prefix><function> --payload '{}' \
  --cli-binary-format raw-in-base64-out response.json
```

With [SnapStart](#snapstart), add `--qualifier live` to run the published version.

## Terraform variables

The generated Terraform has these variables for the deployment as a whole; set them
in a `.tfvars` file or with `-var`:

| Variable | Default | Effect |
|---|---|---|
| `api_stage_name` | `$default` | API Gateway stage. `$default` serves the API without a stage segment (`https://<api>.execute-api.<region>.amazonaws.com/players`); any other name becomes the first path segment. On `$default`, API Gateway answers `/ping` and `/sping` with its own health check; a function on one of these paths makes `terraform plan` fail with a hint, so set a stage name or change the path |
| `log_retention_days` | `30` | Retention of the log groups `/aws/lambda/<function>`, which Terraform creates before the functions |
| `artifact_bucket_force_destroy` | `false` | Lets `terraform destroy` delete the artifact bucket even if it contains objects Terraform does not manage |
| `cloudhopper_name_prefix` | empty | Prefix for all resource names, e.g. for several deployments in one account |

## SnapStart

Java functions start slowly on a new instance: the JVM starts and the function is
initialized on the first request. With Lambda SnapStart, Lambda initializes a published
version once, takes a snapshot of the initialized environment, and starts new instances
from that snapshot:

```java
@Function(name = "hello", extensions = @Extension(
        name = ExtensionKeys.Aws.SnapStart.KEY,
        value = ExtensionKeys.Aws.SnapStart.Values.PUBLISHED_VERSIONS))
```

For such a function, every `terraform apply` that changes it publishes a new version, and the
alias `live` points to it. API Gateway, EventBridge schedules, SQS and S3 invoke the
alias; invoking the function without a qualifier runs `$LATEST` without SnapStart.

Until Lambda has taken the snapshot, a new version is `Pending` and cannot be invoked.
The generated Terraform therefore waits with `aws lambda wait published-version-active`
before it moves the alias, so the triggers keep invoking the previous version during the
deployment. This needs the [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
on the machine running Terraform, with the same credentials as Terraform, and it
adds the time of the snapshot (usually under a minute) to the deployment.

Before the snapshot, the generated handler primes itself: it runs the path of a request once
without calling the function (the conversion of an API Gateway event, Jackson for the function's
body type, the responses of a result, an error and an invalid body), so that restored instances
do not load and compile it on their first request. In a measurement (arm64, 512 MB, an HTTP
function with a JSON body) the first request after a restore took about 380 ms instead of 1.4 s,
and the cold start about 840 ms instead of 1.9 s. The function's own code is not run before the
snapshot, so it has no effects on tables, queues or buckets. To warm clients or caches of your
own, annotate a method of the function class with `@OnPrime`; it is called before the snapshot:

```java
public class Prices {
    private final PriceList prices = new PriceList();

    @OnPrime
    void prime() {
        prices.load();   // e.g. read the price list from a bucket once
    }

    @Function(name = "price", extensions = @Extension(name = ExtensionKeys.Aws.SnapStart.KEY,
            value = ExtensionKeys.Aws.SnapStart.Values.PUBLISHED_VERSIONS))
    public Price price(@PathParam String sku) { ... }
}
```

The method has no parameters and is not private; an exception is logged and does not stop the
snapshot. Without SnapStart, it is not called. For the shortest cold
starts, see [Native image](#native-image).

All instances restored from one snapshot share the state created during initialization:
create random values, unique IDs and network connections in the function call, not in
constructors or static initializers, or renew them in a `org.crac` `afterRestore` hook.

Terraform does not delete old versions; they count toward Lambda's code storage quota per
region. SnapStart cannot be combined with provisioned concurrency.

## Native image

The functions can also run as a GraalVM native image on the Lambda custom runtime
(`provided.al2023`) instead of the Java runtime. A native image starts in a fraction of the
time and needs about a third of the memory: for a function with a JSON body, a cold start took
about 140 ms instead of 1.2–1.8 s on the Java runtime (512–1024 MB) and about 840 ms with
SnapStart.

Build with the profile `aws-native` in addition to `aws-terraform`:

```bash
mvn package -Paws-terraform,aws-native
```

- The profile runs the goal `cloudhopper:native-image` of the `cloudhopper-maven-plugin`: it
  builds the executable `bootstrap` with GraalVM in a container, so it needs Docker (native
  images cannot be cross-compiled), and packs it into
  `target/<artifactId>-<version>-aws-terraform-native.zip`. The build takes about a minute and
  a few GB of memory; `-Dnative.options=-J-Xmx3g` limits it, `-Dnative.image=...` selects
  another GraalVM image.
- `mvn verify -Paws-terraform,aws-native` also runs `cloudhopper:native-smoke`: it starts the
  `bootstrap` locally in the Lambda base image `public.ecr.aws/lambda/provided:al2023`, once per
  function, and runs the request path of each (as the SnapStart priming does, without the
  function's code, so it needs no cloud access). Errors that exist only in a native image, such
  as missing reflection metadata, fail the build there instead of after the deployment. To run
  functions themselves, list events in `src/test/native-smoke/events.json`:

  ```json
  [
    {"function": "price", "event": {"customer": "acme"}, "environment": {"PRICE_LIST": "test"}}
  ]
  ```

  An event fails the build when the invocation fails or an HTTP function answers 5xx;
  `-Dnative.smoke.function=<name>` runs one function, `-Dnative.smoke.skip` skips the goal.
- The generated Terraform deploys that zip with `runtime = "provided.al2023"`. One executable
  serves all functions; `eu.cloudhopper.mc.provider.aws.NativeRuntime` creates the handler the
  function's handler setting names.
- The executable is built for `arm64` (Graviton) by default, and all functions run on that
  architecture. For x86 set `-Dnative.architecture=x86_64`; building for an architecture other
  than the machine's runs emulated and is much slower. A function whose
  `ExtensionKeys.Aws.Architecture` extension asks for another architecture does not compile.
- SnapStart is only available for the Java runtime: the extension is ignored for a native image
  (with a warning).
- Jackson binds the functions' body, result and queue message types reflectively. The annotation
  processor generates the reflection configuration for these types and the types they contain
  (`META-INF/native-image/cloudhopper/<function>/reachability-metadata.json`), with their
  array types.
- Types the application reads or writes as JSON itself, e.g. records it stores in a table or
  returns inside a `Map`, are marked with `@Reflective`; the types they contain come along:

  ```java
  @Reflective
  public record Measurement(String detected, List<Day> days) { }
  ```

  `@Reflective(types = {...})` registers types of a library the same way. Each type gets a file
  of its own (`META-INF/native-image/cloudhopper/types/<type>/`). As an escape hatch,
  `-AnativeReflectionPackages=<package>` (archetype property `native.reflection.packages`)
  registers the classes of a package themselves, without the types they refer to.
- Missing metadata fails with an `Error` (`MissingReflectionRegistrationError`), not an
  exception: the function answers 500, and the log names the type.
- Code of your own that uses reflection, dynamic proxies or resources needs
  [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
  of its own; libraries often bring it, or the
  [tracing agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)
  records it.

## Sending messages

A function with a [`@Queue`](../guides/writing-functions.md#sending-messages)
parameter needs the dependency `eu.cloudhopper.mc:provider-aws-publisher`; the
archetype adds it. It sends through the SQS API, signed with the credentials of
the function's execution role, without the AWS SDK. The generated Terraform passes
the queue URL as the environment variable `CLOUDHOPPER_QUEUE_<NAME>_URL` and grants
the function's identity `sqs:SendMessage` on exactly that queue.

## Buckets

A function with a [`@Bucket`](../guides/writing-functions.md#reading-and-writing-buckets)
parameter needs the dependency `eu.cloudhopper.mc:provider-aws-blobstore`; the
archetype adds it. It calls the S3 REST API, signed with the credentials of the
function's execution role, without the AWS SDK. The generated Terraform

- creates the S3 bucket `<cloudhopper_name_prefix><name>`, unless a storage
  trigger of the project already creates it, the parameter is `existing`, or the
  local `cloudhopper_bucket_names` names a bucket of your own;
- passes the bucket's name as the environment variable
  `CLOUDHOPPER_BUCKET_<NAME>_NAME` and outputs it as `bucket_<name>`;
- grants the function's identity `s3:ListBucket` and `s3:GetObject` for `READ`,
  `s3:PutObject` and `s3:DeleteObject` for `WRITE`, on exactly this bucket.

## Tables

A function with a [`@Table`](../guides/writing-functions.md#reading-and-writing-tables)
parameter needs the dependency `eu.cloudhopper.mc:provider-aws-keyvalue`; the
archetype adds it. It calls the DynamoDB API, signed with the credentials of the
function's execution role, without the AWS SDK, and reads with
`ConsistentRead`. The generated `api_tables.tf`

- creates the DynamoDB table `<cloudhopper_name_prefix><name>` (on demand, the
  string partition key `_pk` and the string sort key `_sk`), unless the parameter
  is `existing` or the local `cloudhopper_table_names` names a table of your own,
  which needs the same keys;
- passes the table's name as the environment variable
  `CLOUDHOPPER_TABLE_<NAME>_NAME` and outputs it as `table_<name>`;
- grants the function's identity `dynamodb:GetItem` and `dynamodb:Query` for
  `READ`, `dynamodb:PutItem`, `dynamodb:UpdateItem`, `dynamodb:DeleteItem` and
  `dynamodb:BatchWriteItem` for `WRITE`, on exactly this table.

Destroying the deployment deletes the tables it created, with their items.

## Secrets

The Terraform variable `cloudhopper_secrets` maps a property name to a secret in
AWS Secrets Manager, by ARN or by name:

```hcl
cloudhopper_secrets = {
  "prices.gcp-credentials" = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:gcp-credentials-AbCdEf"
  "report.api-key"         = "report-api-key"
}
```

- A function gets the references of the properties it reads in the environment
  variable `CLOUDHOPPER_SECRETS`. On first access, `provider-aws` reads the value
  with `GetSecretValue`, signed with the credentials of the execution role,
  without the AWS SDK. The value stays in memory for the life of the instance.
  A binary secret is read as UTF-8.
- The role of the function's identity gets `secretsmanager:GetSecretValue` on
  exactly these secrets (policy `<function>-secrets`). A secret given by name
  matches any ARN of that name with the random suffix that Secrets Manager
  appends. A secret encrypted with a customer managed KMS key also needs
  `kms:Decrypt` on that key; attach it to `aws_iam_role.identity_<identity>`.
- `terraform plan` fails if a property is set in both `cloudhopper_config` and
  `cloudhopper_secrets`. A property without `defaultValue` must be in one of the
  two maps.
- Create the secrets yourself, in your own Terraform or outside it; the generated
  Terraform only references them.

A non-secret parameter, e.g. from the SSM Parameter Store, needs no runtime lookup:
pass its value through `cloudhopper_config`, for example read in the deployment
pipeline and set as `TF_VAR_cloudhopper_config` or in a `.tfvars` file.

## Identities and permissions

Every [identity](../guides/writing-functions.md#identities-and-permissions) becomes an
IAM role named `<cloudhopper_name_prefix><project_name>-<identity>`, the execution role
of its functions. The role has `AWSLambdaBasicExecutionRole` for logging and, for
queue-triggered functions, the permissions to receive and delete the queue's messages.
Storage triggers need no permission on the role: S3 invokes the function through a
resource-based permission.

Attach further permissions in your own `.tf` file next to `main.tf`, to the role
`aws_iam_role.identity_<identity>`:

```hcl
resource "aws_iam_role_policy" "connector_assume_reader" {
  role = aws_iam_role.identity_connector.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "sts:AssumeRole"
      Resource = "arn:aws:iam::*:role/ReaderRole"
    }]
  })
}
```

The outputs `identity_<identity>_role_name` and `identity_<identity>_role_arn` give the
role's name and ARN, e.g. for a trust policy in another account. The name stays the same
as long as the identity does. IAM limits it to 64 characters; a longer name makes
`terraform plan` fail with a hint to shorten `project_name`, the prefix or the identity.

:::note Upgrading from 1.3.0 or earlier
All functions used to share the role `<project_name>-lambda-execution-role`, which also
had `AmazonS3ReadOnlyAccess` (read access to every bucket in the account). Terraform
replaces it with one role per identity. Permissions you attached to
`aws_iam_role.lambda_exec` now belong to the role of the identity that needs them, and a
function that read buckets through `AmazonS3ReadOnlyAccess` needs an explicit policy.
:::

:::note Upgrading from 1.2 or earlier
- The stage used to be named `test`, so URLs contained `/test`. To keep these URLs, set `api_stage_name = "test"`.
- The log groups already exist, because Lambda created them on the first invocation. `terraform apply` then fails with *already exists*. Import each group once, then apply again:

  ```bash
  terraform import 'aws_cloudwatch_log_group.<function id>_logs' /aws/lambda/<function name>
  ```

  Alternatively, delete the groups; this also deletes the logs they contain.
:::

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-aws-terraform` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-aws` | compile | `NativeRuntime`, the entry point of a [native image](#native-image); base classes of the generated handlers: `ApiGatewayEventRouter` (the Lambda entry point `<Handler>$Auto::handleRequest`, which routes API Gateway, SQS, S3, schedule and direct calls), `SqsEventHandler` (reports failed messages as batch item failures), `S3EventHandler`, `SigV4Signer`/`AwsCredentials` for the AWS APIs the runtime calls itself, and the `ConfigSource` that reads secrets from Secrets Manager |
| `eu.cloudhopper.mc:provider-aws-publisher` | compile | `@Queue` publishers for SQS, without the AWS SDK |
| `eu.cloudhopper.mc:provider-aws-blobstore` | compile | `@Bucket` stores for S3 and S3-compatible storage, without the AWS SDK |
| `eu.cloudhopper.mc:provider-aws-keyvalue` | compile | `@Table` stores for DynamoDB, without the AWS SDK |
| `eu.cloudhopper.mc:cloudhopper-maven-plugin` | build plugin (profile `aws-native`) | the goals `native-image` and `native-smoke` |
