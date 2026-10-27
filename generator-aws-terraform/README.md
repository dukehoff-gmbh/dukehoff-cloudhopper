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
    <source>21</source>
    <target>21</target>
    <encoding>UTF-8</encoding>
    <annotationProcessors>
      <annotationProcessor>
        eu.cloudhopper.mc.deployment.config.generator.ServerlessFunctionProcessor
      </annotationProcessor>
    </annotationProcessors>
    <compilerArgs>
      <arg>-Acloudprovider=aws</arg>
      <arg>-AgeneratorId=aws-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/aws-terraform</arg>
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
src/main/resources/templates/aws-terraform/
```

| Template               | Output Type     | Purpose                                                         |
|------------------------|------------------|-----------------------------------------------------------------|
| `api.ftl`              | Terraform        | OpenAPI definition for API Gateway                              |
| `function.ftl`         | Terraform        | Lambda function resource                                        |
| `handler.ftl`          | Java             | Entry class using `AwsLambdaRequestHandler`                     |
| `integration.ftl`      | Terraform        | API Gateway/Lambda integration resource                         |
| `schedule.ftl`         | Terraform        | Scheduled (EventBridge/CloudWatch) trigger                      |
| `shared.ftl`           | Terraform        | Shared infrastructure (IAM roles, Lambda layer, etc.)           |

---

## 🔌 Template Registration

The generator is registered via:

```java
@TemplateRegistration(
    generatorId = "aws-terraform",
    templates = {
        @Template(phase = GenerationPhase.SHARED,   templateName = "shared.ftl",     outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.FUNCTION, templateName = "handler.ftl",    outputFileExtension = "java", outputSubDirectory = "generated", javaFile = true),
        @Template(phase = GenerationPhase.FUNCTION, templateName = "function.ftl",   outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.API,      templateName = "integration.ftl",outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.FINALIZE, templateName = "api.ftl",        outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.SCHEDULE, templateName = "schedule.ftl",   outputFileExtension = "tf",   javaFile = false)
    }
)
public class AwsTerraformOpenApiJava21TemplateRegistration { ... }
```

> `@Template` uses `templateName` (the `.ftl` file), `phase` (one of
> `FUNCTION`, `API`, `SCHEDULE`, `FINALIZE`, `SHARED`), `outputFileExtension`,
> optional `outputSubDirectory`, and `javaFile`.

Cloudhopper uses Java’s ServiceLoader (SPI) mechanism to discover and apply this registration.

---

## 📂 Output

| Artifact                       | Location                                 |
|--------------------------------|------------------------------------------|
| Java handler classes           | `target/generated-sources/aws-terraform/`          |
| Terraform configuration        | `target/deployment/aws-terraform/*.tf`             |
| Metadata (`handler-info.properties`) | `target/classes/META-INF/cloudhopper/` |
| Shaded JAR or ZIP              | `target/${artifactId}-${version}-aws.jar` or `.zip` |

---

## 🛠️ Notes for Generator Developers

- Handler classes extend `AwsLambdaRequestHandler` from the `provider-aws` module
- The generated infrastructure assumes the use of Terraform to deploy Lambda, IAM, and API Gateway resources
- You can override templates or inject additional files into the deployment directory by placing them in `src/main/resources/deployment/aws-terraform/`

---

## 📚 Related Modules

| Module              | Purpose                                               |
|---------------------|-------------------------------------------------------|
| `provider-aws`      | Provides the `AwsLambdaRequestHandler` runtime class  |
| `deployment-config-api` | Defines core annotations and function interface   |

---

📝 *Use this generator if you want to deploy Cloudhopper-based functions to AWS with minimal boilerplate and Terraform-based infrastructure-as-code.*
