# Azure Setup Guide

This guide prepares your environment for deploying Cloudhopper functions to
**Azure Functions** with the `azure-terraform` generator.

The generated Terraform creates one Linux Function App that hosts all your
functions, together with its resource group, storage account, service plan and
Application Insights.

---

## Azure account

You need an Azure subscription in which you may create resources. If you don't
have one, create it at [azure.microsoft.com](https://azure.microsoft.com/).

---

## Install the Azure CLI

Follow the [installation guide](https://learn.microsoft.com/cli/azure/install-azure-cli)
for your operating system, for example:

```bash
# macOS
brew install azure-cli
```

---

## Sign in and pick the subscription

```bash
az login
az account set --subscription "<subscription-id>"
az account show
```

---

## Credentials for Terraform

Terraform's `azurerm` provider reads its credentials from environment
variables. For a service principal:

```bash
export ARM_SUBSCRIPTION_ID="<subscription-id>"
export ARM_TENANT_ID="<tenant-id>"
export ARM_CLIENT_ID="<app-id>"
export ARM_CLIENT_SECRET="<client-secret>"
```

If you don't have a service principal yet, one way to create it is:

```bash
az ad sp create-for-rbac --role Contributor \
  --scopes /subscriptions/<subscription-id>
```

The output contains `appId` (use it as `ARM_CLIENT_ID`), `password`
(`ARM_CLIENT_SECRET`) and `tenant` (`ARM_TENANT_ID`).

The repository contains a helper script, `export-azure-env.sh`, that reads
the subscription and tenant ID from your Azure CLI profile. Client ID and
secret still have to be set by hand.

---

## Terraform variables

The generated `main.tf` in `src/main/resources/deployment/azure-terraform/`
declares these variables:

| Variable | Example |
|---|---|
| `azure_region` | `germanywestcentral` |
| `project_name` | `my-cloudhopper-project` |
| `environment` | `dev` |

Set them in a file such as `azure.auto.tfvars` next to `main.tf`, which
Terraform loads automatically, or as environment variables
(`TF_VAR_azure_region`, …). Resource names are derived from `project_name`
and `environment`; the storage account name must be globally unique in Azure.

---

## Next

Continue with the [Quickstart](../guides/quickstart), using the
`azure-terraform` profile.
