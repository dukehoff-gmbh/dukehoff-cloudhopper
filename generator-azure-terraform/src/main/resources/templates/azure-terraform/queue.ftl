<#-- create is not claimed: the queue lives in the function app's storage account, which this
     configuration creates, so it is always created here -->
<#if eventSource == "QUEUE">
# Storage queue "${eventName}" in the function app's storage account (connection AzureWebJobsStorage).
# Messages must be Base64-encoded; after five failed deliveries they move to "${eventName}-poison".
resource "azurerm_storage_queue" "${handlerInfo.functionId}_queue" {
  name                 = "${eventName}"
  storage_account_id   = azurerm_storage_account.function_storage.id
}
</#if>
