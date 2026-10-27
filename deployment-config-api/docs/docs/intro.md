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
| `eu.cloudhopper.mc.annotations`    | Core annotations like `@Function`, `@ScheduledTrigger`, `@HttpTrigger` |
| `eu.cloudhopper.mc.runtime`        | Runtime interfaces such as `CloudRequestHandler`, `HandlerContext` |

---

## 🧩 Key Annotations

### `@Function`
Defines a deployable cloud function.

~~~java
@Function(name = "hello")
public class HelloFunction implements CloudRequestHandler<String, String> {
    public String handleRequest(String input, HandlerContext context) {
        return "Hello, " + input;
    }
}
~~~

### `@ScheduledTrigger`
Optional trigger to invoke a function on a regular schedule.

~~~java
@ScheduledTrigger(cron = "0 0 * * *")
~~~

### `@HttpTrigger`
Declares HTTP API metadata used to configure gateway routes.

~~~java
@HttpTrigger(method = "GET", path = "/hello", summary = "Say hello")
~~~

---

## 🧪 Implementing a Handler

All functions must implement `CloudRequestHandler<I, O>`:

~~~java
public interface CloudRequestHandler<I, O> {
    O handleRequest(I input, HandlerContext context);
}
~~~

You will receive an implementation of `HandlerContext` at runtime with request-specific metadata (e.g. request ID, region, etc.).

---

## ⚙️ Runtime Interfaces

These are the interfaces you implement or receive at runtime when writing Cloudhopper functions.

### `CloudRequestHandler<I, O>`

Defines the signature of a deployable cloud function.

~~~java
public interface CloudRequestHandler<I, O> {
    O handleRequest(I input, HandlerContext context);
}
~~~

You implement this interface to define the logic of your function. It receives:
- `input`: your deserialized request payload
- `context`: runtime metadata provided by the platform

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

