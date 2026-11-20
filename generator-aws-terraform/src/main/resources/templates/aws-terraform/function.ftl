<#-- with SnapStart, the triggers invoke the published version through the alias "live" -->
<#assign snapStart = (handlerInfo.extensionsMap["x-aws-snapstart"]!"") == "PublishedVersions">
<#assign lambdaTarget = snapStart?then("aws_lambda_alias.${handlerInfo.functionId}_live", "aws_lambda_function.${handlerInfo.functionId}")>
resource "aws_lambda_function" "${handlerInfo.functionId}" {
  #filename      = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  function_name = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}"
  role          = aws_iam_role.identity_${handlerInfo.identity}.arn
  handler       = "${handlerWrapperFullyQualifiedName}$Auto::handleRequest"
  
  s3_bucket     = aws_s3_bucket.lambda_artifacts.bucket
  s3_key        = aws_s3_object.lambda_jar.key
  source_code_hash = filebase64sha256("${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")

  timeout = ${handlerInfo.timeout}
  memory_size = ${handlerInfo.memory}
  runtime = "java21"
  <#-- look up the AWS architecture extension (falls back to empty string) -->
  <#assign awsArch = handlerInfo.extensionsMap["x-aws-architecture"]!>
  <#if awsArch?has_content>
  architectures = ["${awsArch}"]
  </#if>
  depends_on = [aws_cloudwatch_log_group.${handlerInfo.functionId}_logs]
<#if snapStart>

  # every deployment publishes a version; Lambda takes the SnapStart snapshot of it
  publish = true
  snap_start {
    apply_on = "PublishedVersions"
  }
</#if>

  environment {
    variables = {
      # lets the runtime report bucket and queue names without the prefix
      CLOUDHOPPER_NAME_PREFIX = var.cloudhopper_name_prefix
    }
  }
}

# The log group Lambda writes to, created before the function so that Lambda does not
# create it on the first invocation (without retention, outside of Terraform's state).
# Deployments made before this group was managed here already have it; import it once:
#   terraform import 'aws_cloudwatch_log_group.${handlerInfo.functionId}_logs' /aws/lambda/<function name>
resource "aws_cloudwatch_log_group" "${handlerInfo.functionId}_logs" {
  name              = "/aws/lambda/${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}"
  retention_in_days = var.log_retention_days
}
<#if snapStart>

# The triggers invoke the latest published version through this alias, so that new instances
# start from its SnapStart snapshot.
resource "aws_lambda_alias" "${handlerInfo.functionId}_live" {
  name             = "live"
  function_name    = aws_lambda_function.${handlerInfo.functionId}.function_name
  function_version = aws_lambda_function.${handlerInfo.functionId}.version
}
</#if>
