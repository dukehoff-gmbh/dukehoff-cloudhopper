---
title: Writing Functions
---

# ✍️ Writing Functions

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
| `@HeaderParam("X-Token")` | An HTTP request header | The header name is **required** |

Because a blank value falls back to the parameter name, `@PathParam Integer id`
binds the `{id}` path variable without repeating the name.

A parameter of type `HandlerContext` is recognized automatically — no annotation
needed — and gives you invocation metadata (request id, remaining time, memory
limit, and so on). Any unannotated, non-context parameter is treated as the
request body.

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
