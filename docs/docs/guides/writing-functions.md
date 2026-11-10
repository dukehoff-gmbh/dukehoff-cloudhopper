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

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | — | Unique function name in the deployment target |
| `memory` | `int` | `128` | Memory limit in MB |
| `timeout` | `int` | `30` | Maximum run time in seconds |
| `minInstances` | `int` | `0` | Instances kept warm to reduce cold starts |
| `extensions` | `@Extension[]` | `{}` | Vendor-specific attributes (see [Vendor extensions](#vendor-extensions)) |

```java
@Function(name = "resize", memory = 512, timeout = 60)
```

---

## Triggers

A function needs exactly one trigger annotation that decides how it is invoked.

### `@HttpTrigger`

Exposes the function through an HTTP API gateway.

| Attribute | Description |
|-----------|-------------|
| `method` | HTTP method, e.g. `"GET"`, `"POST"` |
| `path` | Route, may contain path variables, e.g. `/player/{id}` |
| `operationId` | Operation id used in the generated OpenAPI document |
| `summary` / `description` | Human-readable API metadata |

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

### `@EventTrigger`

Invokes the function once for each event from a queue or a storage bucket.

| Attribute | Default | Description |
|-----------|---------|-------------|
| `source` | — (required) | `EventSource.QUEUE` or `EventSource.STORAGE` |
| `name` | — (required) | Name of the queue or bucket: 3 to 63 lowercase letters, digits and single hyphens, starting with a letter |
| `create` | `true` | `true` creates the queue or bucket with the function; `false` connects to an existing one of that name |

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
| `springboot-http` | `POST /events/<function name>` | The body is the message; optional `ce-id`, `ce-source` and `ce-time` headers (CloudEvents) fill the metadata. Answers `204`, or an error status if the function throws |

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
| `gcp-terraform` | Cloud Storage bucket in `gcp_region`, delivered through Eventarc | Requires two one-time IAM grants, see [Storage triggers](../setup/gcp-prerequisites#storage-triggers) |
| `springboot-http` | `POST /events/<function name>` | The body is `{"bucket": ..., "key": ..., "size": ...}`; `bucket` defaults to the name in the annotation |

Bucket names on AWS and GCP are global across all accounts, so choose one that
nobody else uses, for example one starting with your company name. Terraform
does not delete a bucket that still contains objects.

### When a target does not support something

Not every target supports every trigger or attribute. The `springboot-http`
target has no schedules, for example, and only GCP evaluates `timezone`. When
you compile for a target, Cloudhopper reports what that target ignores:

```
[WARNING] ScheduledFunction.java:[40,5] @ScheduledTrigger is not supported by generator 'springboot-http'; it is ignored for this target.
[WARNING] ReportMemoryFunction.java:[32,40] Attribute 'memory' of @Function is not supported by generator 'azure-terraform'; it is ignored for this target.
```

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
response payload.

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

---

## What gets generated

At build time the processor generates, per function:

- a core `<Class>_<method>_Handler` that adapts your method to the runtime, plus
  a provider wrapper (e.g. an AWS Lambda entry point),
- the infrastructure-as-code for the active generator (Terraform, OpenAPI, …),
- metadata under `META-INF/`.

You never edit these — rerun the build and they are regenerated. See the
[Demo Project Guide](../modules/demo) for a full walk-through of the output, and
the [Deployment Config API](../modules/deployment-config-api) for the annotation
reference.
