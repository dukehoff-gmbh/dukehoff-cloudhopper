---
title: Deployment Config API
---

# deployment-config-api

This module defines the public annotation and runtime API used by developers to create cloud functions with Cloudhopper.

It provides the key building blocks for function declaration, scheduling, and API gateway integration, independent of any specific cloud provider.

---

## 💡 Purpose

The goal of this module is to allow application developers to define serverless functions in a clean, portable way using standard Java annotations and interfaces.

This module is **used by developers**, **referenced by integrators**, and **consumed by the annotation processor**.

---

## 📦 Package Overview

| Package                             | Description                                                  |
|-------------------------------------|--------------------------------------------------------------|
| `eu.cloudhopper.mc.annotations`    | Function/trigger annotations (`@Function`, `@ScheduledTrigger`, `@HttpTrigger`), parameter-binding annotations (`@RequestBody`, `@PathParam`, `@QueryParam`, `@HeaderParam`) and vendor extensions (`@Extension`, `ExtensionKeys`) |
| `eu.cloudhopper.mc.runtime`        | Runtime types such as `HandlerContext`, `CloudRequestHandler`, `ParamConverters` |

---

## 🧩 Key Annotations

### `@Function`
Marks a **method** as a deployable cloud function. The processor generates the
provider handler for it &mdash; you do not implement any interface.

~~~java
@Function(name = "hello")
@HttpTrigger(method = "GET", path = "/hello/{name}")
public String hello(@PathParam("name") String name) {
    return "Hello, " + name;
}
~~~

Optional attributes: `memory` (MB, default 128), `timeout` (seconds, default 30),
`minInstances` (default 0), and `extensions` for vendor-specific keys.

### `@ScheduledTrigger`
Invokes a function on a cron schedule. A scheduled function typically takes no
parameters and returns `void`.

~~~java
@Function(name = "nightly")
@ScheduledTrigger(cron = "0 0 * * *", timezone = "UTC")
public void run() { ... }
~~~

### `@HttpTrigger`
Declares HTTP API metadata used to configure gateway routes.

~~~java
@HttpTrigger(method = "GET", path = "/hello", summary = "Say hello")
~~~

---

## 🔌 Binding request data

Instead of implementing a handler interface, you declare exactly the parameters
your method needs and annotate them. The processor deserializes and converts
each value before invoking your method:

| Annotation | Binds to |
|------------|----------|
| `@RequestBody` | the deserialized request payload (at most one per method) |
| `@PathParam("id")` | a URI path template variable; blank value falls back to the parameter name |
| `@QueryParam("q")` | a query string parameter; blank value falls back to the parameter name |
| `@HeaderParam("X-Token")` | an HTTP request header; the header name is required |

A parameter of type `HandlerContext` is recognized automatically (no annotation
needed) and receives the runtime context. Any unannotated, non-context
parameter is treated as the request body.

~~~java
@Function(name = "updatePlayer")
@HttpTrigger(method = "PUT", path = "/player/{id}")
public Player update(@RequestBody Player input,
                     @PathParam Integer id,
                     HandlerContext context) {
    ...
}
~~~

---

## ⚙️ Runtime Interfaces

These types live in `eu.cloudhopper.mc.runtime`.

### `CloudRequestHandler<I, O>`

The integration interface that the **generated** handler implements to adapt
your annotated method to the platform. You normally never implement or
reference it directly &mdash; it is generated during annotation processing.

### `HandlerContext`

Provides metadata about the current function invocation.

~~~java
public interface HandlerContext {
    String getRequestId();
    String getFunctionName();
    String getFunctionVersion();
    String getInvokedFunctionArn();
    String getLogGroupName();
    String getLogStreamName();
    long getRemainingTimeInMillis();
    int getMemoryLimitInMB();
}
~~~

The platform adapter (e.g. AWS, Azure, GCP) supplies this to your function. It gives access to:
- invocation-specific IDs
- logging information
- timeout and memory limits

---

## 🔗 Related Modules

- `deployment-config-generator-api`: used by integrators to build custom code generators
- `deployment-config-processor`: uses this module to process annotations at compile time

---

## 👤 Intended Audience

This module is for:
- Application developers writing cloud functions
- Generator implementors referencing annotation types

