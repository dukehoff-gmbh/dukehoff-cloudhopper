# Demo Project Guide

This module demonstrates how to use Cloudhopper to generate deployment artifacts from annotated Java functions for multiple cloud providers.
 It supports AWS, GCP, and Azure via Maven profiles and includes examples for both HTTP-triggered and scheduled functions. 
Please note that there are some properties in the pom.xml that need to be changed if you want to use the terraform tasks. Those are described in the section on **Terraform Integration**.

---
## 🔧 Prerequisites

Before you can build and deploy the demo project, make sure the following tools are installed and available in your `PATH`.

### 🧱 Build Tools 

| Tool   | Required Version | Notes |
|--------|------------------|-------|
| **JDK** | 21 or later       | The project targets Java 21; ensure `java -version` points to a compatible JDK |
| **Maven** | 3.8 or later     | Used to compile, run annotation processors, and package deployments |

To verify:

~~~
java -version
mvn -v
~~~

---

### 🛠️ Terraform

Terraform is used to apply the generated infrastructure configuration.

| Tool       | Required Version | Notes |
|------------|------------------|-------|
| **Terraform** | 1.3 or later     | Specified via `terraform.executable` in the `pom.xml` |

To verify:

~~~
terraform version
~~~

---

### ☁️ Cloud Provider CLIs

Depending on the active Maven profile, you’ll need CLI tools for the cloud provider (or project type) you’re targeting:

#### Spring Boot (no Terraform)

~~~
mvn clean install -Pspringboot-http
~~~

This generates a simple springboot project.

> The profile IDs are `aws-terraform`, `gcp-terraform`, `azure-terraform` and
> `springboot-http` &mdash; each matches the `{generator.id}` used for the
> generated output directories below.


#### AWS 

