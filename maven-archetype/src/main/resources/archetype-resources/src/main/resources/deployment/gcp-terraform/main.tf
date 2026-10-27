terraform {
 
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 6.26.0"
    }
   local = {
      source  = "hashicorp/local"
      version = "~> 2.0"
    }
  }
}

# Configure the Google Cloud provider
provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
  zone    = var.gcp_zone
}

provider "google-beta" {
  project = var.gcp_project_id
  region  = var.gcp_region
  zone    = var.gcp_zone
}


# Variables (keep the existing variables)
variable "gcp_project_id" {
  description = "The GCP project ID"
  type        = string
}

variable "gcp_region" {
  description = "The GCP region to deploy to"
  type        = string
}

variable "gcp_zone" {
  description = "The GCP zone to deploy to"
  type        = string
}

variable "project_name" {
  description = "The name of the project"
  type        = string
}

variable "environment" {
  description = "The deployment environment (e.g., dev, staging, prod)"
  type        = string
}

variable "cloudscheduler_service_account_email" {
  description = "The service account to use for calling scheduled functions"
  type        = string
}

# Output the bucket name
output "terraform_state_bucket" {
  value = google_storage_bucket.terraform_state.name
}
