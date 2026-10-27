# Cloudhopper Maven Archetype

This archetype generates a minimal Cloudhopper-based function project that can be deployed to various cloud providers using provider-specific generators.

## 🚀 Features

- Generates a complete Java function project based on the Cloudhopper framework
- Supports multiple cloud providers and deployment strategies via profiles
- Optional Terraform-based deployment profile
- Post-generation cleanup of unused provider resources
- Includes testable, minimal setup for CI/CD and cloud deployment

## 🛠 Usage

### 📦 Interactive (with prompts)

```sh
    mvn archetype:generate \
      -DarchetypeGroupId=eu.cloudhopper.mc \
      -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
      -DarchetypeVersion=1.0-SNAPSHOT
```

### ⚙️ Non-interactive (fully scripted)

```sh
    mvn archetype:generate \
      -DarchetypeGroupId=eu.cloudhopper.mc \
      -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
      -DarchetypeVersion=1.0-SNAPSHOT \
      -DgroupId=com.example \
      -DartifactId=my-function \
      -Dversion=1.0-SNAPSHOT \
      -Dpackage=com.example.fn \
      -DgeneratorIds=aws-terraform,gcp-terraform \
      -DincludeTerraformProfile=true \
      -B

> `-B` enables batch mode (non-interactive)
```
---

## 🧩 Supported Generators

You can specify one or more `generatorIds` (comma-separated) to activate support for the following providers:

| Generator ID       | Description                         |
|--------------------|-------------------------------------|
| `aws-terraform`     | AWS Lambda via Terraform             |
| `gcp-terraform`     | GCP Cloud Functions via Terraform    |
| `azure-terraform`   | Azure Functions via Terraform        |
| `springboot-http`   | Local Spring Boot HTTP runtime       |

---

## 🧪 Terraform Support

If `includeTerraformProfile=true`, the generated project will include a `deploy-with-terraform` Maven profile that executes:

- `terraform init`
- `terraform plan`
- `terraform apply` / `destroy`

Customize the Terraform executable path and environment using project properties:
```xml
    <properties>
      <terraform.executable>terraform</terraform.executable>
      <path.extras>/usr/local/bin</path.extras>
    </properties>
```
---

## 📋 Project Layout

The generated project includes:

- Java function template in `src/main/java`
- Terraform templates in `src/main/resources/deployment`
- Provider-specific profiles (via `generatorIds`)
- Optional Spring Boot HTTP support
- Maven plugin configuration for annotation processing and packaging

