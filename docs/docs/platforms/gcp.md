---
title: Google Cloud Functions
sidebar_label: Google Cloud Functions
---

# Google Cloud Functions

The profile `gcp-terraform` deploys every function as a Cloud Function (2nd gen),
with Terraform for the infrastructure: API Gateway for HTTP triggers, Cloud
Scheduler for schedules, Pub/Sub and Cloud Storage with Eventarc for events. Set up
the project and the tools with the
[Google Cloud setup guide](../setup/gcp-prerequisites.md).

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `handler.ftl` | Java | `HttpFunction` per function; also receives Pub/Sub and Cloud Storage events |
| `apiIntegrationClass.ftl` | Java | Subclass that knows the route pattern of an `@HttpTrigger`, for path parameters |
| `shared.ftl` | Terraform | Source bucket and archive |
| `function.ftl` | Terraform | Cloud Function and invoker permission |
| `apiIntegration.ftl` | JSON | OpenAPI operation per HTTP function |
| `apiRegistration.ftl` | Terraform | Separate Cloud Function `<name>_api` behind the API Gateway |
| `api.ftl` | Terraform | API Gateway API, config and gateway built from the operations |
| `schedule.ftl` | Terraform | Cloud Scheduler job |
| `queue.ftl` | Terraform | Pub/Sub topic and Eventarc trigger |
| `storage.ftl` | Terraform | Cloud Storage bucket and Eventarc trigger |

## Logs

Function logs go to Cloud Logging. The project's `_Default` log bucket keeps them for
30 days unless its retention was changed; the generated Terraform does not manage it.

## Sending messages

