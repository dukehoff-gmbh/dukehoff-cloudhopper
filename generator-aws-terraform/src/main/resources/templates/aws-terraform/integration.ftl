<#-- with SnapStart, the triggers invoke the published version through the alias "live" -->
<#-- SnapStart exists only for the Java runtime, not for a native image (-Aruntime=native) -->
<#assign snapStart = (runtime!"jvm") != "native" && (handlerInfo.extensionsMap["x-aws-snapstart"]!"") == "PublishedVersions">
<#assign lambdaTarget = snapStart?then("aws_lambda_alias.${handlerInfo.functionId}_live", "aws_lambda_function.${handlerInfo.functionId}")>
resource "aws_apigatewayv2_integration" "${handlerInfo.functionId}_integration" {
  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "AWS_PROXY"
  integration_uri    = ${lambdaTarget}.invoke_arn
  integration_method = "POST"
  payload_format_version = "2.0"
 }

resource "aws_apigatewayv2_route" "${handlerInfo.functionId}_route" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "${httpMethod} ${path}"
  target    = "integrations/${"$"}{aws_apigatewayv2_integration.${handlerInfo.functionId}_integration.id}"
<#if authenticated!false>
  # checked by API Gateway when the authorizer "jwt" exists, always by the function
  authorization_type = local.cloudhopper_jwt_authorizer ? "JWT" : "NONE"
  authorizer_id      = local.cloudhopper_jwt_authorizer ? aws_apigatewayv2_authorizer.jwt[0].id : null
<#if scopes?has_content>
  authorization_scopes = local.cloudhopper_jwt_authorizer ? [<#list scopes as s>"${s}"<#sep>, </#sep></#list>] : null
</#if>
</#if>
<#if path == "/ping" || path == "/sping">
  lifecycle {
    precondition {
      condition     = var.api_stage_name != "$default"
      error_message = "API Gateway answers ${path} on the $default stage with its own health check, so the route of function ${handlerInfo.functionId} would never be called. Set api_stage_name to a stage name (e.g. \"api\") or change the path."
    }
  }
</#if>
}

resource "aws_lambda_permission" "${handlerInfo.functionId}_invoke" {
  # a prefix, so that a new permission can exist next to the one it replaces
  statement_id_prefix = "AllowExecutionFromAPIGateway-"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.${handlerInfo.functionId}.function_name
<#if snapStart>
  qualifier     = aws_lambda_alias.${handlerInfo.functionId}_live.name
</#if>
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${"$"}{aws_apigatewayv2_api.main.execution_arn}/*/*"

  # switching SnapStart or the runtime moves the permission from the alias to the function (or
  # back): the new one is created before the old one is removed, so no request is refused
  lifecycle {
    create_before_destroy = true
  }
}

output "${handlerInfo.functionId}_url" {
  value = "${"$"}{aws_apigatewayv2_api.main.api_endpoint}${"$"}{aws_apigatewayv2_stage.main_stage.name == "$default" ? "" : "/${"$"}{aws_apigatewayv2_stage.main_stage.name}"}${path}"
}
