resource "aws_lambda_function" "${operationId}_function" {
  function_name = "${operationId}"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "${handler}AWSLambdaHandler"
  runtime       = "java11"
  tag           =  "myapp"
  filename      = "lambda.zip"
  source_code_hash = filebase64sha256("lambda.zip")

}