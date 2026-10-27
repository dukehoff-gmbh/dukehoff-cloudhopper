resource "aws_apigatewayv2_api" "main" {
  name          = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.environment}_${"$"}{var.project_name}_public_api"
  protocol_type = "HTTP"
  target        = null
  route_selection_expression = "${"$"}request.method ${"$"}request.path"
}

resource "aws_apigatewayv2_deployment" "main_deployment" {
  api_id = aws_apigatewayv2_api.main.id

  triggers = {
    redeployment = sha1(join(",", tolist([
<#list lambdaMap?keys as key>
      jsonencode(aws_apigatewayv2_route.${lambdaMap[key]}_route.route_key),
      jsonencode(aws_apigatewayv2_integration.${lambdaMap[key]}_integration.integration_uri)<#if key_has_next>,</#if>
</#list>
    ])))
  }

  depends_on = [
    aws_apigatewayv2_route.${lambdaMap?values?first}_route
  ]

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_apigatewayv2_stage" "main_stage" {
  api_id        = aws_apigatewayv2_api.main.id
  name          = "test"
  deployment_id = aws_apigatewayv2_deployment.main_deployment.id
}