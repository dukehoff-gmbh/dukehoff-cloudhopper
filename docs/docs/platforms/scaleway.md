---
title: Scaleway Serverless Containers
sidebar_label: Scaleway
---

# Scaleway Serverless Containers

The `scaleway-terraform` generator builds on `springboot-http`: it generates the
same Spring Boot application and adds Terraform under
`target/deployment/scaleway-terraform` that runs it as one Scaleway Serverless
Container. The account setup and the deployment are described in the
[Scaleway setup guide](../setup/scaleway-prerequisites.md).

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `container.ftl` | `container.tf` | Container namespace and container; variables for the image, scaling, memory, CPU, timeout, privacy, environment and the queues; starts the application with `cloudhopper.schedules.in-process=false` |
| `cronTrigger.ftl` | `<function>_schedule_cronTrigger.tf` | Cron trigger with `schedule` and `timezone` that calls `POST /schedules/<function name>` |
| `queueTrigger.ftl` | `<function>_event_queueTrigger.tf` | For a queue: the queue `<cloudhopper_name_prefix><queue>`, the dead-letter queue `<cloudhopper_name_prefix><queue>-dlq` and a trigger that delivers to `POST /events/<function name>` |
| `sqs.ftl` | `sqs.tf` | Activates Scaleway Queues in the project and creates the credentials of the queues and triggers; written once for all event functions |
| `publisher.ftl` | `publisher.tf` | For `@Queue` publishers: credentials that may only publish, and the queue URLs and credentials as environment of the container |

The container pulls the image given in the variable `image`. Build it from the
generated Dockerfile for `linux/amd64` and push it to a registry first; the
profile builds the executable jar `<artifactId>-<version>-scaleway-terraform.jar`
that the Dockerfile packages.

## Schedules and queues

Schedules and queues reach the container through Scaleway triggers, so they work
while the container is scaled to zero:

- **Schedules:** the cron trigger fires once per schedule, independent of the
  number of instances, and evaluates `timezone`.
- **Queues:** the trigger calls the function with the message as the body. When
  the function fails, the trigger calls it again, up to four times 30 seconds
  apart, and then returns the message to the queue. After
  `queue_max_receive_count` receives (default 1) the message moves to the
  dead-letter queue. The trigger sends no message ID and no attempt count, so
  `EventMetadata` stays empty and the attempt is always 0.

## Sending messages

A [`@Queue`](../guides/writing-functions.md#sending-messages) publisher sends to
the Scaleway Queue of the consuming function through the SQS API of Scaleway
Queues, with the SQS client of `eu.cloudhopper.mc:provider-aws-publisher`; the
archetype adds it without the AWS Lambda libraries. The generated `publisher.tf`
creates credentials that may only publish and passes them, the region and the
queue URLs to the container. The SQS limits apply: at most 256 KiB per message
and 10 messages per batch. Queues outside the project (`existing = true`) are not
supported yet.

## Secrets

The Terraform variable `cloudhopper_secrets` maps a property name to the value of a
[secret](../guides/writing-functions.md#secrets):

```hcl
cloudhopper_secrets = {
  "report.api-key" = "..."
}
```

The container gets each value as a secret environment variable of the property
(`report.api-key` → `REPORT_API_KEY`). Scaleway Serverless Containers cannot
reference Scaleway Secret Manager, so the values are passed to Terraform and are
**stored in the Terraform state**: keep the state in a protected backend, and pass
the values through `TF_VAR_cloudhopper_secrets` or a `.tfvars` file that is not
committed. `terraform plan` fails if a property is set in both
`cloudhopper_config` and `cloudhopper_secrets`.

## Resources and scaling

`@Function` settings for memory, timeout and instances are not claimed: all
functions run in one container, so these are set for the whole container with
the variables `memory_limit_bytes`, `cpu_limit`, `timeout`, `min_scale` and
`max_scale`.

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-scaleway-terraform` | `provided`: only the annotation processor uses it | Templates and template registration; builds on `generator-springboot` |
| `eu.cloudhopper.mc:provider-springboot` | compile | The Spring Boot runtime, see [Spring Boot](springboot.md#runtime-modules) |
| `eu.cloudhopper.mc:provider-http` | compile | `HandlerContext` for HTTP services |
| `org.springframework.boot:spring-boot-starter-webmvc` | compile | Spring MVC |
| `eu.cloudhopper.mc:provider-aws-publisher` | compile, without `aws-lambda-java-core` and `aws-lambda-java-events` | `@Queue` publishers for the SQS API of Scaleway Queues |
