---
title: Azure Functions
sidebar_label: Azure Functions
---

# Azure Functions

The profile `azure-terraform` deploys all functions into one Azure function app on
the consumption plan, with Terraform for the infrastructure: HTTP routes of the
function app, timer triggers for schedules, Storage queues and Event Grid for
events. Set up the subscription and the tools with the
[Azure setup guide](../setup/azure-prerequisites.md).

## What is generated

| Template | Output | Purpose |
|---|---|---|
| `function.ftl` | Java | HTTP-triggered function class per function |
| `apiIntegrationClass.ftl` | Java | Function class for an `@HttpTrigger` route |
| `schedule.ftl` | Java | Function class with `@TimerTrigger` |
| `queueFunction.ftl` | Java | Function class with `@QueueTrigger`, for queue messages and blob events |
| `shared.ftl` | Terraform | Resource group, storage account, consumption plan, function app, Application Insights |
| `output.ftl` | Terraform | Outputs with the function URLs |
| `queue.ftl` | Terraform | Storage queue |
| `storage.ftl` | Terraform | Blob container, event queue and Event Grid subscription |

On Azure, triggers are Java annotations on generated classes; Terraform
creates the infrastructure they bind to.

## Logs

The variable `log_retention_days` (default `30`) sets how long Application Insights
keeps telemetry and logs. Application Insights accepts 30, 60, 90, 120, 180, 270, 365,
550 or 730 days. Before, the Azure default of 90 days applied.

## Sending messages

A function with a [`@Queue`](../guides/writing-functions.md#sending-messages)
parameter needs the dependency `eu.cloudhopper.mc:provider-azure-publisher`; the
archetype adds it. It sends to the Storage queue of that name in the function app's
storage account, signed with the account key from the app's `AzureWebJobsStorage`
setting, without the Azure SDK. A message can be at most 64 KiB after Base64
encoding, and the Queue service has no batches, so `sendAll` sends one request per
message. Queues of other storage accounts (`existing = true`) are not supported yet.

## Secrets

The Terraform variable `cloudhopper_secrets` maps a property name to a secret in
Azure Key Vault, by the secret's resource ID without version
(`azurerm_key_vault_secret.<name>.resource_versionless_id`):

```hcl
cloudhopper_secrets = {
  "report.api-key" = "/subscriptions/<id>/resourceGroups/<group>/providers/Microsoft.KeyVault/vaults/<vault>/secrets/report-key"
}
```

- The function app gets a system-assigned managed identity, and the identity gets
  the role **Key Vault Secrets User** on exactly these secrets. The vault must use
  Azure RBAC (`rbac_authorization_enabled = true`); vaults with access policies are
  not supported. The output `function_app_principal_id` names the identity, e.g. to
  grant it further access.
- The app gets the URIs of the secrets in the setting `CLOUDHOPPER_SECRETS`. On
  first access, `provider-azure` reads a value from Key Vault with a token of the
  managed identity, without the Azure SDK, and keeps it for the life of the
  instance. A new role assignment can take a few minutes to take effect; until
  then, the read is retried for about a minute.
- All functions run in one function app, so they share one identity and can read
  all secrets of the app.
- Deploying needs permission to create role assignments, e.g. the role *Role
  Based Access Control Administrator* (or *User Access Administrator*) in addition
  to *Contributor*.
- `terraform plan` fails if a property is set in both `cloudhopper_config` and
  `cloudhopper_secrets`.

The functions read the secret themselves instead of through Key Vault references in
the app settings: the platform resolves those when the app starts, and a role
assignment that has not taken effect yet leaves the reference text as the value
until the next refresh.

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-azure-terraform` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-azure` | compile | `AzureBaseFunctionWrapper`, the base class of the generated function classes (HTTP, timer and queue triggers), `StorageAccount`, which reads `AzureWebJobsStorage` and signs Storage requests, and the `ConfigSource` that reads secrets from Key Vault |
| `eu.cloudhopper.mc:provider-azure-publisher` | compile | `@Queue` publishers for Storage queues, without the Azure SDK |
| `com.microsoft.azure.functions:azure-functions-java-library` | compile | The trigger annotations of the generated classes |
