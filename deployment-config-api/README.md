---
title: Deployment Config API
---

# deployment-config-api

This module defines the public annotation and runtime API used by developers to create cloud functions with Cloudhopper.

It provides the key building blocks for function declaration, scheduling, and API gateway integration, independent of any specific cloud provider.

## 💡 Purpose

The goal of this module is to allow application developers to define serverless functions in a clean, portable way using standard Java annotations.

This module is **used by developers**, **referenced by integrators**, and **consumed by the annotation processor**.

## 📦 Package Overview

| Package                          | Description                                                             |
|----------------------------------|-------------------------------------------------------------------------|
| `eu.cloudhopper.mc.annotations` | Core annotations like `@Function`, `@ScheduledTrigger`, `@HttpTrigger` |
| `eu.cloudhopper.mc.runtime`     | Runtime types such as `HandlerContext`                                  |

## 🧩 Key Annotations

### `@Function`

Defines a deployable cloud function.

```java
@Function(name = "hello")
@HttpTrigger(method = "GET", path = "/hello", summary = "Say hello")
public String handleRequest() {
    return "Hello!";
}
```

### `@ScheduledTrigger`

Optional trigger to invoke a function on a regular schedule.

```java
@Function(name = "dailyTask")
@ScheduledTrigger(cron = "0 0 * * *")
public void runDailyTask() {
    // do something every day at midnight
}
```

### `@HttpTrigger`

Declares HTTP API metadata used to configure gateway routes.

```java
@Function(name = "greet")
@HttpTrigger(method = "POST", path = "/greet", summary = "Greet user")
public String greetUser(@RequestBody String name) {
    return "Hello, " + name;
}
```

## ⚙️ Runtime Types

These are the types you may receive at runtime when writing Cloudhopper functions.

### `HandlerContext`

Provides metadata about the current function invocation.

```java
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
```

The platform adapter (e.g. AWS, Azure, GCP) supplies this to your function. It gives access to:

- invocation-specific IDs
- logging information
- timeout and memory limits

## 🔗 Related Modules

- `deployment-config-generator-api`: used by integrators to build custom code generators
- `deployment-config-processor`: uses this module to process annotations at compile time

## 👤 Intended Audience

This module is for:

- Application developers writing cloud functions
- Generator implementors referencing annotation types
