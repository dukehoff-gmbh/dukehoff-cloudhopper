# ☁️ GCP Terraform Generator

This module provides a Cloudhopper generator for deploying annotated Java functions as **Google Cloud Functions (HTTP-triggered)** using **Terraform**.

It is used by the Cloudhopper annotation processor to generate:

- GCP-specific Java handler classes
- Terraform infrastructure resources
- Supporting metadata for integration and deployment

---

## 📦 How to Use

To use this generator in your own project, follow these steps:

---

### 1. Add the Maven dependency

```xml
<dependency>
  <groupId>eu.cloudhopper.mc</groupId>
  <artifactId>generator-gcp-terraform</artifactId>
  <version>${cloudhopper.version}</version>
</dependency>
```

---

### 2. Configure the annotation processor

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.8.1</version>
  <configuration>
    <source>21</source>
    <target>21</target>
    <encoding>UTF-8</encoding>
    <annotationProcessors>
      <annotationProcessor>
        eu.cloudhopper.mc.deployment.config.generator.ServerlessFunctionProcessor
      </annotationProcessor>
    </annotationProcessors>
    <compilerArgs>
      <arg>-Acloudprovider=gcp</arg>
      <arg>-AgeneratorId=gcp-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/gcp-terraform</arg>
      <arg>-AtargetDir=${project.build.directory}</arg>
      <arg>-AartifactId=${project.artifactId}</arg>
      <arg>-Aversion=${project.version}</arg>
      <arg>-Aclassifier=gcp</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

This configuration ensures that the correct templates are applied and the output is structured for GCP.

---

## 📁 Templates

Templates for this generator are located at:

```
src/main/resources/templates/gcp-terraform/
```

| Template                  | Output Type | Description                                        |
|---------------------------|-------------|----------------------------------------------------|
| `handler.ftl`             | Java        | Generates function class that wraps the logic      |
| `function.ftl`            | Terraform   | Terraform registration for the Cloud Function      |
| `apiIntegrationClass.ftl` | Java        | Generates an APIFunction                           |
| `apiIntegration.ftl`      | JSON        | OpenAPI 2.0 snippet for the API route              |
| `apiRegistration.ftl`     | Terraform   | Terraform function registration                    |
| `api.ftl`                 | Terraform   | `google_api_gateway_api` resource                  |
| `schedule.ftl`            | Terraform   | `google_cloud_scheduler_job` resource              |
| `shared.ftl`              | Terraform   | Common infrastructure like IAM roles, logging, etc. |

> ℹ️ The `doc/` folder in the template path is not used during generation. It may contain examples or template prototypes.

---

## 🔌 Template Registration

This generator registers itself using:

```java
@TemplateRegistration(
    generatorId = "gcp-terraform",
    templates = {
        @Template(phase = GenerationPhase.SHARED,   templateName = "shared.ftl",             outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.FUNCTION, templateName = "handler.ftl",            outputFileExtension = "java", outputSubDirectory = "generated", javaFile = true),
        @Template(phase = GenerationPhase.FUNCTION, templateName = "function.ftl",           outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.API,      templateName = "apiIntegrationClass.ftl",outputFileExtension = "java", javaFile = true),
        @Template(phase = GenerationPhase.API,      templateName = "apiIntegration.ftl",     outputFileExtension = "json", outputSubDirectory = "api-routes/", javaFile = false),
        @Template(phase = GenerationPhase.API,      templateName = "apiRegistration.ftl",    outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.SCHEDULE, templateName = "schedule.ftl",           outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.FINALIZE, templateName = "api.ftl",                outputFileExtension = "tf",   javaFile = false)
    }
)
public class GcpTerraformJava21TemplateRegistration { ... }
```

> `@Template` uses `templateName` (the `.ftl` file), `phase` (one of
> `FUNCTION`, `API`, `SCHEDULE`, `FINALIZE`, `SHARED`), `outputFileExtension`,
> optional `outputSubDirectory`, and `javaFile`.

This registration is auto-discovered using Java’s SPI (`META-INF/services/...`) mechanism.

---

## 📂 Output

| Artifact                        | Location                                 |
|---------------------------------|------------------------------------------|
| Java handler classes            | `target/generated-sources/gcp-terraform/`          |
| Terraform configuration         | `target/deployment/gcp-terraform/*.tf`             |
| Deployment metadata             | `target/classes/META-INF/cloudhopper/`   |
| Optional ZIP for deployment     | `target/${artifactId}-${version}-gcp.zip` |

---

## 🧩 What’s Special About GCP

- **Handler Generation**: The generator produces a Java class that implements `com.google.cloud.functions.HttpFunction`.
- **Context Mapping**: The handler uses `GcpCloudFunctionRequestHandler`, which adapts GCP’s request/response and environment into Cloudhopper's `CloudRequestHandler` interface.
- **Terraform Output**: The generated `.tf` files define the Cloud Function, IAM permissions, and optional API Gateway or scheduler bindings.

---

## 📚 Related Modules

| Module              | Description                                                 |
|---------------------|-------------------------------------------------------------|
| `provider-gcp`      | Contains `GcpCloudFunctionRequestHandler` used by handlers  |
| `deployment-config-api` | Defines annotations and runtime interfaces              |

---

📝 *Use this generator to compile and deploy Cloudhopper functions as Google Cloud Functions using Terraform.*
