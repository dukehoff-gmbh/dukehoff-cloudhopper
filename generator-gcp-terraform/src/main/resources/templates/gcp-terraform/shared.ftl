# Shared resources for GCP functions

# Terraform versions the generated files are tested with (1.x). The provider versions
# belong in your own main.tf: Terraform allows only one required_providers block per directory.
terraform {
  required_version = ">= 1.5.0, < 2.0.0"
}


# Shared storage bucket for all function JARs
resource "google_storage_bucket" "function_bucket" {
  name     = "${r"${var.project_name}-${var.environment}-functions"}"
  location = var.gcp_region
}

# Shared JAR file for all functions
resource "google_storage_bucket_object" "shared_function_archive" {
  name   = "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
  bucket = google_storage_bucket.function_bucket.name
  source = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
}
