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

## Runtime modules

The archetype adds these dependencies to the profile; add them yourself in a project of your own:

| Dependency | Scope | Contains |
|---|---|---|
| `eu.cloudhopper.mc:generator-azure-terraform` | `provided`: only the annotation processor uses it | Templates and template registration |
| `eu.cloudhopper.mc:provider-azure` | compile | `AzureBaseFunctionWrapper`, the base class of the generated function classes (HTTP, timer and queue triggers), and `StorageAccount`, which reads `AzureWebJobsStorage` and signs Storage requests |
| `eu.cloudhopper.mc:provider-azure-publisher` | compile | `@Queue` publishers for Storage queues, without the Azure SDK |
| `com.microsoft.azure.functions:azure-functions-java-library` | compile | The trigger annotations of the generated classes |
