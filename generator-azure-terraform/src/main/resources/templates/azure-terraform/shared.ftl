# Shared resources for Azure Functions

resource "azurerm_resource_group" "function_rg" {
  name     = "${r"${var.project_name}-${var.environment}"}-rg"
  location = var.azure_region
}

resource "azurerm_storage_account" "function_storage" {
  name                     = "${r'${lower(replace(var.project_name, "-", ""))}${var.environment}'}"
  resource_group_name      = azurerm_resource_group.function_rg.name
  location                 = azurerm_resource_group.function_rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_service_plan" "function_plan" {
  name                = "${r"${var.project_name}-${var.environment}"}-plan"
  resource_group_name = azurerm_resource_group.function_rg.name
  location            = azurerm_resource_group.function_rg.location
  os_type             = "Linux"
  sku_name            = "Y1" # This is the consumption plan
}

resource "azurerm_storage_container" "function_container" {
  name                  = "function-container"
  storage_account_id    = azurerm_storage_account.function_storage.id
  container_access_type = "private"
  depends_on            = [azurerm_storage_account.function_storage]
}

# Shared JAR file for all functions
resource "azurerm_storage_blob" "function_code" {
  name                   = "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
  storage_account_name   = azurerm_storage_account.function_storage.name
  storage_container_name = azurerm_storage_container.function_container.name
  type                   = "Block"
  source                 = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
  depends_on             = [azurerm_storage_container.function_container]
}

data "azurerm_storage_account_blob_container_sas" "function_zip_sas" {
  connection_string = azurerm_storage_account.function_storage.primary_connection_string
  container_name    = azurerm_storage_container.function_container.name

  start  = "2024-01-01"
  expiry = "2099-01-01"

  permissions {
    read   = true
    list   = false
    add    = false
    create = false
    write  = false
    delete = false
  }
}

resource "azurerm_linux_function_app" "shared_function_app" {
  name                = "my-shared-function-app"
  resource_group_name = azurerm_resource_group.function_rg.name
  location            = azurerm_resource_group.function_rg.location
  
  service_plan_id            = azurerm_service_plan.function_plan.id
  storage_account_name       = azurerm_storage_account.function_storage.name
  storage_account_access_key = azurerm_storage_account.function_storage.primary_access_key

  site_config {
    application_stack {
      java_version = "21" 
    }
  }

  app_settings = {
    FUNCTIONS_WORKER_RUNTIME    = "java"
    AzureWebJobsDisableHomepage = "true"
    WEBSITE_RUN_FROM_PACKAGE    = "${"$"}{azurerm_storage_blob.function_code.url}?${"$"}{trim(data.azurerm_storage_account_blob_container_sas.function_zip_sas.sas, "?")}"
    
    # Enable Application Logging (Filesystem)
    "AzureWebJobsDashboard"           = "true" # Not strictly required anymore, but harmless
    "WEBSITE_ENABLE_APP_SERVICE_STORAGE" = "true"

    # Set the logging level
    "FUNCTIONS_EXTENSION_VERSION"    = "~4"    # You're already using this
    "FUNCTIONS_LOGGING_CONSOLE_LEVEL" = "Information"  # or "Verbose" for even more

    APPLICATIONINSIGHTS_CONNECTION_STRING = azurerm_application_insights.function_app_insights.connection_string
    APPLICATIONINSIGHTS_ROLE_NAME         = "my-shared-function-app"
  }

  depends_on = [azurerm_storage_blob.function_code]
}


resource "azurerm_application_insights" "function_app_insights" {
  name                = "${"$"}{var.project_name}-${"$"}{var.environment}-ai"
  location            = azurerm_resource_group.function_rg.location
  resource_group_name = azurerm_resource_group.function_rg.name
  application_type    = "web"
}


output "application_insights_name" {
  value = azurerm_application_insights.function_app_insights.name
}
output "resource_group_name" {
  value = azurerm_resource_group.function_rg.name
}

output "function_storage_name" {
  value = azurerm_storage_account.function_storage.name
}

output "function_storage_primary_access_key" {
  value     = azurerm_storage_account.function_storage.primary_access_key
  sensitive = true
}

output "function_plan_id" {
  value = azurerm_service_plan.function_plan.id
}

output "shared_zip_url" {
  value = azurerm_storage_blob.function_code.url
}
