# 🚀 Deploy Your First Cloud Function in 5 Minutes

Want to see Cloudhopper in action? This guide walks you through generating, deploying, and testing a serverless function on your favorite cloud — in just five minutes.

---

## 📋 Prerequisites

You’ll need:

- **Java 21+**
- **[Maven 3.8+](https://maven.apache.org/install.html)**
- **[Terraform 1.5+](https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli)**
- A cloud account and CLI set up for one of the following:
  - [AWS →](../setup/aws-prerequisites)
  - [GCP →](../setup/gcp-prerequisites) 
  - [Azure →](../setup/azure-prerequisites) 

---

## Step 1: Generate a Project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=eu.cloudhopper.mc \
  -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
  -DarchetypeVersion=1.0-SNAPSHOT \
  -DgroupId=com.example \
  -DartifactId=my-function \
  -Dversion=1.0-SNAPSHOT \
  -Dpackage=com.example.fn \
  -DgeneratorIds=aws-terraform,gcp-terraform,azure-terraform,springboot \
  -DincludeTerraformProfile=true \
  -B
```

> Change the list of generatorIds if targeting a different provider. 

---

## Step 2: Look at Your Function

The generated project already contains a ready-to-deploy function in
`src/main/java/.../HelloFunction.java`:

```java
public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}",
                 summary = "Say Hello", description = "Returns a friendly greeting")
    public String handleRequest(@PathParam String name) {
        return "hello " + name;
    }
}
```

That is the whole programming model: a plain method, a `@Function` name, and a
trigger. Cloudhopper generates the cloud-specific handler and infrastructure
from these annotations. To add your own functions — request bodies, query
parameters, scheduled triggers — see the
[Writing Functions guide](writing-functions).

---

## Step 3: Configure Terraform deployment (for deploy-with-terraform profile)

The generated project contains terraform files that allow you to set variables
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
  region = "us-east-1"
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
  region  = "us-central1"
}
```

For more advanced setups (IAM roles, VPC config), see [Terraform AWS Provider Docs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs).


## Step 4: Build and Generate Deployment Artifacts

```bash
cd my-function
mvn clean install -Paws-terraform
```

This compiles your function, processes annotations, and generates Terraform files in:

```
target/deployment/aws-terraform/
```


You can now copy the generated files along with the generated artefact and deploy the resources
as part of your existing pipeline. If you would like to use the deploy-with-terraform profile, 
proceed with the next step.

---

## Step 5: Deploy the Function with deploy-with-terraform profile

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
mvn verify -Pdeploy-with-terraform -Dterraform.command=apply
```

Terraform will create the function and related infrastructure.

---

## 🔍 Step 6: Test Your Function

Find the deployed URL in Terraform output or your cloud console, then test it:

```bash
curl https://your-function-url/hello/world
```

Expected response:
```json
"hello world"
```

---

## 🧭 What’s Next?

- [Write your own functions →](writing-functions)
- [Explore the Maven Archetype →](../modules/maven-archetype)
- [Annotation & runtime API reference →](../modules/deployment-config-api)
