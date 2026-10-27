terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"  # or your preferred region
}

resource "aws_s3_bucket" "tck_dummy" {
  bucket_prefix = "cloudhopper-tck-test-"
  force_destroy = true
}
