# Shared resources for GCP functions

# Terraform versions the generated files are tested with (1.x). The provider versions
# belong in your own main.tf: Terraform allows only one required_providers block per directory.
terraform {
  required_version = ">= 1.5.0, < 2.0.0"
}

# Values of the configuration properties the functions read with @ConfigProperty, by property
# name, e.g. { "report.bucket" = "my-reports" }. Functions get them as environment variables
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


# Shared storage bucket for all function JARs
resource "google_storage_bucket" "function_bucket" {
  name     = "${r"${var.project_name}-${var.environment}-functions"}"
  location = var.gcp_region
}

# Shared JAR file for all functions
resource "google_storage_bucket_object" "shared_function_archive" {
  name   = "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
  bucket = google_storage_bucket.function_bucket.name
  source = "${"$"}{local.cloudhopper_artifact_dir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.zip"
}
