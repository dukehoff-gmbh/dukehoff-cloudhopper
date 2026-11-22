# Scaleway Setup Guide

This guide prepares a Scaleway project for Cloudhopper functions built with the
`scaleway-terraform` generator. The generator writes Terraform that runs your
functions as one Scaleway Serverless Container; see
[Scaleway (`scaleway-terraform`)](../platforms/scaleway.md)
for what it contains.

---

## What you need

- A Scaleway project, for example the default project of your organization.
- An API key with these permission sets on the project:

  | Permission set | Needed for |
  |---|---|
  | `ContainersFullAccess` | the container namespace, the container and its triggers |
  | `ContainerRegistryFullAccess` | pushing the image to the Scaleway registry |
  | `MessagingAndQueuingFullAccess` | `@EventTrigger(source = QUEUE)`: Scaleway Queues |

  The [Scaleway CLI](https://www.scaleway.com/en/docs/scaleway-cli/quickstart/)
  (`scw init`) also lists the projects of the organization; for that the key
  needs `ProjectReadOnly` on the organization.
- On your machine: `docker`, [Terraform](https://developer.hashicorp.com/terraform/install)
  1.5 or later, and optionally the Scaleway CLI.

Terraform reads the credentials from `~/.config/scw/config.yaml`, which `scw init`
writes, or from the environment variables `SCW_ACCESS_KEY`, `SCW_SECRET_KEY` and
`SCW_DEFAULT_PROJECT_ID`.

---

## Build and push the image

Serverless Containers run on `linux/amd64`. Build the image from the project
directory and push it to a registry namespace (create one in the console or with
`scw registry namespace create name=<name>`; the name is unique across Scaleway):

```bash
mvn clean package -Pscaleway-terraform
scw registry login
docker build --platform linux/amd64 -t rg.fr-par.scw.cloud/<namespace>/my-function:1.0.0 \
  -f target/deployment/scaleway-terraform/shared-resources_dockerfile..dockerfile .
docker push rg.fr-par.scw.cloud/<namespace>/my-function:1.0.0
```

---

## Deploy

`target/deployment/scaleway-terraform` contains the generated Terraform and a
`main.tf.disabled` with the provider configuration. Projects created with the
archetype copy `src/main/resources/deployment/scaleway-terraform/main.tf`
instead. Then:

```bash
cd target/deployment/scaleway-terraform
cp main.tf.disabled main.tf      # unless your project provides its own main.tf
terraform init
terraform apply -var image=rg.fr-par.scw.cloud/<namespace>/my-function:1.0.0
curl "$(terraform output -raw url)/hello/world"
```

The variables in `container.tf` set the container:

| Variable | Default | Meaning |
|---|---|---|
| `image` | (required) | The image to run |
| `container_name` | the artifact ID | Name of the container namespace and the container |
| `min_scale`, `max_scale` | `0`, `5` | Instances; `0` scales to zero when idle |
| `memory_limit_bytes`, `cpu_limit` | 1 GiB, 560 mVCPU | Size of an instance; Scaleway allows fixed pairs of memory and CPU |
| `timeout` | `300` | Maximum duration of a request in seconds |
| `privacy` | `public` | `private` requires a token for HTTP calls |
| `environment_variables`, `secret_environment_variables` | `{}` | Environment of the application |
| `cloudhopper_name_prefix` | `""` | Prefix for the queue names |
| `queue_max_receive_count` | `1` | Receives before a failed message moves to the dead-letter queue |
| `queue_visibility_timeout_seconds` | `30` | Visibility timeout of the queues |

The first queue function activates Scaleway Queues in the project
(`scaleway_mnq_sqs` in `sqs.tf`). If the project already uses Scaleway Queues,
import that resource into the state instead of creating it:
`terraform import scaleway_mnq_sqs.cloudhopper fr-par/<project ID>`.

---

## Logs

The container's output goes to Scaleway Cockpit. Open Cockpit in the console, or
query the logs data source with a Cockpit token that has the scope `query_logs`.
The queue triggers write their own log lines with the status of every delivery.
