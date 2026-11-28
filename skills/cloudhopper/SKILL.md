---
name: cloudhopper
description: Write, change, build and deploy serverless Java functions with Cloudhopper. Use when code has or needs @Function, @HttpTrigger, @ScheduledTrigger, @EventTrigger, @Queue, @Bucket, @Table, @ConfigProperty or @Function(identity), when building with the Maven profiles -Paws-terraform, -Pazure-terraform, -Pgcp-terraform, -Pspringboot-http, -Pknative or -Pscaleway-terraform, or when a function is deployed to AWS Lambda, Azure Functions, Google Cloud Functions, Knative, Scaleway or Spring Boot.
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
4. **Zero cloud SDKs:** never import provider-specific SDK types in function classes
   (`com.amazonaws.*`, `software.amazon.awssdk.*`, `com.google.cloud.*`, `com.azure.*`,
   `com.microsoft.azure.*`, …), and do not rely on provider-specific resources, environment
   variables or event formats. Use `@Queue`, `@Bucket`, `@Table`, `@ConfigProperty`, `@EventTrigger`/`StorageEvent`
   and identities instead of SDKs and hand-written IAM.
5. If no Cloudhopper API covers a need, keep the function class portable: provider-specific
   code goes behind an interface of your own in a separate class, selected through
   configuration, with the supported targets documented.
6. Vendor-specific settings only through `@Function(extensions = @Extension(...))`; other
   targets ignore them.

## Functions and triggers

```java
import eu.cloudhopper.mc.annotations.*;          // Function, HttpTrigger, PathParam, ...
import eu.cloudhopper.mc.runtime.*;              // HttpResponse, HttpStatusException, Publisher, BlobStore, ...

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

- The return value is the response body (JSON, status 200); a `String` is returned as a JSON
  string. For another status or headers return `HttpResponse<T>`
  (`HttpResponse.status(201, item).header("Location", ...)`, `HttpResponse.noContent()`); for
  client errors throw `HttpStatusException` (`new HttpStatusException(404, "no such item")`,
  body `{"error": "..."}`). Any other exception answers 500 without details; a body that is not
  valid JSON answers 400. Never return error objects with status 200.
- Routes for signed-in users: `@HttpTrigger(..., authenticated = true, scopes = {"items:read"})`;
  read the caller with `@ClaimParam("sub") String userId` or a `Claims` parameter. The provider
  is configured with the properties `cloudhopper.auth.issuer` and `cloudhopper.auth.audience`
  (e.g. in `cloudhopper_config`). Never verify JWTs in function code.
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

## Reading and writing buckets

```java
@Function(name = "renderReport")
public void render(ReportRequest request,
                   @Bucket(name = "acme-reports", access = Bucket.Access.WRITE) BlobStore reports) {
    reports.put(request.id() + ".csv", csv, "text/csv");  // get(key) → Optional<byte[]>, delete, list(prefix)
}
```

`access` is `READ` (default), `WRITE` or `READ_WRITE`; anything else throws
`BlobAccessDeniedException`, and on AWS and GCP the identity gets exactly these permissions. The
generator creates the bucket once per name, or shares the bucket of a storage trigger with the same
name; a bucket owned by another system is `@Bucket(name = "...", existing = true)`. Scaleway needs
an object storage key in `cloudhopper_blobstore_credentials`; on Knative the buckets must exist and
the Helm values `blobstore.*` name the S3 endpoint and key.

Docs: https://cloudhopper.eu/docs/guides/writing-functions#reading-and-writing-buckets

## Reading and writing tables

```java
@Function(name = "registerAccount")
public String register(Account account,
                       @Table(name = "accounts", access = Table.Access.READ_WRITE) KeyValueStore accounts) {
    Key key = new Key("account:" + account.id(), "profile");          // partition key, sort key
    return accounts.putIfAbsent(new Item(key, Map.of("name", account.name()))) ? "created" : "exists";
}   // get, put, update(key, set, expected), delete, query(partition, sortKeyPrefix), batchPut/batchDelete
```

Items of one partition are queried by sort key prefix; there are no scans across partitions and no
secondary indexes, so keep an index partition for lists. Keys have no `/ \ # ?` (use `:`), table
names only lowercase letters and digits; values are String, Long, Double, Boolean, List, Map. Reads
are strongly consistent; `access` works as for buckets (`KeyValueAccessDeniedException`).

Docs: https://cloudhopper.eu/docs/guides/writing-functions#reading-and-writing-tables

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
- Values and secrets that the project's own Terraform creates or reads (resource attributes,
  `data` sources) go into the locals `cloudhopper_extra_config` / `cloudhopper_extra_secrets`,
  redefined in an override file `src/main/resources/deployment/<target>/cloudhopper_override.tf`;
  a variable cannot refer to them.
- On AWS and GCP, `terraform plan` fails when a property without `defaultValue` is set nowhere.

Docs: https://cloudhopper.eu/docs/guides/writing-functions#configuration

## Identities and permissions

Every function runs as an identity of its own (an IAM role on AWS, a service account on GCP).
Functions that need the same permissions share one: `@Function(name = "...", identity = "connector")`.
The generator grants only what the function's own triggers and queues need. Further permissions
go into your own `.tf` file in `src/main/resources/deployment/<target>/`, attached to
`aws_iam_role.identity_<identity>` (AWS) or `google_service_account.identity_<identity>.member` (GCP).
On Azure, Knative and Scaleway each identity gets its own function app, service or container
(functions without an identity share the default one).

Docs: https://cloudhopper.eu/docs/guides/writing-functions#identities-and-permissions

## Build and deploy

| Profile | Target | Deploy |
|---|---|---|
| `-Pspringboot-http` | Spring Boot application | `java -jar target/<artifactId>-<version>-springboot-http.jar`; no cloud needed |
| `-P<springboot-http\|knative\|scaleway-terraform>,springboot-native` | the same, as a native image | the generated Dockerfile compiles it (minutes, needs Docker) |
| `-Paws-terraform` | AWS Lambda | Terraform |
| `-Paws-terraform,aws-native` | AWS Lambda, native image (`provided.al2023`, arm64) | Terraform; the build needs Docker |
| `-Pazure-terraform` | Azure Functions | Terraform |
| `-Pgcp-terraform` | Google Cloud Functions | Terraform |
| `-Pscaleway-terraform` | Scaleway Serverless Containers | build and push the image, then Terraform |
| `-Pknative` | Knative on Kubernetes | Helm chart in `target/deployment/knative/chart` |

```bash
mvn clean package -Paws-terraform
cd target/deployment/aws-terraform && terraform init && terraform apply
```

With `aws-native` or `springboot-native`, the functions' body, result and message types are registered
for reflection automatically; mark other types the code reads or writes as JSON with `@Reflective`.
Other reflection, proxies or resources need GraalVM reachability metadata.

Only the profiles the project was created with exist in `pom.xml`. Which annotations and
attributes each target supports: https://cloudhopper.eu/docs/platforms/generator-overview#support-matrix

## Changing what is generated

1. Add resources, permissions and outputs in your own `.tf` files next to `main.tf`; they can
   refer to the generated resources.
2. To change a template for every function, copy it from the generator jar
   (`templates/<target>/<template>.ftl`) to `src/main/resources/cloudhopper-templates/<target>/`.

Docs: https://cloudhopper.eu/docs/guides/customizing-targets
