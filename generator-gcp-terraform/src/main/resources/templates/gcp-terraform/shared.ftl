# Shared resources for GCP functions

# Terraform versions the generated files are tested with (1.x). The provider versions
# belong in your own main.tf: Terraform allows only one required_providers block per directory.
terraform {
  required_version = ">= 1.5.0, < 2.0.0"
}

# Values of the configuration properties the functions read with @ConfigProperty, by property
# name, e.g. { "report.bucket" = "my-reports" }. Functions get them as environment variables
# (report.bucket → REPORT_BUCKET).
variable "cloudhopper_config" {
  description = "Values of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}
}

# Secrets the functions read with @ConfigProperty, by property name: a Secret Manager secret, by
# name in this project or as projects/<project>/secrets/<name>, e.g. { "report.api-key" = "report-key" }.
# Only the reference is deployed; Cloud Functions sets the latest version as an environment
# variable of the function (report.api-key → REPORT_API_KEY), and the service account of each
# identity may read exactly the secrets of the properties its functions read.
variable "cloudhopper_secrets" {
  description = "Secret Manager secrets (name or projects/<project>/secrets/<name>) of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for ref in values(var.cloudhopper_secrets) : can(regex("^([A-Za-z0-9_-]+|projects/[^/]+/secrets/[A-Za-z0-9_-]+)$", ref))])
    error_message = "Each value of cloudhopper_secrets must be a Secret Manager secret: <name> or projects/<project>/secrets/<name>"
  }
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
