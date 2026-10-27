# Optional prefix for all named resources, e.g. to run several deployments of the
# same project side by side in one account (CI runs, previews). Empty by default.
variable "cloudhopper_name_prefix" {
  description = "Prefix prepended to the names of all generated AWS resources"
  type        = string
  default     = ""
}

resource "aws_s3_bucket" "lambda_artifacts" {
  bucket = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.project_name}-${"$"}{var.environment}-lambda-artifacts-${"$"}{data.aws_caller_identity.current.account_id}"
  force_destroy = true  # only for tests; deletes all objects when bucket is deleted
}

resource "aws_iam_role" "lambda_exec" {
  name = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.project_name}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_exec_policy" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "lambda_s3_access" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"
}

resource "aws_s3_object" "lambda_jar" {
  bucket = aws_s3_bucket.lambda_artifacts.bucket
  key    = "systemtests/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  source = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  etag   = filemd5("${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")
}

