# Targets and What They Support

import SupportMatrix from './_support-matrix.md';

Cloudhopper deploys the same annotated functions to four targets. Each target is
a **generator** (it writes handler classes, Terraform and API descriptions at
compile time) plus a **provider** (the runtime library the generated handlers
call). You pick the target with a Maven profile:

| Profile / generator ID | Target | Generator module | Provider module |
|---|---|---|---|
| `aws-terraform` | AWS Lambda | `generator-aws-terraform` | `provider-aws` |
| `azure-terraform` | Azure Functions | `generator-azure-terraform` | `provider-azure` |
| `gcp-terraform` | Google Cloud Functions (2nd gen) | `generator-gcp-terraform` | `provider-gcp` |
| `springboot-http` | Spring Boot service on any server | `generator-springboot` | `provider-springboot`, `provider-http` |

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

- `@EventTrigger` covers both `EventSource.QUEUE` and `EventSource.STORAGE` on every
  target that claims it. On Spring Boot it is the endpoint
  `POST /events/<function name>`, and queues can also be consumed from Kafka
  (see below).
- A function without a trigger can be invoked directly on AWS (Lambda
  invoke). On Azure every function also gets the HTTP route
  `/api/<function name>_http_function_trigger`, and on GCP every function has an
  HTTPS URL. On Spring Boot every function gets the endpoint
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
