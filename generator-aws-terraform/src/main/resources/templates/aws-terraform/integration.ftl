resource "aws_apigatewayv2_integration" "${handlerInfo.functionId}_integration" {
  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "AWS_PROXY"
  integration_uri    = aws_lambda_function.${handlerInfo.functionId}.invoke_arn
  integration_method = "POST"
  payload_format_version = "2.0"
 }

resource "aws_apigatewayv2_route" "${handlerInfo.functionId}_route" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "${httpMethod} ${path}"
  target    = "integrations/${"$"}{aws_apigatewayv2_integration.${handlerInfo.functionId}_integration.id}"
  # authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_lambda_permission" "${handlerInfo.functionId}_invoke" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.${handlerInfo.functionId}.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${"$"}{aws_apigatewayv2_api.main.execution_arn}/*/*"
}

output "${handlerInfo.functionId}_url" {
  value = "${"$"}{aws_apigatewayv2_api.main.api_endpoint}/${"$"}{aws_apigatewayv2_stage.main_stage.name}${path}"
}
