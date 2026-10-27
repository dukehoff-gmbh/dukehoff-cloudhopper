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
    <source>17</source>
    <target>17</target>
    <encoding>UTF-8</encoding>
    <annotationProcessors>
      <annotationProcessor>
        eu.cloudhopper.mc.deployment.config.generator.ServerlessFunctionProcessor
      </annotationProcessor>
    </annotationProcessors>
    <compilerArgs>
      <arg>-Acloudprovider=gcp</arg>
      <arg>-AgeneratorId=gcp-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/gcp</arg>
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
src/main/resources/templates/gcp/
```

| Template               | Output Type | Description                                        |
|------------------------|-------------|----------------------------------------------------|
| `function.ftl`         | Terraform   | Defines GCP Cloud Function resource                |
| `api.ftl`              | Terraform   | API Gateway or routing proxy configuration         |
| `apiIntegration.ftl`   | Terraform   | Binds function with GCP API Gateway (if needed)    |
| `schedule.ftl`         | Terraform   | Scheduled (cron) job configuration via Cloud Scheduler |
| `handler.ftl`          | Java        | Generates function class that wraps the logic      |
| `shared.ftl`           | Terraform   | Common infrastructure like IAM roles, logging, etc. |

> ℹ️ The `doc/` folder in the template path is not used during generation. It may contain examples or template prototypes.

---

## 🔌 Template Registration

This generator registers itself using:

```java
@TemplateRegistration(
    generatorId = "gcp-terraform",
    templates = {
        @Template(name = "handler", phase = GenerationPhase.SOURCES),
        @Template(name = "function", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "api", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "apiIntegration", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "schedule", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "shared", phase = GenerationPhase.DEPLOYMENT)
    }
)
public class GcpTerraformJava21TemplateRegistration { ... }
```

This registration is auto-discovered using Java’s SPI (`META-INF/services/...`) mechanism.

---

## 📂 Output

| Artifact                        | Location                                 |
|---------------------------------|------------------------------------------|
| Java handler classes            | `target/generated-sources/gcp/`          |
| Terraform configuration         | `target/deployment/gcp/*.tf`             |
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
