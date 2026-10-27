# ☁️ Cloud Provider Generator Overview

Cloudhopper supports serverless deployment to multiple cloud providers through provider-specific **generator modules**. These generators produce:

- Java adapter classes to bridge Cloudhopper with cloud-native function interfaces
- Terraform configuration for provisioning infrastructure
- Supporting metadata for packaging and deployment

---

## ✨ Comparison

| Feature                          | AWS Generator                          | Azure Generator                          | GCP Generator                              |
|----------------------------------|----------------------------------------|------------------------------------------|--------------------------------------------|
| **Module**                       | `generator-aws-terraform`              | `generator-azure-terraform`              | `generator-gcp-terraform`                  |
| **Handler Output**               | Java (Lambda + API Gateway)            | Java (HTTP & Timer Function wrappers)    | Java (HttpFunction)                        |
| **Terraform Output**            | Full (Lambda, API Gateway, IAM, etc.)  | Shared infra only                        | Full (Function, Scheduler, IAM, etc.)      |
| **Runtime Adapter**              | `AwsLambdaRequestHandler`              | `AzureBaseFunctionWrapper`               | `GcpCloudFunctionRequestHandler`           |
| **Java Templates**               | `handler.ftl`, `apiIntegrationClass.ftl` | `handler.ftl`, `function.ftl`, `schedule.ftl` | `handler.ftl`                          |
| **Terraform Templates**         | `function.ftl`, `api.ftl`, `integration.ftl`, `schedule.ftl`, `shared.ftl` | `shared.ftl` only                   | `function.ftl`, `api.ftl`, `apiIntegration.ftl`, `schedule.ftl`, `shared.ftl` |
| **Generator ID**                | `aws-terraform`                | `azure-terraform`                | `gcp-terraform`                    |
| **Primary Use Case**            | API endpoints via Lambda + Gateway     | HTTP-triggered and scheduled Functions   | Cloud Functions + optional Gateway         |

---

## 🛠️ Maven Configuration (Common Structure)

To activate a generator, you need:

1. A generator dependency
2. Annotation processing (`ServerlessFunctionProcessor`)
3. Build properties set via `compilerArgs`

### AWS Example

```xml
<arg>-Acloudprovider=aws</arg>
<arg>-AgeneratorId=aws-terraform</arg>
```

### Azure Example

```xml
<arg>-Acloudprovider=azure</arg>
<arg>-AgeneratorId=azure-terraform</arg>
```

### GCP Example

```xml
<arg>-Acloudprovider=gcp</arg>
<arg>-AgeneratorId=gcp-terraform</arg>
```

---

## 🔌 Related Modules

| Generator Module           | Runtime Adapter Module |
|---------------------------|------------------------|
| `generator-aws-terraform` | `provider-aws`         |
| `generator-azure-terraform` | `provider-azure`     |
| `generator-gcp-terraform` | `provider-gcp`         |

---

## 📚 See Also

- [AWS Generator](../modules/provider-aws.md)
- [Azure Generator](../modules/provider-azure.md)
- [GCP Generator](../modules/provider-gcp.md)
