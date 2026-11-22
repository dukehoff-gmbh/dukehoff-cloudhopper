---
name: cloudhopper
description: Write, change, build and deploy serverless Java functions with Cloudhopper. Use when code has or needs @Function, @HttpTrigger, @ScheduledTrigger, @EventTrigger, @Queue, @ConfigProperty or @Function(identity), when building with the Maven profiles -Paws-terraform, -Pazure-terraform, -Pgcp-terraform, -Pspringboot-http, -Pknative or -Pscaleway-terraform, or when a function is deployed to AWS Lambda, Azure Functions, Google Cloud Functions, Knative, Scaleway or Spring Boot.
---

# Cloudhopper

Cloudhopper generates the handler classes and the deployment configuration of serverless
functions from annotations, at compile time. The same code builds for several targets; a
Maven profile selects one. Full documentation: https://cloudhopper.eu/docs/intro

## Rules

1. A function is a plain method with `@Function` and a trigger. Never write handler classes,
   cloud entry points, controllers or Terraform for a function: the build generates them.
2. Never edit `target/`. Hand-written infrastructure goes into
   `src/main/resources/deployment/<target>/`; template overrides into
   `src/main/resources/cloudhopper-templates/<target>/`.
3. After a change, build with the target's profile. Read the warnings: a target that does not
   support an annotation or attribute ignores it.
4. Prefer `@Queue`, `@ConfigProperty` and identities over cloud SDKs and hand-written IAM.

## Functions and triggers

```java
import eu.cloudhopper.mc.annotations.*;          // Function, HttpTrigger, PathParam, ...
import eu.cloudhopper.mc.runtime.*;              // Publisher, EventMetadata, StorageEvent, HandlerContext

public class PlayerFunctions {

    @Function(name = "getPlayer")                 // name: unique per deployment
    @HttpTrigger(method = "GET", path = "/player/{id}")
    public Player get(@PathParam Integer id, @QueryParam("full") boolean full) { ... }

    @Function(name = "updatePlayer", memory = 512, timeout = 60)
    @HttpTrigger(method = "PUT", path = "/player/{id}")
    public Player update(@RequestBody Player player, @PathParam Integer id,
                         @HeaderParam("X-Token") String token) { ... }

    @Function(name = "nightlyCleanup")
    @ScheduledTrigger(cron = "0 0 * * *")              // UTC; timezone = "..." where supported
    public void cleanup() { ... }

    @Function(name = "orderReceived")
    @EventTrigger(source = EventSource.QUEUE, name = "orders")   // body: JSON → Order
    public void onOrder(Order order, EventMetadata meta) { ... }

    @Function(name = "invoiceUploaded")
    @EventTrigger(source = EventSource.STORAGE, name = "acme-invoices")
    public void onUpload(StorageEvent event) { ... }             // bucket, key, size
}
```

- The return value is the response body (JSON); a `String` is returned as a JSON string.
- An unannotated parameter is the request body; `HandlerContext` and `EventMetadata` are
  recognized by type.
- Events are delivered at least once: make event functions tolerate duplicates.
- `@EventTrigger(create = false)` connects to an existing queue or bucket instead of creating it.
- Queue and bucket names: 3 to 63 lowercase letters, digits and single hyphens. Bucket names on
  AWS and GCP are global: start them with a company prefix.

Docs: https://cloudhopper.eu/docs/guides/writing-functions

## Sending messages

```java
@Function(name = "startCollection")
@ScheduledTrigger(cron = "0 4 * * *")
public void start(@Queue(name = "collect") Publisher<CollectRequest> queue) {
    queue.sendAll(requests);                      // or queue.send(request)
}

@Function(name = "collectOne")
@EventTrigger(source = EventSource.QUEUE, name = "collect")
public void collect(CollectRequest request) { ... }
```

`@Queue(name)` must refer to a queue that an `@EventTrigger(source = QUEUE)` of the same project
creates; a queue owned by another system is `@Queue(name = "...", existing = true)`. Anything else
is a build error. The generator grants the sending function's identity exactly this queue.

Docs: https://cloudhopper.eu/docs/guides/writing-functions#sending-messages

## Configuration and secrets

```java
@Function(name = "report")
public void report(@ConfigProperty(name = "report.bucket") String bucket,
                   @ConfigProperty(name = "report.days", defaultValue = "7") int days,
                   @ConfigProperty(name = "report.api-key") String apiKey) { ... }
```

`org.eclipse.microprofile.config.inject.ConfigProperty`; in other code
`ConfigProvider.getConfig().getValue("report.bucket", String.class)`. The deployment sets the
values, the code does not change:

- Values: Terraform variable `cloudhopper_config = { "report.bucket" = "acme-reports" }`
  (Knative: Helm value `env`; Spring Boot: environment variable `REPORT_BUCKET`).
- Secrets: Terraform variable `cloudhopper_secrets`, a reference to the target's secret store
  (AWS Secrets Manager ARN or name, Key Vault secret ID, Secret Manager name; Knative: Helm
  value `secrets`; Scaleway: the value). Never put secrets into `cloudhopper_config` or code.
- On AWS and GCP, `terraform plan` fails when a property without `defaultValue` is set in
  neither map.

Docs: https://cloudhopper.eu/docs/guides/writing-functions#configuration

## Identities and permissions

Every function runs as an identity of its own (an IAM role on AWS, a service account on GCP).
Functions that need the same permissions share one: `@Function(name = "...", identity = "connector")`.
The generator grants only what the function's own triggers and queues need. Further permissions
go into your own `.tf` file in `src/main/resources/deployment/<target>/`, attached to
`aws_iam_role.identity_<identity>` (AWS) or `google_service_account.identity_<identity>.member` (GCP).

Docs: https://cloudhopper.eu/docs/guides/writing-functions#identities-and-permissions

## Build and deploy

| Profile | Target | Deploy |
|---|---|---|
| `-Pspringboot-http` | Spring Boot application | `java -jar target/<artifactId>-<version>-springboot-http.jar`; no cloud needed |
| `-Paws-terraform` | AWS Lambda | Terraform |
| `-Pazure-terraform` | Azure Functions | Terraform |
| `-Pgcp-terraform` | Google Cloud Functions | Terraform |
| `-Pscaleway-terraform` | Scaleway Serverless Containers | build and push the image, then Terraform |
| `-Pknative` | Knative on Kubernetes | Helm chart in `target/deployment/knative/chart` |

```bash
mvn clean package -Paws-terraform
cd target/deployment/aws-terraform && terraform init && terraform apply
```

Only the profiles the project was created with exist in `pom.xml`. Which annotations and
attributes each target supports: https://cloudhopper.eu/docs/platforms/generator-overview#support-matrix

## Changing what is generated

1. Add resources, permissions and outputs in your own `.tf` files next to `main.tf`; they can
   refer to the generated resources.
2. To change a template for every function, copy it from the generator jar
   (`templates/<target>/<template>.ftl`) to `src/main/resources/cloudhopper-templates/<target>/`.

Docs: https://cloudhopper.eu/docs/guides/customizing-targets
