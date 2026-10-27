# Configure the Azure provider
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }

#  backend "azurerm" {
#    resource_group_name  = "terraform-state-rg"
#    storage_account_name = "terrastateYOURUNIQUENAME"
#    container_name       = "tfstate"
#    key                  = "terraform.tfstate"
#  }
}

provider "azurerm" {
  features {}
}

# Variables
variable "azure_region" {
  description = "The Azure region to deploy to"
  type        = string
  default     = "East US"  # Change this to your preferred default region
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
