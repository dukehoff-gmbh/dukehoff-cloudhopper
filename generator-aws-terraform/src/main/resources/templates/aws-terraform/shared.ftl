# Terraform versions the generated files are tested with (1.x). The provider versions
# belong in your own main.tf: Terraform allows only one required_providers block per directory.
terraform {
  required_version = ">= 1.5.0, < 2.0.0"
}

# Optional prefix for all named resources, e.g. to run several deployments of the
# same project side by side in one account (CI runs, previews). Empty by default.
variable "cloudhopper_name_prefix" {
  description = "Prefix prepended to the names of all generated AWS resources"
  type        = string
  default     = ""
}

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

# Values of the configuration properties the functions read with @ConfigProperty, by property
# name, e.g. { "report.bucket" = "my-reports" }. Functions get them as environment variables
# (report.bucket → REPORT_BUCKET). A value "ssm:<parameter name>" is read from SSM Parameter
# Store when Terraform plans, e.g. { "cloudhopper.auth.issuer" = "ssm:/app/auth/issuer" }: a
# String or StringList parameter; for secrets use cloudhopper_secrets.
variable "cloudhopper_config" {
  description = "Values of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}
}

# Secrets the functions read with @ConfigProperty, by property name: the ARN or name of a Secrets
# Manager secret, e.g. { "report.api-key" = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:report-key-AbCdEf" }.
# Only the reference is deployed; a function reads the value at runtime, and its identity may read
# exactly the secrets of the properties it reads.
variable "cloudhopper_secrets" {
  description = "Secrets Manager secrets (ARN or name) of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}
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

  # what the functions get; the ssm: references of cloudhopper_config resolved (below)
  cloudhopper_config  = merge(var.cloudhopper_config, { for name, parameter in data.aws_ssm_parameter.cloudhopper_config : name => nonsensitive(parameter.value) }, local.cloudhopper_extra_config)
  cloudhopper_secrets = merge(var.cloudhopper_secrets, local.cloudhopper_extra_secrets)
  # properties set more than once, checked by a precondition
  cloudhopper_duplicate_properties = sort(setunion(
    setintersection(keys(var.cloudhopper_config), keys(local.cloudhopper_extra_config)),
    setintersection(keys(var.cloudhopper_secrets), keys(local.cloudhopper_extra_secrets)),
    setintersection(keys(local.cloudhopper_config), keys(local.cloudhopper_secrets))))
}

# The values of cloudhopper_config given as "ssm:<parameter name>", read when Terraform plans
# (the deployment needs ssm:GetParameter). Only cloudhopper_config: values of
# cloudhopper_extra_config can refer to data sources of the project's Terraform directly.
data "aws_ssm_parameter" "cloudhopper_config" {
  for_each = { for name, value in var.cloudhopper_config : name => trimprefix(value, "ssm:") if startswith(value, "ssm:") }
  name     = each.value

  lifecycle {
    postcondition {
      # a SecureString value would end up in plain text in the function's environment and the state
      condition     = self.type != "SecureString"
      error_message = "The configuration property ${"$"}{each.key} refers to the SecureString parameter ${"$"}{each.value}: pass secrets in cloudhopper_secrets (Secrets Manager) instead."
    }
  }
}

# Name of the API Gateway stage. "$default" serves the API at the root of its endpoint
# (no stage segment in the URLs); any other name becomes the first path segment.
variable "api_stage_name" {
  description = "Name of the API Gateway stage; \"$default\" for URLs without a stage segment"
  type        = string
  default     = "$default"
}

# Retention of the functions' CloudWatch log groups /aws/lambda/<function>.
variable "log_retention_days" {
  description = "Days to keep the functions' logs (a CloudWatch retention value, e.g. 1, 7, 14, 30, 90, 365; 0 keeps them forever)"
  type        = number
  default     = 30
}

# Lets terraform destroy delete the artifact bucket even if it holds objects that
# Terraform does not manage. Off by default, so a destroy never deletes foreign objects.
variable "artifact_bucket_force_destroy" {
  description = "Delete the artifact bucket on destroy even if it contains objects not managed by Terraform"
  type        = bool
  default     = false
}

# The region, for the addresses of queues functions send to (@Queue).
data "aws_region" "cloudhopper" {}

resource "aws_s3_bucket" "lambda_artifacts" {
  bucket = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.project_name}-${"$"}{var.environment}-lambda-artifacts-${"$"}{data.aws_caller_identity.current.account_id}"
  force_destroy = var.artifact_bucket_force_destroy
}

<#-- the jar, or with -Aruntime=native the zip with the native executable bootstrap -->
<#assign artifact = ((runtime!"jvm") == "native")?then("${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}-native.zip", "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")>
resource "aws_s3_object" "lambda_jar" {
  bucket = aws_s3_bucket.lambda_artifacts.bucket
  # the bucket is per project and environment already
  key    = "${artifact}"
  source = "${"$"}{local.cloudhopper_artifact_dir}/${artifact}"
  etag   = filemd5("${"$"}{local.cloudhopper_artifact_dir}/${artifact}")
}

