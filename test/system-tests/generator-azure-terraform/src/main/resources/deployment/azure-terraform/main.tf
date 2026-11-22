# Configure the Azure provider
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.12"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
    # the TCK's vault: purged on destroy, so no soft-deleted vaults pile up
    key_vault {
      purge_soft_delete_on_destroy = true
    }
  }
}

# Variables
variable "azure_region" {
  description = "The Azure region to deploy to"
  type        = string
  default     = "germanywestcentral"  # Change this to your preferred default region
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "my-cloudhopper-project"
}

variable "environment" {
  description = "The deployment environment (e.g., dev, staging, prod)"
  type        = string
  default     = "test"
}


# The secret that the TCK function "secret" reads, in a Key Vault of its own (Azure RBAC). The
# binding applies these resources first and references the secret's ID in cloudhopper_secrets,
# never in cloudhopper_config.
variable "tck_vault_name" {
  description = "Name of the TCK's Key Vault, unique per run; empty: no vault"
  type        = string
  default     = ""
}

variable "tck_secret" {
  description = "The value of the TCK secret"
  type        = string
  sensitive   = true
  default     = ""
}

data "azurerm_client_config" "current" {}

resource "azurerm_resource_group" "tck_secrets" {
  count    = var.tck_vault_name == "" ? 0 : 1
  name     = "${var.tck_vault_name}-rg"
  location = var.azure_region
}

resource "azurerm_key_vault" "tck" {
  count                      = var.tck_vault_name == "" ? 0 : 1
  name                       = var.tck_vault_name
  resource_group_name        = azurerm_resource_group.tck_secrets[0].name
  location                   = azurerm_resource_group.tck_secrets[0].location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  purge_protection_enabled   = false
}

# the TCK's own principal writes the secret
resource "azurerm_role_assignment" "tck_secrets_officer" {
  count                = var.tck_vault_name == "" ? 0 : 1
  scope                = azurerm_key_vault.tck[0].id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# a new role assignment takes a while to take effect
resource "time_sleep" "tck_secrets_officer" {
  count           = var.tck_vault_name == "" ? 0 : 1
  depends_on      = [azurerm_role_assignment.tck_secrets_officer]
  create_duration = "90s"
}

resource "azurerm_key_vault_secret" "tck" {
  count        = var.tck_vault_name == "" ? 0 : 1
  name         = "tck-secret"
  value        = var.tck_secret
  key_vault_id = azurerm_key_vault.tck[0].id
  depends_on   = [time_sleep.tck_secrets_officer]
}
