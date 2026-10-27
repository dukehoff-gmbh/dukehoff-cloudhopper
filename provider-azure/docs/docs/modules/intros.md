# provider-azure

This module contains the core utility class `AzureBaseFunctionWrapper`, which is used by Cloudhopper generators to execute vendor-neutral functions as **Azure Functions**.

It is currently used by the `generator-azure-terraform` module and is important for anyone building or customizing generators for Azure.

---

## 🧩 Purpose

The `AzureBaseFunctionWrapper<I, O>` class:

- Encapsulates Azure-specific runtime concepts (e.g., `ExecutionContext`, `HttpRequestMessage`)
- Translates Azure’s execution context into a Cloudhopper-compatible `HandlerContext`
- Invokes a `CloudRequestHandler<I, O>` implementation using deserialized input
- Produces HTTP responses in JSON format
- Supports both HTTP-triggered and timer-triggered (scheduled) functions

---

## 🔧 Example Usage in a Generated Handler

```java
public class MyFunction extends AzureBaseFunctionWrapper<MyInput, MyOutput> {

    public MyFunction() {
        super(new MyCloudhopperFunction(), MyInput.class);
    }

    @FunctionName("myHttpFunction")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<String> request,
        final ExecutionContext context
    ) {
        return handleRequest(request, context);
    }
}
```

---

## ⏰ Supported Triggers

| Trigger Type | Method                      | Description                                      |
|--------------|-----------------------------|--------------------------------------------------|
| HTTP         | `handleRequest()`           | Parses input, invokes handler, returns response  |
| Timer        | `handleScheduledRequest()`  | Executes function without input on a schedule    |

---

## 🧠 Internal Structure

The class contains:

- A generic Jackson-based parser for request bodies (`parseInput`)
- Utility methods for creating success and error responses
- A nested `AzureContextAdapter` that implements `HandlerContext` using Azure’s `ExecutionContext`

⚠️ Some context values (like ARN or log stream name) are not available on Azure and return `null` or `-1`.

---

## 🛠️ For Generator Authors

If you're implementing your own Azure generator (e.g., replacing or extending `generator-azure-terraform`):

- Use `AzureBaseFunctionWrapper` as the base class for your generated function handlers
- Pass the actual `CloudRequestHandler` and the expected input `Type`
- Make sure this class is included in the deployment artifact (via shading or bundling)

---

## 📦 Module Summary

| Component                  | Description                                   |
|---------------------------|-----------------------------------------------|
| `AzureBaseFunctionWrapper`| Base class for generated Azure Function entrypoints |
| `AzureContextAdapter`     | Adapts `ExecutionContext` → `HandlerContext`  |

---

## 📚 Related Modules

| Module                    | Role                                                   |
|---------------------------|--------------------------------------------------------|
| `generator-azure-terraform` | Uses this class in generated Azure Function handlers  |
| `deployment-config-api`     | Defines `CloudRequestHandler` and `HandlerContext`    |

---

```java title="AzureBaseFunctionWrapper.java"
public abstract class AzureBaseFunctionWrapper<I, O> {
    // ...
}
```

This class acts as the runtime bridge between Azure Functions and Cloudhopper’s provider-independent function logic.
