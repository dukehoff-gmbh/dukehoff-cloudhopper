# Cloudhopper Maven Archetype

The easiest way to get started is the Maven Archetype. It generates a minimal 
Cloudhopper-based function project that can be deployed to various cloud providers
 using our provider-specific generators.

## 🚀 Features

The Archetype creates a simple "Hello World!" function using cloudhopper annotations.
Using commandline parameters you can configure where you want to deploy the function, or more accurately, what generator you want to use.
If you want to use Maven to deploy your functions, the archetype can optionally add a Terraform-based deployment profile.

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

## 🧪 Terraform Support {#terraform-support}

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

The generated project looks like this:

```
my-function
├── pom.xml
├── README.md
└── src
    └── main
        ├── java
        │   └── com
        │       └── example
        │           └── fn
        │               └── HelloFunction.java  // The sample function
        └── resources
            └── deployment                      // files will be copied to target/deployment/...
                ├── aws-terraform               // optional, only for generator aws-terraform
                │   └── main.tf                 // customize to provide credentials, etc. 
                ├── azure-terraform             // optional, only for generator azure-terraform
                │   └── main.tf                 // customize to provide credentials, etc. 
                └── gcp-terraform               // optional, only for generator gcp-terraform)
                    ├── gcp.auto.tfvars         // customize to provide service account, etc.
                    └── main.tf                 // customize to provide credentials, etc. 
```

## 🏗️ Build the project

For each of the generatorIds specified, a profile is created in the pom that can be used to build the project and generate
for the specified platform and generate the boilerplate needed for deployment.

```bash
# build with generator aws-terraform
mvn clean install -Paws-terraform
# build with generator gcp-terraform
mvn clean install -Pgcp-terraform
# build with generator azure-terraform
mvn clean install -Pazure-terraform
# build with generator springboot-http
mvn clean install -Pspringboot-http
```

The annotation processor generates integration classes. The deployment files, (for aws, gcp and azure),
will be generated into a directory in target/deployment/`{generatorId}`. You can import those files along with 
the generated function JAR in your own terraform project, or use terraform directly from the generated directory.


You need to provide additional variables for the deployment. Please check the generator documentation for details. 

## Deploy with Terraform

If the generator is terraform based, the .tf files are generated in target/deployment/`{generatorId}`.
You can copy the generated files to your terraform modules and use them from there.
For smaller projects or for testing you can use the generated maven profile [see here](#terraform-support). Make sure to provide
the path to your terraform executable in the pom's properties:

```xml
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <cloudhopper.version>1.0-SNAPSHOT</cloudhopper.version>
        <!-- add the path to your terraform executable here -->
        <terraform.executable>terraform</terraform.executable>
        <path.extras></path.extras>
    </properties>
```

The maven profile is configured to always run terraform init. A second command can be passed via a parameter:

  ```bash
  mvn verify -Pdeploy-with-terraform -Dterraform.command=plan
  mvn verify -Pdeploy-with-terraform -Dterraform.command=apply
  mvn verify -Pdeploy-with-terraform -Dterraform.command=delete
  ```
