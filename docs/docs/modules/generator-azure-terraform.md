# Azure Terraform Generator

This module provides a Cloudhopper generator that enables the deployment of annotated Java functions as **Azure Functions** using **Terraform** and **Java handler code generation**.

Azure is unique in that triggers are Java annotations: Cloudhopper generates a Java class with `@HttpTrigger`, `@TimerTrigger` or `@QueueTrigger` per function. Terraform creates the infrastructure they bind to: the function app and storage account, and the queues, blob containers and Event Grid subscriptions of event-triggered functions.

---

## Documentation Notice:

Every Cloudhopper function on Azure is exposed via HTTP under the route `/api/{name}_http_function_trigger`; functions with an `@HttpTrigger` are additionally served under their own path.
- Direct invocation via SDK (like AWS Lambda invoke()) is not supported on Azure — Azure Functions require trigger bindings.

Therefore, every Cloudhopper function in Azure is accessible via an HTTP call to its assigned route.

## How to Use

To use this generator in your own project, configure the following:

---

### 1. Add the dependency

```xml
<dependency>
  <groupId>eu.cloudhopper.mc</groupId>
  <artifactId>generator-azure-terraform</artifactId>
  <version>${cloudhopper.version}</version>
</dependency>
```

---

### 2. Configure annotation processing

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
      <arg>-Acloudprovider=azure</arg>
      <arg>-AgeneratorId=azure-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/azure-terraform</arg>
      <arg>-AtargetDir=${project.build.directory}</arg>
      <arg>-AartifactId=${project.artifactId}</arg>
      <arg>-Aversion=${project.version}</arg>
      <arg>-Aclassifier=azure</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

---

### 3. Add Azure Functions Maven Plugin

This plugin is used to **package and deploy** the generated Azure function handler classes.

```xml
<plugin>
  <groupId>com.microsoft.azure</groupId>
  <artifactId>azure-functions-maven-plugin</artifactId>
  <version>${azure.functions.maven.plugin.version}</version>
  <configuration>
    <appName>${functionAppName}</appName>
    <resourceGroup>java-functions-group</resourceGroup>
    <appServicePlanName>java-functions-app-service-plan</appServicePlanName>
    <region>westus</region>
    <runtime>
      <os>linux</os>
      <javaVersion>11</javaVersion>
    </runtime>
    <appSettings>
      <property>
        <name>FUNCTIONS_EXTENSION_VERSION</name>
        <value>~4</value>
      </property>
    </appSettings>
    <skipInstallExtensions>true</skipInstallExtensions>
  </configuration>
  <executions>
    <execution>
      <id>package-functions</id>
      <goals>
        <goal>package</goal>
      </goals>
      <phase>package</phase>
    </execution>
  </executions>
</plugin>
```

---

### 4. Optional: ZIP Packaging for Terraform Deployment

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-assembly-plugin</artifactId>
  <version>3.3.0</version>
  <executions>
    <execution>
      <id>make-zip</id>
      <phase>package</phase>
      <goals>
        <goal>single</goal>
      </goals>
      <configuration>
        <descriptors>
          <descriptor>src/assembly/azure-zip.xml</descriptor>
        </descriptors>
        <finalName>${project.artifactId}-${project.version}-${jar.classifier}</finalName>
        <appendAssemblyId>false</appendAssemblyId>
      </configuration>
    </execution>
  </executions>
</plugin>
```

---

## Templates

Templates are located under:

```
src/main/resources/templates/azure-terraform/
```

| Template                 | Output Type | Purpose                                            |
|--------------------------|-------------|----------------------------------------------------|
| `apiIntegrationClass.ftl` | Java        | Entry class for API functions                      |
| `function.ftl`           | Java        | HTTP function handler                              |
| `output.ftl`             | Terraform   | URLs to call the generated functions               |
| `schedule.ftl`           | Java        | Scheduled function handler                         |
| `shared.ftl`             | Terraform   | Base infrastructure setup (Function App, etc.)     |
| `queueFunction.ftl`      | Java        | `@QueueTrigger` function for queue messages and blob events |
| `queue.ftl`              | Terraform   | Storage queue (`@EventTrigger` `QUEUE`)            |
| `storage.ftl`            | Terraform   | Blob container, event queue, Event Grid subscription (`@EventTrigger` `STORAGE`) |

---

## Template Registration

```java
@TemplateRegistration(
    generatorId = "azure-terraform",
    templates = {
        @Template(phase = GenerationPhase.SHARED,   templateName = "shared.ftl",              outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.FUNCTION, templateName = "function.ftl",            outputFileExtension = "java", outputSubDirectory = "generated", javaFile = true),
        @Template(phase = GenerationPhase.API,      templateName = "apiIntegrationClass.ftl", outputFileExtension = "java", javaFile = true),
        @Template(phase = GenerationPhase.API,      templateName = "output.ftl",              outputFileExtension = "tf",   javaFile = false),
        @Template(phase = GenerationPhase.SCHEDULE, templateName = "schedule.ftl",            outputFileExtension = "java", javaFile = true)
    }
)
public class AzureTerraformJava21TemplateRegistration { ... }
```

> `@Template` uses `templateName` (the `.ftl` file), `phase` (one of
> `FUNCTION`, `API`, `SCHEDULE`, `FINALIZE`, `SHARED`), `outputFileExtension`,
> optional `outputSubDirectory`, and `javaFile`.

---

## Output

| Artifact                       | Location                                |
|--------------------------------|-----------------------------------------|
| Java handler classes           | `target/generated-sources/azure-terraform/` |
| Terraform infrastructure       | `target/deployment/azure-terraform/shared.tf` |
| Deployment metadata            | `target/classes/META-INF/cloudhopper/`  |
| Deployment ZIP (optional)      | `target/${artifactId}-${version}-azure.zip` |

---

## Related Modules

| Module              | Description                                                 |
|---------------------|-------------------------------------------------------------|
| `provider-azure`    | Runtime adapter used in generated Java classes              |
| `deployment-config-api` | Annotations and interfaces for vendor-neutral functions |

---

📝 *This module is required if you're targeting Azure Functions with Cloudhopper. It provides the logic and templates to generate handlers, infrastructure, and deployable artifacts.*
