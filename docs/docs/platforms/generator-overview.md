# Targets and What They Support

import SupportMatrix from './_support-matrix.md';

Cloudhopper deploys the same annotated functions to six targets. Each target is
a **generator** (it writes handler classes, Terraform and API descriptions at
compile time) plus a **provider** (the runtime library the generated handlers
call). You pick the target with a Maven profile:

| Profile / generator ID | Target | Generator module | Provider module |
|---|---|---|---|
| `aws-terraform` | AWS Lambda | `generator-aws-terraform` | `provider-aws` |
| `azure-terraform` | Azure Functions | `generator-azure-terraform` | `provider-azure` |
| `gcp-terraform` | Google Cloud Functions (2nd gen) | `generator-gcp-terraform` | `provider-gcp` |
| `springboot-http` | Spring Boot service on any server | `generator-springboot` | `provider-springboot`, `provider-http` |
| `knative` | Knative service on Kubernetes (Helm chart) | `generator-knative` | `provider-springboot`, `provider-http` |
| `scaleway-terraform` | Scaleway Serverless Containers | `generator-scaleway-terraform` | `provider-springboot`, `provider-http` |

---

## Support matrix

Every generator declares which annotations and attributes it supports. The
same declarations drive the build and the
compatibility tests (TCK): when you
compile for a target, the build warns about every annotation or attribute it
does not claim (see
[When a target does not support something](../guides/writing-functions#when-a-target-does-not-support-something)),
and the TCK certifies each target against the tests whose features it claims.
The table below is generated from these declarations on every build of this
site.

<SupportMatrix />

Notes on the event and trigger columns:

- `@EventTrigger` lists the event sources a target supports as rows below
  `source`; today every target supports `QUEUE` and `STORAGE`. On Spring Boot it is the endpoint
  `POST /events/<function name>`, and queues can also be consumed from Kafka
  (see below). On Knative a queue is a Kafka topic; storage events have no
  generated source and are posted to `POST /events/<function name>` like on
  Spring Boot. On Scaleway a queue is a Scaleway Queue (SQS); storage events
  have no trigger there either.
- A function without a trigger can be invoked directly on AWS (Lambda
  invoke). On Azure every function also gets the HTTP route
  `/api/<function name>_http_function_trigger`, and on GCP every function has an
  HTTPS URL. On Spring Boot, Knative and Scaleway every function gets the endpoint
  `GET`/`POST /functions/<function name>`; set
  `cloudhopper.functions.direct-endpoint=false` to switch these off.
- Vendor-specific settings, such as the processor architecture on AWS, go
  through [`@Extension`](../guides/writing-functions#vendor-extensions) and are
  only read by the target they are meant for.

---

## What each target generates

### AWS (`aws-terraform`)

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | Lambda handler classes; `Auto` routes API Gateway, SQS, S3, schedule and direct calls |
| `shared.ftl` | Terraform | Artifact bucket, execution role |
| `function.ftl` | Terraform | Lambda function |
| `integration.ftl` | Terraform | API Gateway integration and permission per HTTP function |
| `api.ftl` | Terraform | HTTP API with its OpenAPI description |
| `schedule.ftl` | Terraform | EventBridge rule |
| `queue.ftl` | Terraform | SQS queue and event source mapping |
| `storage.ftl` | Terraform | S3 bucket and event notification |

### Azure (`azure-terraform`)

| Template | Output | Purpose |
|---|---|---|
| `function.ftl` | Java | HTTP-triggered function class per function |
| `apiIntegrationClass.ftl` | Java | Function class for an `@HttpTrigger` route |
| `schedule.ftl` | Java | Function class with `@TimerTrigger` |
| `queueFunction.ftl` | Java | Function class with `@QueueTrigger`, for queue messages and blob events |
| `shared.ftl` | Terraform | Resource group, storage account, consumption plan, function app, Application Insights |
| `output.ftl` | Terraform | Outputs with the function URLs |
| `queue.ftl` | Terraform | Storage queue |
| `storage.ftl` | Terraform | Blob container, event queue and Event Grid subscription |

On Azure, triggers are Java annotations on generated classes; Terraform
creates the infrastructure they bind to.

### GCP (`gcp-terraform`)

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | `HttpFunction` per function; also receives Pub/Sub and Cloud Storage events |
| `apiIntegrationClass.ftl` | Java | Subclass that knows the route pattern of an `@HttpTrigger`, for path parameters |
| `shared.ftl` | Terraform | Source bucket and archive |
| `function.ftl` | Terraform | Cloud Function and invoker permission |
| `apiIntegration.ftl` | JSON | OpenAPI operation per HTTP function |
| `apiRegistration.ftl` | Terraform | Separate Cloud Function `<name>_api` behind the API Gateway |
| `api.ftl` | Terraform | API Gateway API, config and gateway built from the operations |
| `schedule.ftl` | Terraform | Cloud Scheduler job |
| `queue.ftl` | Terraform | Pub/Sub topic and Eventarc trigger |
| `storage.ftl` | Terraform | Cloud Storage bucket and Eventarc trigger |

### Spring Boot (`springboot-http`)

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | `@RestController` per HTTP function |
| `function.ftl` | Java | `@RestController` with `GET`/`POST /functions/<function name>` per function (direct invocation) |
| `event.ftl` | Java | `@RestController` with `POST /events/<function name>` per event-triggered function |
| `eventRoute.ftl` | properties | Where an event-triggered function accepts its events, for tools such as the TCK binding |
| `schedule.ftl` | Java | `@RestController` with `POST /schedules/<function name>` per scheduled function, for an external scheduler |
| `scheduleJob.ftl` | Java | In-process `@Scheduled` job per scheduled function (`cloudhopper.schedules.in-process`) |
| `dockerfile.ftl` | Dockerfile | Container image for the service |

The project contains the `@SpringBootApplication` class (the archetype creates it
in `src/main/springboot-http`), and the profile builds an executable jar,
`<artifactId>-<version>-springboot-http.jar`, which the Dockerfile packages.
Like on the other targets, a `String` result is returned as JSON (`"pong"`).

#### Queues from Kafka

On Spring Boot, `@EventTrigger(source = EventSource.QUEUE, name = "<queue>")`
always has the endpoint `POST /events/<function name>`. To consume the queue
from Kafka instead, add `spring-kafka` to the project and set one property; the
function code stays the same:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

```properties
cloudhopper.events.broker=kafka
spring.kafka.bootstrap-servers=kafka:9092
```

Every queue-triggered function then consumes the topic `<queue>`:

- The consumer group is `cloudhopper-<function id>`, so the replicas of the
  service share the messages and each function sees each message once. Change
  the prefix with `cloudhopper.events.kafka.group-id-prefix`.
- A message is committed after the function returned. When the function throws,
  it is delivered again after `cloudhopper.events.kafka.backoff` (default `1s`),
  up to `cloudhopper.events.kafka.max-attempts` times (default `5`), and then
  published to the topic `<queue>.DLT`.
- A new consumer group starts with the oldest message in the topic, as a new
  queue subscription does on the cloud targets.
- `EventMetadata` gets the CloudEvents attributes of the Kafka binding
  (`ce_id`, `ce_source`, `ce_time` headers); without them the id is
  `<topic>-<partition>-<offset>`, which stays the same when a message is
  delivered again, and the time is the record timestamp. Other headers become
  the attributes.

Manage the versions with the Spring Boot BOM (`spring-boot-dependencies` or
`spring-boot-starter-parent`). Without it, Maven may pick the SLF4J 1.7 that
`kafka-clients` declares, and Spring Boot does not start.

The endpoint follows the CloudEvents HTTP binding (`ce-id`, `ce-source`,
`ce-time` headers), so a Knative `Trigger` or another CloudEvents sender can
deliver to `POST /events/<function name>` without Kafka on the classpath.

### Knative (`knative`)

The `knative` generator builds on `springboot-http`: it generates the same
Spring Boot application (all templates above) and adds a Helm chart under
`target/deployment/knative/chart` that runs it as one Knative service. The
cluster setup and the installation are described in the
[Knative setup guide](../setup/knative-prerequisites).

| Template | Output | Purpose |
|---|---|---|
| `chart.ftl` | `chart/Chart.yaml` | Chart metadata; the chart and app version follow the project version |
| `values.ftl` | `chart/values.yaml` | Image, Kafka bootstrap servers, delivery, scaling, timeout, resources, environment |
| `service.ftl` | `chart/templates/service.yaml` | Knative `Service`; starts the application with `cloudhopper.schedules.in-process=false` |
| `pingSource.ftl` | `chart/templates/<function>_schedule_pingSource.yaml` | `PingSource` with `cron` and `timezone` that calls `POST /schedules/<function name>` |
| `kafkaSource.ftl` | `chart/templates/<function>_event_kafkaSource.yaml` | For a queue: `KafkaSource` on the topic `<queue>` that delivers to `POST /events/<function name>`, and a `KafkaSink` for the dead-letter topic `<queue>.DLT` |

The profile builds the executable jar `<artifactId>-<version>-knative.jar`,
which the generated Dockerfile packages. The release name of the chart is the
name of the service; the sources and sinks are named `<release>-<function>`.

Schedules and queues reach the service through Knative Eventing, so they work
while the service is scaled to zero:

- **Schedules:** the `PingSource` fires once per schedule, independent of the
  number of replicas, and evaluates `timezone`.
- **Queues:** each queue function has its own consumer group,
  `<kafka.consumerGroupPrefix><function>` (default `cloudhopper-<function>`).
  A message is delivered at least once; when the function fails, it is
  delivered again `delivery.retry` times (default 4) with exponential backoff
  starting at `delivery.backoffDelay` (default `PT1S`), and then written to the
  topic `<queue>.DLT` with the error code in the CloudEvents extensions
  `knativeerrorcode` and `knativeerrordest`. Set `delivery.deadLetterTopic=false`
  to drop failed messages instead. The topic itself must exist, or the Kafka
  cluster must create topics automatically.
- `EventMetadata` gets the CloudEvents attributes of the `KafkaSource`
  (id `partition:<p>/offset:<o>`, the record time). The source does not report
  the delivery attempt, so it is always 0.

`@Function` settings for memory, timeout and instances are not claimed: all
functions run in one container, so these are set for the whole service in
`values.yaml` (`resources`, `timeoutSeconds`, `scaling`).

### Scaleway (`scaleway-terraform`)

The `scaleway-terraform` generator builds on `springboot-http`: it generates the
same Spring Boot application and adds Terraform under
`target/deployment/scaleway-terraform` that runs it as one Scaleway Serverless
Container. The account setup and the deployment are described in the
[Scaleway setup guide](../setup/scaleway-prerequisites).

| Template | Output | Purpose |
|---|---|---|
| `container.ftl` | `container.tf` | Container namespace and container; variables for the image, scaling, memory, CPU, timeout, privacy, environment and the queues; starts the application with `cloudhopper.schedules.in-process=false` |
| `cronTrigger.ftl` | `<function>_schedule_cronTrigger.tf` | Cron trigger with `schedule` and `timezone` that calls `POST /schedules/<function name>` |
| `queueTrigger.ftl` | `<function>_event_queueTrigger.tf` | For a queue: the queue `<cloudhopper_name_prefix><queue>`, the dead-letter queue `<cloudhopper_name_prefix><queue>-dlq` and a trigger that delivers to `POST /events/<function name>` |
| `sqs.ftl` | `sqs.tf` | Activates Scaleway Queues in the project and creates the credentials of the queues and triggers; written once for all event functions |

The container pulls the image given in the variable `image`. Build it from the
generated Dockerfile for `linux/amd64` and push it to a registry first; the
profile builds the executable jar `<artifactId>-<version>-scaleway-terraform.jar`
that the Dockerfile packages.

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

`@Function` settings for memory, timeout and instances are not claimed: all
functions run in one container, so these are set for the whole container with
the variables `memory_limit_bytes`, `cpu_limit`, `timeout`, `min_scale` and
`max_scale`.

---

## Terraform and provider versions

The generated Terraform requires Terraform `>= 1.5.0, < 2.0.0` (`required_version`)
and is tested with Terraform 1.17.

Terraform allows only one `required_providers` block per directory. The provider
versions therefore belong in your own `main.tf`, next to the provider configuration.
The exception is Scaleway, where the generated files declare the provider. Projects
created with the [archetype](../modules/maven-archetype) already contain these
constraints:

| Target | Provider constraints in `main.tf` |
|---|---|
| `aws-terraform` | `hashicorp/aws ~> 6.0` |
| `azure-terraform` | `hashicorp/azurerm ~> 4.0` |
| `gcp-terraform` | `hashicorp/google ~> 8.0`, `hashicorp/google-beta ~> 8.0`, `hashicorp/local ~> 2.0` |
| `scaleway-terraform` | none: the generated files declare `scaleway/scaleway ~> 2.92`. Do not add a `required_providers` block to your `main.tf` |

Without a constraint, `terraform init` installs the latest major version of the
provider, which the generated files may not support. Older projects should add the
block, for example on AWS:

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
```

---

## Maven configuration

Projects created with the [archetype](../modules/maven-archetype) have a
profile per target, so building for a target is
`mvn clean install -P<generator ID>`. Each profile adds the generator and
provider dependencies and passes the generator ID to the annotation processor:

```xml
<compilerArgs>
    <arg>-AgeneratorId=${generator.id}</arg>
    <arg>-AconfigOutputDir=${project.build.directory}/deployment/${generator.id}</arg>
    <arg>-AartifactId=${project.artifactId}</arg>
    <arg>-Aversion=${project.version}</arg>
    <arg>-Aclassifier=${jar.classifier}</arg>
    <arg>-AtargetDir=${project.build.directory}</arg>
</compilerArgs>
```

---

## See also

- [AWS generator](../modules/generator-aws-terraform) and [provider](../modules/provider-aws)
- [Azure generator](../modules/generator-azure-terraform) and [provider](../modules/provider-azure)
- [GCP generator](../modules/generator-gcp-terraform) and [provider](../modules/provider-gcp)
- [Knative setup guide](../setup/knative-prerequisites)
- [Scaleway setup guide](../setup/scaleway-prerequisites)
