terraform {
  backend "s3" {
    bucket = "cloudhopper-demo-terraform-state-bucket"
    key    = "state/terraform.tfstate"
    region = "eu-central-1"
  }
}

# Configure the AWS Provider
provider "aws" {
  region = var.aws_region
  
  # Uncomment and fill these if you want to specify credentials in the Terraform file
  # access_key = var.aws_access_key
  # secret_key = var.aws_secret_key
  
  # Alternatively, you can specify a profile
  profile = var.aws_profile
}

# Variables
variable "aws_region" {
  description = "The AWS region to deploy to"
  type        = string
  default     = "eu-central-1"  # Change this to your preferred default region
}

#variable "aws_access_key" {
#  description = "AWS access key"
#  type        = string
#  default     = ""  # Do not put actual credentials here
#}
#
#variable "aws_secret_key" {
#  description = "AWS secret key"
#  type        = string
#  default     = ""  # Do not put actual credentials here
#}

variable "aws_profile" {
  description = "AWS profile to use"
  type        = string
  default     = "dukehoff"
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

data "aws_caller_identity" "current" {}

