# provider-gcp

This module contains the core utility class `GcpCloudFunctionRequestHandler`, used by Cloudhopper-based generators to execute cloud-neutral functions in **Google Cloud Functions**.

It is used internally by the `generator-gcp-terraform` module and is essential for developers writing custom GCP serverless generators.

---

## 🧩 Purpose

The `GcpCloudFunctionRequestHandler<I, O>` class:

- Implements the `HttpFunction` interface required by Google Cloud Functions
- Deserializes JSON input using `Gson`
- Invokes a generic `CloudRequestHandler<I, O>`
- Serializes the output as a JSON HTTP response
- Adapts GCP-specific request metadata into Cloudhopper's `HandlerContext`

---

## 🔧 Example Usage in a Generated Function

```java
public class MyFunction extends GcpCloudFunctionRequestHandler<MyInput, MyOutput> {

    public MyFunction() {
        super(new MyCloudhopperFunction(), TypeToken.get(MyInput.class));
    }
}
```

Google Cloud Functions will call `service(...)` on this class, which in turn:

1. Parses the HTTP request body into a Java object
2. Passes it to your `CloudRequestHandler`
3. Serializes the result to the HTTP response

---

## 🧠 Internal Structure

| Method | Responsibility |
|--------|----------------|
| `service(...)` | GCF entrypoint: parse, delegate, serialize |
| `parseInput(...)` | Uses Gson to deserialize JSON input |
| `writeOutput(...)` | Converts output to JSON and writes to response |
| `GcpContextAdapter` | Implements `HandlerContext` using GCF metadata (e.g. env vars and headers) |

Some AWS/Azure-specific context features (ARN, logs, etc.) are not available on GCP and return `null` or `-1`.

---

## 💡 For Generator Developers

If you are building your own GCP generator (or extending `generator-gcp-terraform`):

- Use `GcpCloudFunctionRequestHandler` as the base class for the generated function handler
- Provide:
  - A reference to your `CloudRequestHandler`-based function
  - A `TypeToken<I>` to support proper deserialization of generic input
- Ensure this class is included in the deployment JAR

---

## 📦 Module Overview

| Component | Purpose |
|----------|---------|
| `GcpCloudFunctionRequestHandler` | Generic HTTP adapter for Google Cloud Functions |
| `GcpContextAdapter` | Maps GCF environment and headers to Cloudhopper’s `HandlerContext` |

---

## 📚 Related Modules

| Module | Description |
|--------|-------------|
| `generator-gcp-terraform` | Uses this class to generate deployable GCP function handlers |
| `deployment-config-api` | Defines `CloudRequestHandler` and `HandlerContext` interfaces |

---

```java title="GcpCloudFunctionRequestHandler.java"
public abstract class GcpCloudFunctionRequestHandler<I, O> implements HttpFunction {
    // ...
}
```

This class is the GCP-specific bridge between Cloudhopper’s cloud-agnostic logic and Google’s serverless infrastructure.
