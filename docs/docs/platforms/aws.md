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

All instances restored from one snapshot share the state created during initialization:
create random values, unique IDs and network connections in the function call, not in
constructors or static initializers, or renew them in a `org.crac` `afterRestore` hook.

Terraform does not delete old versions; they count toward Lambda's code storage quota per
region. SnapStart cannot be combined with provisioned concurrency.

## Sending messages

A function with a [`@Queue`](../guides/writing-functions.md#sending-messages)
parameter needs the dependency `eu.cloudhopper.mc:provider-aws-publisher`; the
archetype adds it. It sends through the SQS API, signed with the credentials of
the function's execution role, without the AWS SDK. The generated Terraform passes
the queue URL as the environment variable `CLOUDHOPPER_QUEUE_<NAME>_URL` and grants
the function's identity `sqs:SendMessage` on exactly that queue.

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
| `eu.cloudhopper.mc:provider-aws` | compile | Base classes of the generated handlers: `ApiGatewayEventRouter` (the Lambda entry point `<Handler>$Auto::handleRequest`, which routes API Gateway, SQS, S3, schedule and direct calls), `SqsEventHandler` (reports failed messages as batch item failures), `S3EventHandler`, `SigV4Signer`/`AwsCredentials` for the AWS APIs the runtime calls itself, and the `ConfigSource` that reads secrets from Secrets Manager |
| `eu.cloudhopper.mc:provider-aws-publisher` | compile | `@Queue` publishers for SQS, without the AWS SDK |