- Install the [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- Authenticate via:

  ~~~
  aws configure
  ~~~

- Terraform uses the `AWS_PROFILE` environment variable (e.g. `dukehoff`) defined in the Maven plugin execution.

#### GCP

- Install the [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)
- Authenticate via:

  ~~~
  gcloud auth login
  gcloud config set project your-project-id
  ~~~

#### Azure

- Install the [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)
- Authenticate via:

  ~~~
  az login
  ~~~

---

💡 You can define custom paths to Terraform and CLI tools via properties in the `pom.xml`, such as:

```xml
<terraform.executable>/opt/homebrew/bin/terraform</terraform.executable>
<path.extras>/opt/homebrew/bin/</path.extras>
```

This ensures compatibility with local setups like Homebrew on macOS.

---

## Build Instructions

To compile the project and run the annotation processor:

~~~
mvn clean install -Paws-terraform
~~~

You can switch providers using:

~~~
mvn clean install -Pgcp-terraform
mvn clean install -Pazure-terraform
mvn clean install -Pspringboot-http
~~~

Generated resources will be placed in the following directories based on the active profile:

| Profile | Cloud Provider | Generated Sources             | Deployment Output                    |
|---------|----------------|-------------------------------|--------------------------------------|
| `aws`   | AWS Lambda     | `target/generated-sources/aws-terraform`   | `target/deployment/aws-terraform`             |
| `gcp`   | GCP Functions  | `target/generated-sources/gcp-terraform`   | `target/deployment/gcp-terraform`             |
| `azure` | Azure Functions| `target/generated-sources/azure-terraform` | `target/deployment/azure-terraform`           |
| `springboot` | None/local| `target/generated-sources/springboot-http` | `target/deployment/springboot-http`           |

---

## Structure and Generated Files

### 📁 `target/generated-sources/{generator.id}`

Contains provider-specific **function handlers** that wrap your annotated methods. For each `@Function` method the processor generates a core `<Class>_<method>_Handler` plus a provider wrapper. Example (AWS):

~~~
target/generated-sources/aws-terraform/eu/cloudhopper/mc/demo/
  ├── ApiFunction_handleRequest_Handler.java
  ├── AwsLambdaApiFunction_handleRequest_HandlerHandler.java
  ├── ScheduledFunction_handleRequest_Handler.java
  └── AwsLambdaScheduledFunction_handleRequest_HandlerHandler.java
~~~

These are compiled and included in the shaded JAR for deployment.

---

### 📁 `target/deployment/{generator.id}`

Contains:

- `main.tf` and related Terraform resources (`*.tf`)
- Cloudhopper metadata (`handler-info.properties`)
- Any user-defined `.tf` files copied from `src/main/resources/deployment/{generator.id}`

Example (AWS):

~~~
target/deployment/aws-terraform/
  ├── main.tf
  ├── api_api.tf
  ├── hello_world_2_function.tf
  ├── hello_world_2_api_integration.tf
  ├── scheduledfunction_function.tf
  ├── scheduledfunction_schedule_schedule.tf
  ├── shared-resources_shared.tf
  └── META-INF/handler-info.properties
~~~

These files are used by the Terraform CLI to deploy your function stack.

---

### 📁 `src/main/resources/deployment/{generatorId}`

Optional folder where you can place **manually created resources**. Its content is copied into the corresponding `target/deployment/{generatorID}` folder at build time.

Use this to:

- Add your own `*.tf` files
- Include helper scripts or documentation

---

## Function Examples

You write plain methods and annotate them &mdash; there is no interface to
implement. The annotation processor generates the provider-specific handler.

### HTTP-triggered function

```java
public class ApiFunction {

    @HttpTrigger(
        operationId = "helloworld2",
        method = "POST",
        path = "/hello2/{id}",
        summary = "bla",
        description = "dummy description"
    )
    @Function(
        name = "hello_world_2",
        extensions = {
            @Extension(
                name = ExtensionKeys.Aws.Architecture.KEY,
                value = ExtensionKeys.Aws.Architecture.Values.ARM64)
        })
    public String handleRequest(@RequestBody Integer input) {
        return "Hallo " + input;
    }
}
```

The request body is bound with `@RequestBody`; use `@PathParam`, `@QueryParam`
or `@HeaderParam` to bind other parts of the request.

### Scheduled function

A scheduled function needs no input or output &mdash; a plain `void` method is enough.

```java
public class ScheduledFunction {

    @Function(name = "ScheduledFunction")
    @ScheduledTrigger(cron = "0 0 * * *")
    public void handleRequest() {
        System.out.println("scheduled action is performed");
    }
}
```

---
## ⚙️ How Code Generation Works

Cloudhopper uses annotation processors at **build time** to scan your Java classes for specific annotations (e.g. `@Function`, `@ScheduledTrigger`) and generate deployment metadata using pre-defined **Freemarker templates**.

### 🧩 Annotation Processor Setup

The annotation processor is registered via:

```xml
<annotationProcessor>
  eu.cloudhopper.mc.deployment.config.generator.ServerlessFunctionProcessor
</annotationProcessor>
```

This processor is triggered automatically by the Maven Compiler Plugin during the `compile` phase. It reads:

- Java annotations such as `@Function`, `@ScheduledTrigger`
- Compile-time parameters (e.g. `cloudprovider`, `generatorId`, `targetDir`)
- Project metadata (e.g. artifact ID, version)

Based on these, it looks up the correct **template set** and generates the following:

- Cloud-specific handler Java classes
- Terraform `.tf` deployment scripts
- Metadata files in `META-INF/`

---

### 📁 Where the Templates Are

Templates are organized by cloud provider in their respective generator modules:

| Module | Location | Notes |
|--------|----------|-------|
| `generator-aws-terraform` | `src/main/resources/templates/aws-terraform/` | Terraform and AWS Lambda integration templates |
| `generator-gcp-terraform` | `src/main/resources/templates/gcp-terraform/` | Templates for GCP Cloud Functions |
| `generator-azure-terraform` | `src/main/resources/templates/azure-terraform/` | Templates for Azure Functions and resources |
| `springboot-http`| `src/main/resources/templates/springboot-http/` | Templates for SpringBoot |


Each folder contains files like:

- `function.ftl` — per-function deployment definition
- `schedule.ftl` — scheduled trigger definition
- `handler.ftl` — Java wrapper class
- `shared.ftl` — common infrastructure

---

### 🧷 Template Registration

Each generator module includes a `TemplateRegistration` class, which registers all the templates and features exposed by that provider.

Examples:

- `generator-gcp-terraform`  
  → `GcpTerraformJava21TemplateRegistration`

- `generator-aws-terraform`  
  → `AwsTerraformOpenApiJava21TemplateRegistration`

- `generator-azure-terraform`  
  → `AzureTerraformJava21TemplateRegistration`

These classes are auto-discovered via the standard `META-INF/services/...` SPI mechanism and used by the processor to:

1. Load available templates
2. Register feature support (e.g., schedules, API integration)
3. Apply provider-specific options if needed

---

### 📂 Output Structure

The templates are rendered and written to:

- Java handlers:  
  `target/generated-sources/{provider}`

- Terraform config & metadata:  
  `target/deployment/{generator.id}`


This allows you to inspect or customize any generated artifact before deploying.

---

## Output Artifacts

After running the build, the following artifacts are created:

- **Shaded JAR** with all function classes and generated handlers:

  ```
  target/demo-1.0-SNAPSHOT-{provider}.jar
  ```

- **Terraform deployment bundle** (when using GCP/Azure ZIP packaging):

  ```
  target/demo-1.0-SNAPSHOT-{provider}.zip
  ```

---

## Terraform Integration

Terraform initialization and planning can be done automatically using the 'deploy-with-terraform' profile. 
It uses the exec-maven-plugin to run terraform:

```xml
<plugin>
  <artifactId>exec-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>terraform-init</id>
      <phase>verify</phase>
      ...
    </execution>
    <execution>
      <id>terraform-plan</id>
      <phase>verify</phase>
      ...
    </execution>
  </executions>
</plugin>
```

Please note that in order to succeed you will have to adjust some properties in the pom.xml specifying the path and the terraform executable in the properties section.
You will also need to adjust the value of AWS_PROFILE.

```
<properties>
    ...
    <terraform.executable>/opt/homebrew/bin/terraform</terraform.executable>
    <path.extras>/opt/homebrew/bin/</path.extras>
</properties>
``` 

Alternatively you can run Terraform manually in the output directory as well:

~~~
cd target/deployment/aws-terraform
terraform apply
~~~

---