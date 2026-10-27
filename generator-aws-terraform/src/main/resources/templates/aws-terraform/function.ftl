resource "aws_lambda_function" "${handlerInfo.functionId}" {
  #filename      = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  function_name = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}"
  role          = aws_iam_role.lambda_exec.arn
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
  environment {
    variables = {
      # Add any environment variables your function needs
    }
  }
}

# The CloudWatch log group "/aws/lambda/<function_name>" is intentionally
# NOT managed by Terraform. AWS Lambda creates it automatically on first invocation
# (the execution role has the AWSLambdaBasicExecutionRole logs permissions). Managing
# it here caused "already exists" conflicts, because Lambda re-creates the group
# outside of Terraform's state whenever the function runs.


