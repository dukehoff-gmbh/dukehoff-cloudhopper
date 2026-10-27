terraform {
  backend "gcs" {
    bucket = "cloudhopper-demo-terraform-state-bucket"
    prefix = "terraform/state"
  }
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

# Create a GCS bucket for storing Terraform state
resource "google_storage_bucket" "terraform_state" {
  name          = "cloudhopper-demo-terraform-state-bucket"
  location      = var.gcp_region
  force_destroy = true
  
  versioning {
    enabled = true
  }
}

# Variables (keep the existing variables)
variable "gcp_project_id" {
  description = "The GCP project ID"
  type        = string
}

variable "gcp_region" {
  description = "The GCP region to deploy to"
  type        = string
  default     = "us-central1"
}

variable "gcp_zone" {
  description = "The GCP zone to deploy to"
  type        = string
  default     = "us-central1-a"
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "my-cloudhopper-project"
}

variable "environment" {
  description = "The deployment environment (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "cloudscheduler_service_account_email" {
  description = "The service account to use for calling scheduled functions"
  type        = string
}

# Output the bucket name
output "terraform_state_bucket" {
  value = google_storage_bucket.terraform_state.name
}