A function with a [`@Queue`](../guides/writing-functions.md#sending-messages)
parameter needs the dependency `eu.cloudhopper.mc:provider-gcp-publisher`; the
archetype adds it. It publishes to the Pub/Sub topic of the queue's name in the
function's project through the Pub/Sub REST API, with a token of the function's
service account from the metadata server, without the Google client libraries.
The configuration property `cloudhopper.queue.<name>.url`
(`projects/<project>/topics/<topic>`) names another topic.

Give a function that sends at least 256 MB (`@Function(memory = 256)`): with the
default of 128 MB, a Java function that sends over HTTP exceeds the limit on GCP and
is terminated.

The identity of a sending function gets `roles/pubsub.publisher` on exactly the
topics it sends to (`publishers.tf`).

## Buckets

A function with a [`@Bucket`](../guides/writing-functions.md#reading-and-writing-buckets)
parameter needs the dependency `eu.cloudhopper.mc:provider-gcp-blobstore`; the
archetype adds it. It calls the Cloud Storage JSON API with a token of the
function's service account from the metadata server, without the Google client
libraries. The generated Terraform (`buckets.tf`)

- creates the bucket `<name>` in `gcp_region`, unless a storage trigger of the
  project already creates it, the parameter is `existing`, or the local
  `cloudhopper_bucket_names` names a bucket of your own;
- passes the bucket's name as the environment variable
  `CLOUDHOPPER_BUCKET_<NAME>_NAME` and outputs it as `bucket_<name>`;
- grants the function's identity `roles/storage.objectViewer` on the bucket for
  `READ`, and `roles/storage.objectUser` for `WRITE` and `READ_WRITE`. Cloud
  Storage has no predefined role that writes without reading; the runtime refuses
  the reads that a `WRITE` parameter did not declare.

As for sending messages, give such a function at least 256 MB.

## Tables

A function with a [`@Table`](../guides/writing-functions.md#reading-and-writing-tables)
parameter needs the dependency `eu.cloudhopper.mc:provider-gcp-keyvalue`; the
archetype adds it. Each table is a Firestore database in Native mode. The runtime
calls the Firestore REST API with a token of the function's service account,
without the Google client libraries; an item is the document
`partitions/k<partition>/items/k<sort key>`, its attributes are the document's
fields. The generated Terraform (`tables.tf`)

- creates the database `<project_name>-<name>-<random suffix>` in `gcp_region`,
  unless the parameter is `existing` (then the database ID is the name) or the
  local `cloudhopper_table_names` names a database of your own. Firestore keeps
  the ID of a deleted database for a few minutes, hence the suffix;
- passes the database ID as the environment variable
  `CLOUDHOPPER_TABLE_<NAME>_NAME` and outputs it as `table_<name>`;
- grants the function's identity `roles/datastore.viewer` for `READ`, and
  `roles/datastore.user` for `WRITE` and `READ_WRITE`, with an IAM condition on
  exactly this database. Firestore has no predefined role that writes without
  reading; the runtime refuses the reads that a `WRITE` parameter did not
  declare.

Destroying the deployment deletes the databases it created, with their items.
The project needs the Firestore API (`firestore.googleapis.com`) and, for the
conditional roles, the Cloud Resource Manager API
(`cloudresourcemanager.googleapis.com`). Firestore's free
quota applies to only one database per project, and a project has at most 100
databases unless Google raises the limit.

## Secrets

The Terraform variable `cloudhopper_secrets` maps a property name to a secret in
Secret Manager, by name in the project or as `projects/<project>/secrets/<name>`:

```hcl
cloudhopper_secrets = {
  "report.api-key" = "report-key"
}
```

- Cloud Functions sets the latest version of each secret a function reads as the
  environment variable of the property (`report.api-key` → `REPORT_API_KEY`) when an
  instance starts; no runtime code is involved. A new version reaches new instances.
- The service account of each [identity](#identities-and-permissions) gets
  `roles/secretmanager.secretAccessor` on exactly the secrets its functions read
  (`secrets.tf`). Cloud Functions checks this access when it deploys a function, so
  a missing permission fails the deployment instead of the call.
- `terraform plan` fails if a property is set in both `cloudhopper_config` and
  `cloudhopper_secrets`. A property without `defaultValue` must be in one of the
  two maps.
- Create the secrets yourself, with the Secret Manager API enabled in the project;
  the generated Terraform only references them. Deploying needs permission to grant
  roles on them, e.g. `roles/secretmanager.admin`.

## Identities and permissions

Every [identity](../guides/writing-functions.md#identities-and-permissions) becomes a
service account, `google_service_account.identity_<identity>`, and its functions run
as it instead of the project's default compute service account, which usually has
the role Editor. Service account IDs have 6 to 30 characters, so the ID is
`project_name` and the identity, shortened, with a hash of both; the display name is
`<project_name> <identity>`.

The generated Terraform grants a service account only what its functions' own
resources need, such as `roles/pubsub.publisher` on the topics they send to, the
storage roles on their buckets and the Firestore roles on their tables. Triggers
(Cloud Scheduler, Eventarc) still run as `cloudscheduler_service_account_email`.
Grant further permissions in your own `.tf` file, to the member of the identity's
service account:

```hcl
resource "google_storage_bucket_iam_member" "reports_reader" {
  bucket = "my-reports"
  role   = "roles/storage.objectViewer"
  member = google_service_account.identity_reporting.member
}
```

The output `identity_<identity>_email` gives the account's email address.

Deploying needs permission to create service accounts (`roles/iam.serviceAccountAdmin`),
to deploy functions that run as them (`roles/iam.serviceAccountUser`), and to grant
roles on topics (`roles/pubsub.admin`). With `@Table`, it also needs to create
Firestore databases (`roles/datastore.owner`) and to grant the conditional roles
on them, which are bindings of the project (`roles/resourcemanager.projectIamAdmin`).

:::note Upgrading from 1.4 or earlier
The functions used to run as the default compute service account. Permissions they got
through it, e.g. Editor, are gone: grant what a function needs to the service account of
its identity.
:::

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-gcp-terraform` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-gcp` | compile | `GcpCloudFunctionRequestHandler`, the base class of the generated `HttpFunction`s (HTTP calls, Pub/Sub and Cloud Storage events), and `GcpMetadata` for the project and access tokens from the metadata server |
| `eu.cloudhopper.mc:provider-gcp-publisher` | compile | `@Queue` publishers for Pub/Sub, without the Google client libraries |
| `eu.cloudhopper.mc:provider-gcp-blobstore` | compile | `@Bucket` stores for Cloud Storage, without the Google client libraries |
| `eu.cloudhopper.mc:provider-gcp-keyvalue` | compile | `@Table` stores for Firestore, without the Google client libraries |
