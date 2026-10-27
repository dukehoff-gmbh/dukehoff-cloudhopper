# Configure the Azure provider
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
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

