# ☁️ AWS Terraform Generator

This module provides a Cloudhopper generator for deploying annotated Java functions as **AWS Lambda functions** using **Terraform**.

It generates:

- AWS-specific handler classes
- Terraform configuration files
- Integration metadata to bridge vendor-neutral logic with AWS infrastructure

---

## 📦 How to Use

To enable this generator in your own project, you need to:

---

### 1. Add the Maven dependency

```xml
<dependency>
  <groupId>eu.cloudhopper.mc</groupId>
  <artifactId>generator-aws-terraform</artifactId>
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
      <arg>-Acloudprovider=aws</arg>
      <arg>-AgeneratorId=aws-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/aws</arg>
      <arg>-AtargetDir=${project.build.directory}</arg>
      <arg>-AartifactId=${project.artifactId}</arg>
      <arg>-Aversion=${project.version}</arg>
      <arg>-Aclassifier=aws</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

This setup enables Cloudhopper to generate AWS-specific Terraform and handler code.

---

## 📁 Templates

Templates used by this generator are located under:

```
src/main/resources/templates/aws/
```

| Template               | Output Type     | Purpose                                                         |
|------------------------|------------------|-----------------------------------------------------------------|
| `api.ftl`              | Terraform        | API Gateway integration resource                                |
| `apiIntegrationClass.ftl` | Java           | API handler implementation                                      |
| `function.ftl`         | Terraform        | Lambda function resource                                        |
| `handler.ftl`          | Java             | Entry class using `AwsLambdaRequestHandler`                     |
| `integration.ftl`      | Terraform        | API Gateway/Lambda integration resource                         |
| `schedule.ftl`         | Terraform        | Scheduled (CloudWatch Event) trigger                            |
| `shared.ftl`           | Terraform        | Shared infrastructure (IAM roles, Lambda layer, etc.)           |

---

## 🔌 Template Registration

The generator is registered via:

```java
@TemplateRegistration(
    generatorId = "aws-terraform",
    templates = {
        @Template(name = "handler", phase = GenerationPhase.SOURCES),
        @Template(name = "apiIntegrationClass", phase = GenerationPhase.SOURCES),
        @Template(name = "function", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "api", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "integration", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "schedule", phase = GenerationPhase.DEPLOYMENT),
        @Template(name = "shared", phase = GenerationPhase.DEPLOYMENT)
    }
)
public class AwsTerraformOpenApiJava21TemplateRegistration { ... }
```

Cloudhopper uses Java’s ServiceLoader (SPI) mechanism to discover and apply this registration.

---

## 📂 Output

| Artifact                       | Location                                 |
|--------------------------------|------------------------------------------|
| Java handler classes           | `target/generated-sources/aws/`          |
| Terraform configuration        | `target/deployment/aws/*.tf`             |
| Metadata (`handler-info.properties`) | `target/classes/META-INF/cloudhopper/` |
| Shaded JAR or ZIP              | `target/${artifactId}-${version}-aws.jar` or `.zip` |

---

## 🛠️ Notes for Generator Developers

- Handler classes extend `AwsLambdaRequestHandler` from the `provider-aws` module
- The generated infrastructure assumes the use of Terraform to deploy Lambda, IAM, and API Gateway resources
- You can override templates or inject additional files into the deployment directory by placing them in `src/main/resources/deployment/aws/`

---

## 📚 Related Modules

| Module              | Purpose                                               |
|---------------------|-------------------------------------------------------|
| `provider-aws`      | Provides the `AwsLambdaRequestHandler` runtime class  |
| `deployment-config-api` | Defines core annotations and function interface   |

---

📝 *Use this generator if you want to deploy Cloudhopper-based functions to AWS with minimal boilerplate and Terraform-based infrastructure-as-code.*
