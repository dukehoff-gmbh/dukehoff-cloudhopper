# Targets and What They Support

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

What each target supports, taken from the generators' feature declarations.
When you compile for a target, the build warns about every annotation or
attribute that target ignores (see
[When a target does not support something](../guides/writing-functions#when-a-target-does-not-support-something)).

### Triggers

| Trigger | AWS | Azure | GCP | Spring Boot |
|---|:-:|:-:|:-:|:-:|
| [`@HttpTrigger`](../guides/writing-functions#httptrigger) | ✓ | ✓ | ✓ | ✓ |
| [`@ScheduledTrigger`](../guides/writing-functions#scheduledtrigger) | ✓ | ✓ | ✓ | – |
| [`@EventTrigger`](../guides/writing-functions#eventtrigger) `QUEUE` | ✓ | ✓ | ✓ | ✓ ¹ |
| [`@EventTrigger`](../guides/writing-functions#eventtrigger) `STORAGE` | ✓ | ✓ | ✓ | ✓ ¹ |
| Without a trigger | Lambda invoke | HTTP route ² | Function URL | – |

¹ As the endpoint `POST /events/<function name>`; there is no broker binding yet.<br/>
² Every function also gets the route `/api/<function name>_http_function_trigger`.

### Attributes

| Attribute | AWS | Azure | GCP | Spring Boot |
|---|:-:|:-:|:-:|:-:|
| `@Function` `name` | ✓ | ✓ | ✓ | ✓ |
| `@Function` `memory` | ✓ | – | ✓ | – |
| `@Function` `timeout` | ✓ | – | ✓ | – |
| `@Function` `minInstances` | – ³ | – | – ³ | – |
| `@HttpTrigger` `method`, `path`, `summary`, `description`, `operationId` | ✓ | ✓ | ✓ | ✓ |
| `@ScheduledTrigger` `cron` | ✓ | ✓ | ✓ | – |
| `@ScheduledTrigger` `timezone` | – | – | ✓ | – |
| `@EventTrigger` `source`, `name`, `create` | ✓ | ✓ | ✓ | ✓ |

³ Declared by the generator, but not yet applied to the generated function, so the
build does not warn about it.

Vendor-specific settings, such as the processor architecture on AWS, go
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
| `event.ftl` | Java | `@RestController` with `POST /events/<function name>` per event-triggered function |
| `dockerfile.ftl` | Dockerfile | Container image for the service |

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
