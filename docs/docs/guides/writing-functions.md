---
title: Writing Functions
---

# Writing Functions

With Cloudhopper you write **plain Java methods** and describe them with a few
annotations. You never implement a cloud-specific interface: the annotation
processor reads your annotations at compile time and generates the
provider-specific handler and deployment artifacts for you.

A function is just a method annotated with `@Function` plus a trigger:

```java
public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}",
                 summary = "Say Hello", description = "Returns a friendly greeting")
    public String handleRequest(@PathParam String name) {
        return "hello " + name;
    }
}
```

---

## `@Function`

Marks a method as a deployable function and carries provider-neutral deployment
metadata. Only `name` is required.

Which target applies which attribute is listed in the
[support matrix](../platforms/generator-overview.md#support-matrix).

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | — | Unique function name in the deployment target |
| `memory` | `int` | `128` | Memory limit in MB |
| `timeout` | `int` | `30` | Maximum run time in seconds |
| `minInstances` | `int` | `0` | Instances kept warm to reduce cold starts. GCP sets `min_instance_count`; warm instances are billed while idle. Not applied on the other targets yet (AWS: [#48](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/issues/48)) |
| `identity` | `String` | own identity | The identity the function runs as and its permissions are granted to (see [Identities and permissions](#identities-and-permissions)) |
| `extensions` | `@Extension[]` | `{}` | Vendor-specific attributes (see [Vendor extensions](#vendor-extensions)) |

```java
@Function(name = "resize", memory = 512, timeout = 60)
```

---

## Triggers

Trigger annotations decide how a function is invoked. A function may carry
several, and Cloudhopper generates each of them; the compatibility tests cover
each trigger on its own, so prefer one trigger per function. Without a
trigger it can only be invoked directly, as far as the target allows; the
[support matrix](../platforms/generator-overview.md#support-matrix) lists what
each target offers.

### `@HttpTrigger`

Exposes the function through an HTTP API gateway.

| Attribute | Description |
|-----------|-------------|
| `method` | HTTP method, e.g. `"GET"`, `"POST"` |
| `path` | Route, may contain path variables, e.g. `/player/{id}` |
| `operationId` | Operation id used in the generated OpenAPI document |
| `summary` / `description` | Human-readable API metadata; written into the OpenAPI description of the API Gateway on GCP, ignored by the other targets |

### `@ScheduledTrigger`

Invokes the function on a cron schedule. Scheduled functions usually take no
parameters and return `void`.

| Attribute | Default | Description |
|-----------|---------|-------------|
| `cron` | — (required) | Cron expression, e.g. `"0 0 * * *"` |
| `timezone` | `"UTC"` | Timezone the schedule is evaluated in |

```java
@Function(name = "nightlyCleanup")
@ScheduledTrigger(cron = "0 0 * * *", timezone = "UTC")
public void cleanup() {
    // ...
}
```

| Target | Schedule | Notes |
|--------|----------|-------|
| `aws-terraform` | EventBridge rule | Evaluated in UTC |
| `azure-terraform` | Timer trigger | Evaluated in UTC |
| `gcp-terraform` | Cloud Scheduler job | Evaluates `timezone` |
| `springboot-http` | Spring `@Scheduled` in the service, and `POST /schedules/<function name>` | Evaluates `timezone`; see below |
| `knative` | `PingSource` that calls `POST /schedules/<function name>` | Evaluates `timezone`; fires once, also when the service is scaled to zero |
| `scaleway-terraform` | Cron trigger that calls `POST /schedules/<function name>` | Evaluates `timezone`; fires once, also when the container is scaled to zero |

On Spring Boot every running instance fires the schedule on its own. That is
right for a single instance. When the service runs with several replicas, or on
a platform that scales to zero, set `cloudhopper.schedules.in-process=false` and
let an external scheduler call `POST /schedules/<function name>` instead, for
example a Kubernetes `CronJob` or the platform's cron trigger. Set
`cloudhopper.schedules.endpoint=false` to switch the endpoints off.

### `@EventTrigger`

Invokes the function once for each event from a queue or a storage bucket.

| Attribute | Default | Description |
|-----------|---------|-------------|
| `source` | — (required) | `EventSource.QUEUE` or `EventSource.STORAGE` |
| `name` | — (required) | Name of the queue or bucket: 3 to 63 lowercase letters, digits and single hyphens, starting with a letter |
| `create` | `true` | `true` creates the queue or bucket with the function; `false` connects to an existing one of that name (AWS and GCP; the other targets ignore it) |

Delivery is **at least once**: when the function throws, the platform delivers
the event again, so make the function tolerate duplicates. A parameter of type
`EventMetadata` gives you the event id, source, time, delivery attempt and
attributes; on invocations that are not events it is `null`.

#### Queues

For `EventSource.QUEUE`, the message body is deserialized from JSON into the
body parameter, just like an HTTP request body.

```java
@Function(name = "orderReceived")
@EventTrigger(source = EventSource.QUEUE, name = "orders")
public void onOrder(Order order, EventMetadata meta) {
    // ...
}
```

| Target | Queue | Notes |
|--------|-------|-------|
| `aws-terraform` | SQS queue | One message per invocation; failed messages are reported individually and delivered again |
| `azure-terraform` | Storage queue in the function app's storage account | Messages must be Base64-encoded; after five failed attempts a message moves to `<name>-poison` |
| `gcp-terraform` | Pub/Sub topic, delivered through Eventarc | Requires the Eventarc and Pub/Sub APIs in the project |
| `springboot-http` | `POST /events/<function name>`, or a Kafka topic | The body is the message; optional `ce-id`, `ce-source` and `ce-time` headers (CloudEvents) fill the metadata. Answers `204`, or an error status if the function throws. With `spring-boot-starter-kafka` and `cloudhopper.events.broker=kafka`, the function consumes the topic `<name>`; after five failed attempts a message moves to `<name>.DLT`, see [Queues from Kafka](../platforms/springboot.md#queues-from-kafka) |
| `knative` | Kafka topic `<name>`, delivered by a `KafkaSource` | Delivered again up to four times with exponential backoff, then written to `<name>.DLT`; see [Knative](../platforms/knative.md) |
| `scaleway-terraform` | Scaleway Queue (SQS) `<name>` with a trigger | Called up to four times per receive, then moved to `<name>-dlq` after `queue_max_receive_count` receives (default 1); see [Scaleway](../platforms/scaleway.md) |

#### Storage buckets

For `EventSource.STORAGE`, the function is invoked for every object created or
overwritten in the bucket. Its body parameter, if it has one, must be a
`StorageEvent`: bucket, key and size of the object. The function reads the
content itself if it needs it, with the storage client of the platform.

```java
@Function(name = "invoiceUploaded")
@EventTrigger(source = EventSource.STORAGE, name = "acme-invoices")
public void onUpload(StorageEvent event) {
    // event.getBucket(), event.getKey(), event.getSize()
}
```

| Target | Bucket | Notes |
|--------|--------|-------|
| `aws-terraform` | S3 bucket with an event notification | The bucket is named `<cloudhopper_name_prefix><name>`. S3 keeps one notification configuration per bucket: with `create = false`, it replaces the notifications the bucket already has |
| `azure-terraform` | Blob container in the function app's storage account | Event Grid puts the events on the storage queue `<name>-events`, which the function reads |
| `gcp-terraform` | Cloud Storage bucket in `gcp_region`, delivered through Eventarc | Requires two one-time IAM grants, see [Storage triggers](../setup/gcp-prerequisites.md#storage-triggers) |
| `springboot-http` | `POST /events/<function name>` | The body is `{"bucket": ..., "key": ..., "size": ...}`; `bucket` defaults to the name in the annotation |
| `knative` | `POST /events/<function name>` | No storage source is generated; a CloudEvents sender, for example an S3-compatible store's webhook notification, posts the body `{"bucket": ..., "key": ..., "size": ...}` |
| `scaleway-terraform` | `POST /events/<function name>` | Scaleway has no trigger for Object Storage events; post the body `{"bucket": ..., "key": ..., "size": ...}` yourself |

Bucket names on AWS and GCP are global across all accounts, so choose one that
nobody else uses, for example one starting with your company name. Terraform
does not delete a bucket that still contains objects.

### When a target does not support something

Not every target supports every trigger or attribute. AWS and Azure ignore the
`timezone` of a schedule, for example. When
you compile for a target, Cloudhopper reports what that target ignores:

```
[WARNING] NightlyCleanup.java:[24,48] Attribute 'timezone' of @ScheduledTrigger is not supported by generator 'aws-terraform'; it is ignored for this target.
[WARNING] ReportMemoryFunction.java:[32,40] Attribute 'memory' of @Function is not supported by generator 'azure-terraform'; it is ignored for this target.
```

A target can also support an attribute only for some values, such as the
`source` of an `@EventTrigger`. A value it does not support is reported the
same way, for example
`@EventTrigger(source = TOPIC) is not supported by generator '…'; it supports QUEUE, STORAGE.`

To fail the build instead, pass the processor option
`-Acloudhopper.strictFeatureCheck=true` (a `compilerArgs` entry of the
`maven-compiler-plugin`). Vendor extensions are never reported, since they are
meant for one target. Maven shows these warnings only with `showWarnings`
enabled, which is the default from `maven-compiler-plugin` 3.13.0 on and set in
projects generated by the archetype.

---

## Binding request data

Declare exactly the parameters your method needs and annotate them. The
processor deserializes and converts each value to the parameter's declared type
before calling your method.

| Annotation | Binds to | Notes |
|------------|----------|-------|
| `@RequestBody` | The deserialized request body | At most one per method |
| `@PathParam("id")` | A path template variable | Blank value → the parameter name is used |
| `@QueryParam("q")` | A query string parameter | Blank value → the parameter name is used |
| `@HeaderParam("X-Token")` | An HTTP request header | The header name is **required** and matched case-insensitively |
| `@ConfigProperty(name = "report.bucket")` | A configuration value of the deployment | See [Configuration](#configuration) |

Because a blank value falls back to the parameter name, `@PathParam Integer id`
binds the `{id}` path variable without repeating the name.

Path, query and header values can be bound as `String`, `Integer`, `Long`,
`Double` or `Boolean` (or the primitive types); they are converted for you.

A parameter of type `HandlerContext` is recognized automatically — no annotation
needed — and gives you invocation metadata (request id, remaining time, memory
limit, and so on). The same holds for `EventMetadata` in event-triggered
functions (see [`@EventTrigger`](#eventtrigger)). Any other unannotated
parameter is treated as the request body.

```java
@Function(name = "updatePlayer")
@HttpTrigger(method = "PUT", path = "/player/{id}")
public Player update(@RequestBody Player input,
                     @PathParam Integer id,
                     @QueryParam("dryRun") boolean dryRun,
                     HandlerContext context) {
    // ...
}
```

The method's return value becomes the response body; a `void` method produces no
response payload. Body and return value can be parameterized types such as
`Map<String, List<String>>`.

A method may declare checked exceptions (`throws IOException`). Any exception the
method throws fails the invocation: an HTTP call answers with a server error, an
event is redelivered. Checked exceptions reach the platform wrapped in
`FunctionException`, runtime exceptions unchanged.

---

## Sending messages

A function sends messages to a queue through a `Publisher` parameter annotated
with `@Queue`. A typical use is fanning out work: a scheduled function sends one
message per item, and a queue-triggered function processes each of them.

```java
@Function(name = "startCollection")
@ScheduledTrigger(cron = "0 4 * * *")
public void start(@Queue(name = "collect") Publisher<CollectRequest> queue) {
    queue.sendAll(accounts().stream().map(CollectRequest::new).toList());
}

@Function(name = "collectOne")
@EventTrigger(source = EventSource.QUEUE, name = "collect")
public void collect(CollectRequest request) {
    // ...
}
```

- `@Queue(name)` refers to a queue that an `@EventTrigger(source = QUEUE)` of the
  same project creates. The generator uses its deployed name, gives the sending
  function's [identity](#identities-and-permissions) permission to send to exactly
  this queue, and passes the queue's address to the runtime.
- A queue that another system owns is declared with
  `@Queue(name = "...", existing = true)`; it is used under exactly this name.
- A `@Queue` that is neither is a build error, so messages cannot end up in a
  queue nobody reads.
- A `String` is sent as it is, any other message as JSON, the way queue triggers
  read their input; the consumer receives the message as its input type.
- `sendAll` sends in as few requests as the queue's batch limits allow. If the
  queue does not accept a message after retries, `send` and `sendAll` throw
  `PublishException`.

The target needs a runtime module for this, which the archetype adds; if it is
missing, the build names the dependency. Which targets support `@Queue` is listed
in the [support matrix](../platforms/generator-overview.md#support-matrix).

Cloudhopper sends with small HTTP clients of its own instead of the cloud SDKs
(SQS on AWS and Scaleway, Pub/Sub on GCP, Storage queues on Azure; Kafka on
Knative uses the Kafka client of Spring): the SQS client of the AWS
SDK alone would more than double the size of a function's runtime and its cold
start, and SDKs often conflict with the
dependencies of the project. The cloud APIs these clients call are stable and
versioned. An implementation based on an SDK can replace them through the
`eu.cloudhopper.mc.runtime.spi.PublisherFactory` service interface.

---

## Configuration

Values that differ per deployment, such as resource names, endpoints or limits,
are read with [MicroProfile Config](https://microprofile.io/specifications/config/):
`@ConfigProperty` on a parameter, or `ConfigProvider.getConfig()` in any code.

```java
@Function(name = "report")
@ScheduledTrigger(cron = "0 6 * * *")
public void report(@ConfigProperty(name = "report.bucket") String bucket,
                   @ConfigProperty(name = "report.days", defaultValue = "7") int days,
                   @ConfigProperty(name = "report.cc") Optional<String> cc) {
    // ...
}
```

- A property needs a `name`; there is no field or method name to derive it from.
- Without a value and without `defaultValue`, the call fails with an exception
  that names the property and its environment variable. `Optional<T>` is empty
  instead.
- Types: `String`, the primitive types and their wrappers, enums, `List<T>`
  (comma-separated, `\,` for a literal comma), and every class with a static
  `of`, `valueOf` or `parse` method or a constructor taking a `String`, e.g.
  `Duration` (`PT30S`) or `URI`.
- Values are read on every call; the sources are read once per instance.

The deployment passes the values as **environment variables**: `report.bucket`
becomes `REPORT_BUCKET` (every character other than a letter or digit becomes
`_`, upper case). How to set them is described per target in
[Targets and what they support](../platforms/generator-overview.md#configuration).
System properties take precedence over environment variables, and
`META-INF/microprofile-config.properties` in the function jar provides defaults
for every target.

Cloudhopper ships a small implementation of the MicroProfile Config API with
these three sources and the converters above; it keeps the runtime small and
starts fast. It has no profiles, property expressions or config mapping. To use
a complete implementation such as SmallRye Config instead, add it to the
project; your own `ConfigSource` and `Converter` implementations registered
through `META-INF/services` work with either.

### Secrets

Secrets, such as API keys or service account keys, are read like any other
property; the code does not change:

```java
@Function(name = "prices")
public PriceList prices(@ConfigProperty(name = "prices.gcp-credentials") String credentials) {
    // ...
}
```

The deployment decides that a property is a secret: instead of a value, it
references a secret in the target's secret store. The value is not copied into
the deployment (Terraform state, function environment); the function reads it at
runtime and keeps it for the life of the instance, so a rotated secret reaches new
instances. The function's [identity](#identities-and-permissions) may read exactly
the secrets of the properties its functions read. A value set as an environment
variable or system property takes precedence, e.g. for local runs and tests.

Which targets support secrets, and how to reference them, is described per target;
see [Configuration](../platforms/generator-overview.md#configuration). With
[SnapStart](../platforms/aws.md#snapstart), read secrets in the function call, not
in a constructor or static initializer, so they are not part of the snapshot.

---

## Identities and permissions

Every function runs as an identity: the principal its permissions are granted to, an
IAM role on AWS, a service account on GCP. By default, every function has an identity of its own, so a function
gets only the permissions meant for it. Functions that need the same permissions can
share one identity:

```java
@Function(name = "connectAccount", identity = "connector")
@HttpTrigger(method = "POST", path = "/accounts")
public String connect(@RequestBody Account account) { ... }

@Function(name = "collectUsage", identity = "connector")
@ScheduledTrigger(cron = "0 3 * * *")
public void collect() { ... }
```

An identity name has up to 32 lowercase letters, digits and hyphens and starts with a
letter. A function without `identity` uses its function name as the identity name. An
explicit `identity` that is also the name of a function without `identity` is a build
error, because both would silently run as one identity; to share it on purpose, declare
the same `identity` on both functions.

The generator grants an identity logging and the permissions the triggers of its
functions need, such as receiving from the function's queue. Nothing else: a function
that reads another bucket, writes to a database or calls another account needs
permissions from your own deployment configuration, attached to the identity. How to
refer to the identity is described per target: [AWS](../platforms/aws.md#identities-and-permissions),
[GCP](../platforms/gcp.md#identities-and-permissions).

The name of an identity does not depend on the function names. Renaming a function
keeps its identity, so policies elsewhere that refer to it, for example a role in
another account that trusts it, keep working.

Targets that deploy all functions of a project as one unit run them as one identity
and ignore `identity`; the build warns about it
([#82](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/issues/82)).

---

## Vendor extensions

When you need a provider-specific setting that has no dedicated `@Function`
attribute, attach it as an `@Extension`. Prefer the constants in `ExtensionKeys`
over raw strings.

```java
@Function(
    name = "hello",
    extensions = {
        @Extension(
            name = ExtensionKeys.Aws.Architecture.KEY,
            value = ExtensionKeys.Aws.Architecture.Values.ARM64)
    })
@HttpTrigger(method = "GET", path = "/hello/{name}")
public String hello(@PathParam String name) {
    return "hello " + name;
}
```

Only generators that declare support for a given extension key act on it; others
ignore it.

| Key | Values | Target | Effect |
|---|---|---|---|
| `x-aws-architecture` (`ExtensionKeys.Aws.Architecture`) | `arm64`, `x86_64` | `aws-terraform` | CPU architecture of the Lambda function |
| `x-aws-snapstart` (`ExtensionKeys.Aws.SnapStart`) | `PublishedVersions` | `aws-terraform` | [Lambda SnapStart](../platforms/aws.md#snapstart) for shorter cold starts |

---

## What gets generated

At build time the processor generates, per function:

- a core `<Class>_<method>_Handler` that adapts your method to the runtime, plus
  a provider wrapper (e.g. an AWS Lambda entry point),
- the infrastructure-as-code for the active generator (Terraform, OpenAPI, …),
- metadata under `META-INF/`.

You never edit these — rerun the build and they are regenerated. See the
[Demo Project Guide](../modules/demo.md) for a full walk-through of the output, and
the [Deployment Config API](../modules/deployment-config-api.md) for the annotation
reference.
