# ☁️ provider-aws

This module provides AWS-specific base classes for Cloudhopper functions. These classes act as bridges between the AWS Lambda runtime and the cloud-neutral `CloudRequestHandler<I, O>` interface.

They are used by generated handler classes in the `generator-aws-terraform` module to support:

- Plain Lambda invocations
- Scheduled executions (e.g., EventBridge)
- API Gateway HTTP APIs (v2), both proxy and typed events
- Automatic runtime dispatch via a unified router

---

## 🚀 Purpose

These base classes enable Cloudhopper functions to:

- Stay **platform-neutral** in their logic
- Be executed in **multiple AWS contexts**
- Use a consistent interface for path/query parameters and runtime metadata
- Simplify Lambda handler generation and reuse

---

## 🔧 Available Base Classes

| Class | Trigger Type | Input | Output | Used for |
|-------|--------------|-------|--------|----------|
| `AwsLambdaRequestHandler<I, O>` | Lambda direct or scheduled | `I` (or `null`) | POJO | `Plain` |
| `ApiGatewayV2ProxyRequestHandler<I, O>` | API Gateway v2 (proxy) | `APIGatewayV2ProxyRequestEvent` | `APIGatewayV2ProxyResponseEvent` | `ApiProxyV2` |
| `ApiGatewayV2HttpEventHandler<I, O>` | API Gateway v2 (typed) | `APIGatewayV2HTTPEvent` | `APIGatewayV2HTTPResponse` | `ApiHttpV2` |
| `ApiGatewayEventRouter<I, O>` | Dynamic | `Object` | `Object` | `Auto` router that delegates to the appropriate handler |

---

## 🧪 Example Usage

Generated handlers typically look like this:

```java
public class AwsLambdaMyFunctionHandler {

    public static class Plain extends AwsLambdaRequestHandler<InputType, OutputType> {
        public Plain() {
            super(new MyFunction());
        }
    }

    public static class ApiProxyV2 extends ApiGatewayV2ProxyRequestHandler<InputType, OutputType> {
        public ApiProxyV2() {
            super(new MyFunction(), InputType.class);
        }
    }

    public static class ApiHttpV2 extends ApiGatewayV2HttpEventHandler<InputType, OutputType> {
        public ApiHttpV2() {
            super(new MyFunction(), InputType.class);
        }
    }

    public static class Auto extends ApiGatewayEventRouter<InputType, OutputType> {
        public Auto() {
            super(new Plain(), new ApiProxyV2(), new ApiHttpV2());
        }
    }
}
```

In Terraform-based deployments, the `Auto` class is typically used as the Lambda entry point:

```hcl
handler = "com.example.AwsLambdaMyFunctionHandler$Auto::handleRequest"
```

---

## 🧭 Scheduled Events

Scheduled EventBridge or CloudWatch invocations will result in `input == null`. These are automatically routed to the `Plain` handler.

This behavior is consistent across cloud providers and allows developers to treat scheduled calls as "fire-and-forget" jobs.

---

## 📦 Packaging

All base classes in this module must be available at runtime (e.g., in the shaded JAR).  
They are required for dispatch, input parsing, and context adaptation.

---

## 💡 For Generator Authors

If you're building your own AWS generator module (like `generator-aws-terraform`), you should:

- Use the correct base class for each handler type
- Register `Auto` as the Lambda entry point to allow unified routing
- Pass `null` as input for scheduled calls

---

## 📚 Related Modules

| Module | Purpose |
|--------|---------|
| `generator-aws-terraform` | Uses these base classes to generate AWS Lambda handler classes |
| `deployment-config-api` | Provides `CloudRequestHandler` and `HandlerContext` interfaces |

---

```java title="AwsLambdaRequestHandler.java"
public abstract class AwsLambdaRequestHandler<I, O> implements RequestHandler<I, O> {
    // ...
}
```
These base classes form the backbone of Cloudhopper’s AWS Lambda support, enabling clean separation of infrastructure, runtime integration, and platform-neutral business logic.