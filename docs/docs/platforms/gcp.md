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

The functions run as the project's default compute service account, which needs
`roles/pubsub.publisher` on the topics (the role Editor includes it). A service
account per [identity](aws.md#identities-and-permissions) is planned for GCP as well.

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-gcp-terraform` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-gcp` | compile | `GcpCloudFunctionRequestHandler`, the base class of the generated `HttpFunction`s (HTTP calls, Pub/Sub and Cloud Storage events), and `GcpMetadata` for the project and access tokens from the metadata server |
| `eu.cloudhopper.mc:provider-gcp-publisher` | compile | `@Queue` publishers for Pub/Sub, without the Google client libraries |
