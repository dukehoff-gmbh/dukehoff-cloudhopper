<#include "storageQueueName.ftl">
<#if eventSource == "STORAGE">
<#assign fid = handlerInfo.functionId>
<#assign queueName = storageEventQueue(eventName)>
<#if eventCreate>
# Blob container "${eventName}" in the function app's storage account.
resource "azurerm_storage_container" "${fid}_container" {
  name                  = "${eventName}"
  storage_account_id    = azurerm_storage_account.function_storage.id
  container_access_type = "private"
}
</#if>

# Event Grid puts every "blob created" event of the container on this queue; the function
# "${fid}_event" reads it. After five failed deliveries an event moves to "${queueName}-poison".
resource "azurerm_storage_queue" "${fid}_storage_events" {
  name                 = "${queueName}"
  storage_account_name = azurerm_storage_account.function_storage.name
}

resource "azurerm_eventgrid_event_subscription" "${fid}_storage" {
  name  = "${fid?lower_case?replace("_", "-")}-storage"
  scope = azurerm_storage_account.function_storage.id

  included_event_types = ["Microsoft.Storage.BlobCreated"]
  subject_filter {
    subject_begins_with = "/blobServices/default/containers/${eventName}/"
  }

  storage_queue_endpoint {
    storage_account_id = azurerm_storage_account.function_storage.id
    queue_name         = azurerm_storage_queue.${fid}_storage_events.name
  }
<#if eventCreate>

  depends_on = [azurerm_storage_container.${fid}_container]
</#if>
}
</#if>
