# Shared resources for Azure Functions

# Terraform versions the generated files are tested with (1.x). The provider versions
# belong in your own main.tf: Terraform allows only one required_providers block per directory.
terraform {
  required_version = ">= 1.5.0, < 2.0.0"
}

# Values of the configuration properties the functions read with @ConfigProperty, by property
# name, e.g. { "report.bucket" = "my-reports" }. All functions run in one function app and get all of them as environment variables
# (report.bucket → REPORT_BUCKET).
# The directory of the built artifact. By default the Maven target directory, relative to this
# module, so that the configuration also works from another directory or machine (e.g. a CI job
# that checked out the build's target directory).
variable "cloudhopper_artifact_dir" {
  description = "Directory of the built artifact (jar or zip); empty: the Maven target directory relative to this module"
  type        = string
  default     = ""
}

locals {
  cloudhopper_artifact_dir = var.cloudhopper_artifact_dir != "" ? var.cloudhopper_artifact_dir : "${"$"}{path.module}/${artifactDir}"
}

variable "cloudhopper_config" {
  description = "Values of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}
}

# Secrets the functions read with @ConfigProperty, by property name: the resource ID of a Key Vault
# secret without version (azurerm_key_vault_secret.<name>.resource_versionless_id), e.g.
# { "report.api-key" = "/subscriptions/.../resourceGroups/.../providers/Microsoft.KeyVault/vaults/my-vault/secrets/report-key" }.
# Only the reference is deployed; the functions read the value at runtime with the function app's
# managed identity, which gets "Key Vault Secrets User" on exactly these secrets (vaults with
# Azure RBAC).
variable "cloudhopper_secrets" {
  description = "Key Vault secrets (resource ID without version) of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for id in values(var.cloudhopper_secrets) : can(regex("^/subscriptions/[^/]+/resourceGroups/[^/]+/providers/Microsoft.KeyVault/vaults/[^/]+/secrets/[^/]+$", id))])
    error_message = "Each value of cloudhopper_secrets must be the resource ID of a Key Vault secret without version: /subscriptions/<id>/resourceGroups/<group>/providers/Microsoft.KeyVault/vaults/<vault>/secrets/<name>"
  }
}

# Values from the project's own Terraform. The generated files and your own .tf files form one
# module, and a variable cannot refer to its resources or data sources. To pass such values,
# redefine these locals in an override file (override.tf or <name>_override.tf next to main.tf):
#   locals {
#     cloudhopper_extra_config  = { "auth.client-id" = <resource>.<name>.<attribute> }
#     cloudhopper_extra_secrets = { "report.api-key" = <reference to a secret> }
#   }
# A property may be set only once in cloudhopper_config, cloudhopper_secrets and these locals.
locals {
  cloudhopper_extra_config  = {}
  cloudhopper_extra_secrets = {}

  # what the functions get
  cloudhopper_config  = merge(var.cloudhopper_config, local.cloudhopper_extra_config)
  cloudhopper_secrets = merge(var.cloudhopper_secrets, local.cloudhopper_extra_secrets)
  # properties set more than once, checked by a precondition
  cloudhopper_duplicate_properties = sort(setunion(
    setintersection(keys(var.cloudhopper_config), keys(local.cloudhopper_extra_config)),
    setintersection(keys(var.cloudhopper_secrets), keys(local.cloudhopper_extra_secrets)),
    setintersection(keys(local.cloudhopper_config), keys(local.cloudhopper_secrets))))
}


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
  storage_container_id   = azurerm_storage_container.function_container.id
  type                   = "Block"
  source                 = "${"$"}{local.cloudhopper_artifact_dir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
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

# The function apps, one per identity (@Function(identity)), are in api_apps.tf.

resource "azurerm_application_insights" "function_app_insights" {
  name                = "${"$"}{var.project_name}-${"$"}{var.environment}-ai"
  location            = azurerm_resource_group.function_rg.location
  resource_group_name = azurerm_resource_group.function_rg.name
  application_type    = "web"
  retention_in_days   = var.log_retention_days
}

variable "log_retention_days" {
  description = "Days Application Insights keeps the functions' telemetry and logs"
  type        = number
  default     = 30
  validation {
    condition     = contains([30, 60, 90, 120, 180, 270, 365, 550, 730], var.log_retention_days)
    error_message = "Application Insights accepts 30, 60, 90, 120, 180, 270, 365, 550 or 730 days."
  }
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
