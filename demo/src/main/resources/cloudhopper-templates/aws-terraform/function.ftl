resource "aws_lambda_function" "${handlerInfo.functionId}" {
  #filename      = "${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar"
  function_name = "${handlerInfo.functionId}"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "${handlerWrapperFullyQualifiedName}$Auto::handleRequest"
  
  s3_bucket     = aws_s3_bucket.lambda_artifacts.bucket
  s3_key        = aws_s3_object.lambda_jar.key
  source_code_hash = filebase64sha256("${handlerInfo.targetDir}/${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")

  timeout = ${handlerInfo.timeout}
  memory_size = ${handlerInfo.memory}
  runtime = "java21"
  
  environment {
    variables = {
      # Add any environment variables your function needs
    }
  }
  tags = {
    Name = "override_templates_test"
  }
}

resource "aws_cloudwatch_log_group" "${handlerInfo.functionId}_log_group" {
  name              = "/aws/lambda/${handlerInfo.functionId}"
  retention_in_days = 7
}



