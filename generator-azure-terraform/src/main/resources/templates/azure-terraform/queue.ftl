<#if eventSource == "QUEUE" && eventCreate>
# Storage queue "${eventName}" in the function app's storage account (connection AzureWebJobsStorage).
# Messages must be Base64-encoded; after five failed deliveries they move to "${eventName}-poison".
resource "azurerm_storage_queue" "${handlerInfo.functionId}_queue" {
  name                 = "${eventName}"
  storage_account_name = azurerm_storage_account.function_storage.name
}
</#if>
