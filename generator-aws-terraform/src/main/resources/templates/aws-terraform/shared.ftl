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

# Values of the configuration properties the functions read with @ConfigProperty, by property
# name, e.g. { "report.bucket" = "my-reports" }. Functions get them as environment variables
# (report.bucket → REPORT_BUCKET).
variable "cloudhopper_config" {
  description = "Values of the functions' configuration properties (@ConfigProperty), by property name"
  type        = map(string)
  default     = {}
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

resource "aws_s3_object" "lambda_jar" {
  bucket = aws_s3_bucket.lambda_artifacts.bucket
  # the bucket is per project and environment already
  key    = "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  source = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  etag   = filemd5("${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")
}

