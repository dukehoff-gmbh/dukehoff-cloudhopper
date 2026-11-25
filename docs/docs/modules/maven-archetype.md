# Cloudhopper Maven Archetype

The easiest way to get started is the Maven Archetype. It generates a minimal 
Cloudhopper-based function project that can be deployed to various cloud providers
 using our provider-specific generators.

## Features

The Archetype creates a simple "Hello World!" function using cloudhopper annotations.
Using commandline parameters you can configure where you want to deploy the function, or more accurately, what generator you want to use.
If you want to use Maven to deploy your functions, the archetype can optionally add a Terraform-based deployment profile.

## Usage

### Interactive (with prompts)

```sh
    mvn archetype:generate \
      -DarchetypeGroupId=eu.cloudhopper.mc \
      -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
      -DarchetypeVersion=1.6.1
```

### Non-interactive (fully scripted)

```sh
    mvn archetype:generate \
      -DarchetypeGroupId=eu.cloudhopper.mc \
      -DarchetypeArtifactId=cloudhopper-maven-archetype-core \
      -DarchetypeVersion=1.6.1 \
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

## Supported Generators

You can specify one or more `generatorIds` (comma-separated) to activate support for the following providers:

| Generator ID       | Description                         |
|--------------------|-------------------------------------|
| `aws-terraform`     | AWS Lambda via Terraform             |
| `gcp-terraform`     | GCP Cloud Functions via Terraform    |
| `azure-terraform`   | Azure Functions via Terraform        |
| `springboot-http`   | Local Spring Boot HTTP runtime       |
| `knative`           | Knative service on Kubernetes (Helm chart) |
| `scaleway-terraform` | Scaleway Serverless Containers via Terraform |

---

## Terraform Support {#terraform-support}

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

## Project Layout

The generated project looks like this:

```
my-function
├── AGENTS.md                                   // instructions for coding agents
├── .agents/skills/cloudhopper/SKILL.md         // Agent Skill (Codex, Gemini CLI, Cursor, Copilot)
├── .claude/skills/cloudhopper/SKILL.md         // the same skill for Claude Code
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

### Coding agents

The project contains instructions for coding agents, so that an agent works with the
annotations instead of writing handlers and Terraform by hand:

- `AGENTS.md`, read by agents that support it (e.g. Codex, Cursor, GitHub Copilot,
  Gemini CLI; Claude Code when the project has no `CLAUDE.md`): the rules of a
  Cloudhopper project and the build commands of its profiles.
- The [Agent Skill](https://agentskills.io) `cloudhopper`, in `.agents/skills/` and
  `.claude/skills/`, with short recipes for functions, triggers, messages,
  configuration, secrets, identities and deployment. Its description names the
  annotations and profiles, so that agents load it when they come up; whether to
  load it is up to the agent.

Keep, change or delete them like any other file of the project.

#### Existing projects

The skill is also on this site, at
[`/skills/cloudhopper/SKILL.md`](pathname:///skills/cloudhopper/SKILL.md), and in the
[`skills/`](https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/tree/main/skills) folder of the
repository. To add it to a project that was not created with the archetype:

```bash
for dir in .agents/skills/cloudhopper .claude/skills/cloudhopper; do
  mkdir -p $dir && curl -fsSL https://cloudhopper.eu/skills/cloudhopper/SKILL.md -o $dir/SKILL.md
done
```

Tools that install skills from a GitHub repository can use `dukehoff-gmbh/dukehoff-cloudhopper`.
For `AGENTS.md`, copy the rules from the skill's "Rules" section and add the build commands of
your profiles.

[`/llms.txt`](pathname:///llms.txt) lists the pages of this documentation for agents that
read the [llms.txt](https://llmstxt.org) format.

## Build the project

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

The annotation processor generates integration classes and writes the deployment files to
`target/deployment/{generatorId}`. For the Terraform targets the profile also builds the artifact the
generated Terraform uploads, and copies the files from `src/main/resources/deployment/{generatorId}`
(provider, backend, variables) next to the generated ones:

| Profile | Artifact |
|---|---|
| `aws-terraform` | `target/<artifactId>-<version>-aws-terraform.jar` (with all dependencies) |
| `gcp-terraform` | `target/<artifactId>-<version>-gcp-terraform.zip` (the jar with all dependencies) |
| `azure-terraform` | `target/<artifactId>-<version>-azure-terraform.zip` (the function app) |

You can import the generated files along with the artifact into your own Terraform project, or run
Terraform directly in `target/deployment/{generatorId}`:

```bash
cd target/deployment/aws-terraform
terraform init
terraform apply
```


You need to provide additional variables for the deployment. Please check the generator documentation for details. 

For `springboot-http` the project contains the entry point `src/main/springboot-http/.../Application.java`,
and the profile builds an executable jar. Start the service and call the example function:

```bash
mvn clean package -Pspringboot-http
java -jar target/<artifactId>-<version>-springboot-http.jar
curl localhost:8080/hello/world
```

The generated `Dockerfile` in `target/deployment/springboot-http` packages the same jar.

For `knative` the project uses the same entry point and builds `<artifactId>-<version>-knative.jar`.
`target/deployment/knative` contains the Dockerfile and a Helm chart; see the
[Knative setup guide](../setup/knative-prerequisites.md).

For `scaleway-terraform` the project also uses this entry point and builds
`<artifactId>-<version>-scaleway-terraform.jar`. `target/deployment/scaleway-terraform`
contains the Dockerfile and the Terraform; see the
[Scaleway setup guide](../setup/scaleway-prerequisites.md).

## Deploy with Terraform

If the generator is terraform based, the .tf files are generated in target/deployment/`{generatorId}`.
You can copy the generated files to your terraform modules and use them from there.
For smaller projects or for testing you can use the generated maven profile [see here](#terraform-support). Make sure to provide
the path to your terraform executable in the pom's properties:

```xml
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <cloudhopper.version>1.6.1</cloudhopper.version>
        <!-- add the path to your terraform executable here -->
        <terraform.executable>terraform</terraform.executable>
        <path.extras></path.extras>
    </properties>
```

The maven profile is configured to always run terraform init. Activate it together with the target profile; a second command can be passed via a parameter:

  ```bash
  mvn verify -Paws-terraform,deploy-with-terraform -Dterraform.command=plan
  mvn verify -Paws-terraform,deploy-with-terraform -Dterraform.command=apply
  mvn verify -Paws-terraform,deploy-with-terraform -Dterraform.command=destroy
  ```
