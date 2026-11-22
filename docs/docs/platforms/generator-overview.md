# Targets and What They Support

import SupportMatrix from './_support-matrix.md';

Cloudhopper deploys the same annotated functions to six targets. Each target is
a **generator** (it writes handler classes, Terraform and API descriptions at
compile time) plus a **provider** (the runtime library the generated handlers
call). You pick the target with a Maven profile:

| Profile / generator ID | Target | Generator module | Provider module |
|---|---|---|---|
| `aws-terraform` | [AWS Lambda](aws.md) | `generator-aws-terraform` | `provider-aws` |
| `azure-terraform` | [Azure Functions](azure.md) | `generator-azure-terraform` | `provider-azure` |
| `gcp-terraform` | [Google Cloud Functions (2nd gen)](gcp.md) | `generator-gcp-terraform` | `provider-gcp` |
| `springboot-http` | [Spring Boot service](springboot.md) on any server | `generator-springboot` | `provider-springboot`, `provider-http` |
| `knative` | [Knative service](knative.md) on Kubernetes (Helm chart) | `generator-knative` | `provider-springboot`, `provider-http` |
| `scaleway-terraform` | [Scaleway Serverless Containers](scaleway.md) | `generator-scaleway-terraform` | `provider-springboot`, `provider-http` |

All modules are listed in [Modules](../modules/overview.md).

---

## Support matrix

Every generator declares which annotations and attributes it supports. The
same declarations drive the build and the
compatibility tests (TCK): when you
compile for a target, the build warns about every annotation or attribute it
does not claim (see
[When a target does not support something](../guides/writing-functions.md#when-a-target-does-not-support-something)),
and the TCK certifies each target against the tests whose features it claims.
The table below is generated from these declarations on every build of this
site.

<SupportMatrix />

Notes on the event and trigger columns:

- `@EventTrigger` lists the event sources a target supports as rows below
  `source`; today every target supports `QUEUE` and `STORAGE`. On Spring Boot it is the endpoint
  `POST /events/<function name>`, and queues can also be consumed from Kafka
  (see [Queues from Kafka](springboot.md#queues-from-kafka)). On Knative a queue is a Kafka topic; storage events have no
  generated source and are posted to `POST /events/<function name>` like on
  Spring Boot. On Scaleway a queue is a Scaleway Queue (SQS); storage events
  have no trigger there either.
- A function without a trigger can be invoked directly on AWS (Lambda
  invoke). On Azure every function also gets the HTTP route
  `/api/<function name>_http_function_trigger`, and on GCP every function has an
  HTTPS URL. On Spring Boot, Knative and Scaleway every function gets the endpoint
  `GET`/`POST /functions/<function name>`; set
  `cloudhopper.functions.direct-endpoint=false` to switch these off.
- Vendor-specific settings, such as the processor architecture on AWS, go
  through [`@Extension`](../guides/writing-functions.md#vendor-extensions) and are
  only read by the target they are meant for.

---

## What each target generates

Every target has its own page with the generated files, the Terraform variables or
Helm values, how schedules and queues are delivered, how messages are sent, and the
runtime modules it needs:

- [AWS Lambda](aws.md)
- [Azure Functions](azure.md)
- [Google Cloud Functions](gcp.md)
- [Spring Boot](springboot.md)
- [Knative](knative.md)
- [Scaleway Serverless Containers](scaleway.md)

---

## Configuration

Functions read [configuration](../guides/writing-functions.md#configuration)
from environment variables; each target passes them its own way.

| Target | How to set values |
|---|---|
| `aws-terraform`, `gcp-terraform` | Terraform variable `cloudhopper_config`; each function gets the values of the properties it reads |
| `azure-terraform`, `scaleway-terraform` | Terraform variable `cloudhopper_config`; all functions run in one app or container and get all values |
| `knative` | Helm value `env` (list of `name`/`value`) |
| `springboot-http` | environment variables or system properties (`-Dreport.bucket=…`) of the process |

`cloudhopper_config` is a map from property name to value, for example in a
`.tfvars` file:

```hcl
cloudhopper_config = {
  "report.bucket" = "acme-reports-prod"
  "report.days"   = "30"
}
```

On AWS and GCP, `terraform plan` fails if a function reads a property without
`defaultValue` that the map does not contain, and names the missing properties.
Values are plain environment variables of the function: keep secrets out of
them.

On Spring Boot, `@ConfigProperty` reads MicroProfile Config, not Spring's
`application.properties`; set the values as environment variables or system
properties.

## Terraform and provider versions

The generated Terraform requires Terraform `>= 1.5.0, < 2.0.0` (`required_version`)
and is tested with Terraform 1.17.

Terraform allows only one `required_providers` block per directory. The provider
versions therefore belong in your own `main.tf`, next to the provider configuration.
The exception is Scaleway, where the generated files declare the provider. Projects
created with the [archetype](../modules/maven-archetype.md) already contain these
constraints:

| Target | Provider constraints in `main.tf` |
|---|---|
| `aws-terraform` | `hashicorp/aws ~> 6.0` |
| `azure-terraform` | `hashicorp/azurerm ~> 4.0` |
| `gcp-terraform` | `hashicorp/google ~> 8.0`, `hashicorp/google-beta ~> 8.0`, `hashicorp/local ~> 2.0` |
| `scaleway-terraform` | none: the generated files declare `scaleway/scaleway ~> 2.92`. Do not add a `required_providers` block to your `main.tf` |

Without a constraint, `terraform init` installs the latest major version of the
provider, which the generated files may not support. Older projects should add the
block, for example on AWS:

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
```

---

## Maven configuration

Projects created with the [archetype](../modules/maven-archetype.md) have a
profile per target, so building for a target is
`mvn clean install -P<generator ID>`. Each profile adds the generator (in
`provided` scope: only the annotation processor uses it) and the provider (the
runtime of the generated handlers), and passes the generator ID to the annotation
processor:

```xml
<compilerArgs>
    <arg>-AgeneratorId=${generator.id}</arg>
    <arg>-AconfigOutputDir=${project.build.directory}/deployment/${generator.id}</arg>
    <arg>-AartifactId=${project.artifactId}</arg>
    <arg>-Aversion=${project.version}</arg>
    <arg>-Aclassifier=${jar.classifier}</arg>
    <arg>-AtargetDir=${project.build.directory}</arg>
</compilerArgs>
```

---

## See also

- [Customizing targets](../guides/customizing-targets.md): override templates or add a target of your own
- [Modules](../modules/overview.md): all published modules and what they contain
