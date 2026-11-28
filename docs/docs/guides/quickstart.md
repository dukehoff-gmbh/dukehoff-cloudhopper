# Quickstart

This guide creates a project with one function, runs it locally as a Spring Boot
application, and then deploys the same code to a cloud. The local run needs no cloud
account and no Terraform, and takes about two minutes.

---

## Prerequisites

You’ll need:

- **Java 21+**
- **[Maven 3.8+](https://maven.apache.org/install.html)**

For a cloud deployment (Step 4 and later), in addition:

- **[Terraform 1.5+](https://developer.hashicorp.com/terraform/install)**
- A cloud account and CLI set up for one of the targets:
  [AWS](../setup/aws-prerequisites.md), [GCP](../setup/gcp-prerequisites.md),
  [Azure](../setup/azure-prerequisites.md) or [Scaleway](../setup/scaleway-prerequisites.md),
  or a Kubernetes cluster with [Knative](../setup/knative-prerequisites.md)

---

## Step 1: Generate a Project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=eu.cloudhopper.mc \
  -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
  -DarchetypeVersion=1.10.0 \
  -DgroupId=com.example \
  -DartifactId=my-function \
  -Dversion=1.0-SNAPSHOT \
  -Dpackage=com.example.fn \
  -DgeneratorIds=aws-terraform,gcp-terraform,azure-terraform,springboot-http \
  -DincludeTerraformProfile=true \
  -B
```

> `generatorIds` lists the targets the project is prepared for, one Maven profile
> each: `aws-terraform`, `gcp-terraform`, `azure-terraform`, `springboot-http`,
> `knative` and `scaleway-terraform`. Keep `springboot-http` for the local run and add
> the clouds you want to use.

---

## Step 2: Look at Your Function

The generated project already contains a ready-to-deploy function in
`src/main/java/.../HelloFunction.java`:

```java
public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}")
    public String handleRequest(@PathParam String name) {
        return "hello " + name;
    }
}
```

That is the whole programming model: a plain method, a `@Function` name, and a
trigger. Cloudhopper generates the cloud-specific handler and infrastructure
from these annotations. To add your own functions — request bodies, query
parameters, scheduled triggers — see the
[Writing Functions guide](writing-functions.md).

---

## Step 3: Run It Locally

Build the project for the `springboot-http` target and start the application:

```bash
cd my-function
mvn clean install -Pspringboot-http
java -jar target/my-function-1.0-SNAPSHOT-springboot-http.jar
```

In a second terminal:

```bash
curl localhost:8080/hello/world
```

Expected response:

```json
"hello world"
```

The same method now runs as an HTTP endpoint of a Spring Boot application. Every
function also gets `POST /functions/<function name>` for a direct call; schedules run
in the application. Stop it with `Ctrl+C`. The [Spring Boot](../platforms/springboot.md)
page describes what is generated.

---

## Step 4: Configure the Terraform deployment

The cloud targets deploy with Terraform; Knative deploys with Helm, see the [Knative setup guide](../setup/knative-prerequisites.md).

The generated project contains Terraform files that allow you to set variables
required for deployment and add additional variables and resources. You will need
to configure these only if you're planning to use the terraform deployment profile.

If you want to copy the generated resources to your own deployment pipeline, you can skip this step.

Edit the provider configuration in the generated Terraform, for example:

```
src/main/resources/deployment/aws-terraform/main.tf
```

You need to provide your region and adjust authentication details or the Terraform backend configuration:

### AWS

```hcl
provider "aws" {
  region = "eu-central-1"
}
```

### Azure

```hcl
provider "azurerm" {
  features {}
}
```

### GCP

```hcl
provider "google" {
  project = "your-project-id"
  region  = "europe-west3"
}
```

For more advanced setups (IAM roles, VPC config), see [Terraform AWS Provider Docs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs).


## Step 5: Build and Generate Deployment Artifacts

```bash
mvn clean install -Paws-terraform
```

This compiles your function, processes annotations, builds the Lambda jar with all dependencies
(`target/my-function-1.0-SNAPSHOT-aws-terraform.jar`) and writes the Terraform to:

```
target/deployment/aws-terraform/
```

The folder contains the generated files and your `main.tf` from Step 4, so you can run
`terraform init` and `terraform apply` there.


You can now copy the generated files along with the generated artefact and deploy the resources
as part of your existing pipeline. If you would like to use the deploy-with-terraform profile, 
proceed with the next step.

---

## Step 6: Deploy the Function with the deploy-with-terraform profile

For the three cloud provider profiles you need to configure the terraform executable.

Make sure your `pom.xml` includes the path to Terraform:
```xml
<properties>
  <terraform.executable>terraform</terraform.executable>
  <path.extras></path.extras>
</properties>
```

Use the built-in Maven profile to apply the Terraform plan:

```bash
mvn verify -Paws-terraform,deploy-with-terraform -Dterraform.command=apply
```

Terraform will create the function and related infrastructure.

---

## Step 7: Test Your Function

Find the deployed URL in Terraform output or your cloud console, then test it:

```bash
curl https://your-function-url/hello/world
```

Expected response:
```json
"hello world"
```

---

## What’s Next?

- [Write your own functions](writing-functions.md): request bodies, schedules, queue and storage events, messages, configuration and secrets
- [Cloud Providers](../platforms/generator-overview.md): what each target generates and supports
- [Maven archetype](../modules/maven-archetype.md): the generated project and its profiles
