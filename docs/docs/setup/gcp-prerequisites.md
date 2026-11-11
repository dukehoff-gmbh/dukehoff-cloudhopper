# Google Cloud Setup Guide

This guide prepares your environment for deploying Cloudhopper functions to
**Google Cloud Functions** with the `gcp-terraform` generator.

---

## Google Cloud project

You need a Google Cloud project with billing enabled. Create one in the
[Google Cloud console](https://console.cloud.google.com/) if necessary and
note its project ID.

---

## Install the Google Cloud CLI

Follow the [installation guide](https://cloud.google.com/sdk/docs/install)
for your operating system, then sign in:

```bash
gcloud auth login
gcloud config set project <project-id>
```

---

## Credentials for Terraform

Terraform's `google` provider can use a service account key file:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/keys/cloudhopper-deployer.json"
export GOOGLE_CLOUD_PROJECT="<project-id>"
```

Alternatively, for local experiments, use your own account through
Application Default Credentials:

```bash
gcloud auth application-default login
```

The account needs permission to create Cloud Functions, storage buckets,
API Gateway resources and, for scheduled functions, Cloud Scheduler jobs; for
event-triggered functions also Pub/Sub topics and Eventarc triggers.
Terraform reports any API that is not yet enabled in the project, together
with the link to enable it.

### Storage triggers

Functions with `@EventTrigger(source = EventSource.STORAGE)` receive events
directly from Cloud Storage through Eventarc. This needs two one-time grants in
the project:

```bash
PROJECT_ID=<project-id>
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')

# Cloud Storage publishes its events through Pub/Sub; the first command creates
# the Cloud Storage service agent if the project does not have it yet
gcloud storage service-agent --project=$PROJECT_ID
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:service-$PROJECT_NUMBER@gs-project-accounts.iam.gserviceaccount.com" \
  --role="roles/pubsub.publisher"

# The trigger's service account (cloudscheduler_service_account_email, see below) receives the events
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:<cloudscheduler_service_account_email>" \
  --role="roles/eventarc.eventReceiver"
```

---

## Terraform variables

The generated `main.tf` in `src/main/resources/deployment/gcp-terraform/`
declares these variables:

| Variable | Example |
|---|---|
| `gcp_project_id` | `my-project-123` |
| `gcp_region` | `europe-west3` |
| `gcp_zone` | `europe-west3-a` |
| `project_name` | `my-cloudhopper-project` |
| `environment` | `dev` |
| `cloudscheduler_service_account_email` | `scheduler@my-project-123.iam.gserviceaccount.com` |

The last one is the service account Cloud Scheduler uses to call scheduled
functions, and Eventarc to deliver queue messages and storage events; it is only used if your
project contains a
[`@ScheduledTrigger`](../guides/writing-functions#scheduledtrigger) or an
[`@EventTrigger`](../guides/writing-functions#eventtrigger).

Set the variables in the generated `gcp.auto.tfvars`, which Terraform loads
automatically, or as environment variables (`TF_VAR_gcp_project_id`, …).

---

## Next

Continue with the [Quickstart](../guides/quickstart), using the
`gcp-terraform` profile.
