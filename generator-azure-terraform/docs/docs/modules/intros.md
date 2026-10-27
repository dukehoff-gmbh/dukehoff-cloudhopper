# ⚙️ Azure Terraform Generator

This module provides a Cloudhopper generator that enables the deployment of annotated Java functions as **Azure Functions** using **Terraform** and **Java handler code generation**.

Azure is unique in that Cloudhopper generates most of the glue code as Java classes. Only shared Terraform infrastructure (like the Function App and Storage Account) is generated as `.tf` files.

---

## 📢 Documentation Notice:

All Cloudhopper functions on Azure are exposed via HTTP under the /api/functionIdroute unless otherwise specified with an @HttpTrigger(path="...") annotation.
- Functions without an @HttpTrigger will default to their @Function(functionId) name as the HTTP route.
- Direct invocation via SDK (like AWS Lambda invoke()) is not supported on Azure — Azure Functions require trigger bindings.

Therefore, every Cloudhopper function in Azure is accessible via an HTTP call to its assigned route.

## 📦 How to Use

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
    <source>11</source>
    <target>11</target>
    <encoding>UTF-8</encoding>
    <annotationProcessors>
      <annotationProcessor>
        eu.cloudhopper.mc.deployment.config.generator.ServerlessFunctionProcessor
      </annotationProcessor>
    </annotationProcessors>
    <compilerArgs>
      <arg>-Acloudprovider=azure</arg>
      <arg>-AgeneratorId=azure-terraform</arg>
      <arg>-AconfigOutputDir=${project.build.directory}/deployment/azure</arg>
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

## 📁 Templates

Templates are located under:

```
src/main/resources/templates/azure/
```

| Template                 | Output Type | Purpose                                            |
|--------------------------|-------------|----------------------------------------------------|
| `apiIntegrationClass.ftl` | Java        | Entry class for API functions                      |
| `function.ftl`           | Java        | HTTP function handler                              |
| `schedule.ftl`           | Java        | Scheduled function handler                         |
| `shared.ftl`             | Terraform   | Base infrastructure setup (Function App, etc.)     |

---

## 🔌 Template Registration

```java
@TemplateRegistration(
  generatorId = "azure-terraform",
  templates = {
    @Template(name = "function", phase = GenerationPhase.SOURCES),
    @Template(name = "schedule", phase = GenerationPhase.SOURCES),
    @Template(name = "apiIntegrationClass", phase = GenerationPhase.SOURCES),
    @Template(name = "shared", phase = GenerationPhase.DEPLOYMENT)
  }
)
public class AzureTerraformJava21TemplateRegistration { ... }
```

---

## 📂 Output

| Artifact                       | Location                                |
|--------------------------------|-----------------------------------------|
| Java handler classes           | `target/generated-sources/azure/`       |
| Terraform infrastructure       | `target/deployment/azure/shared.tf`     |
| Deployment metadata            | `target/classes/META-INF/cloudhopper/`  |
| Deployment ZIP (optional)      | `target/${artifactId}-${version}-azure.zip` |

---

## 📚 Related Modules

| Module              | Description                                                 |
|---------------------|-------------------------------------------------------------|
| `provider-azure`    | Runtime adapter used in generated Java classes              |
| `deployment-config-api` | Annotations and interfaces for vendor-neutral functions |

---

📝 *This module is required if you're targeting Azure Functions with Cloudhopper. It provides the logic and templates to generate handlers, infrastructure, and deployable artifacts.*
