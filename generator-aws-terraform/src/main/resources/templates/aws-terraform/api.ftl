resource "aws_apigatewayv2_api" "main" {
  name          = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.environment}_${"$"}{var.project_name}_public_api"
  protocol_type = "HTTP"
  target        = null
  route_selection_expression = "${"$"}request.method ${"$"}request.path"
}

# The JWT authorizer of API Gateway: it rejects requests to authenticated routes
# (@HttpTrigger(authenticated = true)) before the function runs, so these cost no invocation.
# API Gateway reads the keys from the OpenID discovery document of the issuer
# (<issuer>/.well-known/openid-configuration), so the authorizer is created only when
# cloudhopper.auth.issuer and cloudhopper.auth.audience are set in cloudhopper_config (or
# cloudhopper_extra_config) and neither cloudhopper.auth.jwks nor cloudhopper.auth.jwks-uri is
# set; otherwise only the function checks the token. The function checks it in any case.
variable "cloudhopper_api_jwt_authorizer" {
  description = "Whether API Gateway checks the tokens of authenticated routes before the function runs (only when the issuer serves OpenID discovery)"
  type        = bool
  default     = true
}

locals {
  cloudhopper_jwt_authorizer = var.cloudhopper_api_jwt_authorizer && alltrue([
    contains(keys(local.cloudhopper_config), "cloudhopper.auth.issuer"),
    contains(keys(local.cloudhopper_config), "cloudhopper.auth.audience"),
    !contains(keys(local.cloudhopper_config), "cloudhopper.auth.jwks"),
    !contains(keys(local.cloudhopper_config), "cloudhopper.auth.jwks-uri"),
    !contains(keys(local.cloudhopper_secrets), "cloudhopper.auth.jwks"),
    !contains(keys(local.cloudhopper_secrets), "cloudhopper.auth.jwks-uri"),
  ])
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  count            = local.cloudhopper_jwt_authorizer ? 1 : 0
  name             = "cloudhopper-jwt"
  api_id           = aws_apigatewayv2_api.main.id
  authorizer_type  = "JWT"
  identity_sources = ["${"$"}request.header.Authorization"]

  jwt_configuration {
    issuer   = local.cloudhopper_config["cloudhopper.auth.issuer"]
    audience = [local.cloudhopper_config["cloudhopper.auth.audience"]]
  }
}

resource "aws_apigatewayv2_deployment" "main_deployment" {
  api_id = aws_apigatewayv2_api.main.id

  triggers = {
    redeployment = sha1(join(",", concat(tolist([
<#list lambdaMap?keys as key>
      jsonencode(aws_apigatewayv2_route.${lambdaMap[key]}_route.route_key),
      jsonencode(aws_apigatewayv2_route.${lambdaMap[key]}_route.authorization_type),
      jsonencode(aws_apigatewayv2_route.${lambdaMap[key]}_route.authorization_scopes),
      jsonencode(aws_apigatewayv2_integration.${lambdaMap[key]}_integration.integration_uri)<#if key_has_next>,</#if>
</#list>
    ]), [jsonencode(aws_apigatewayv2_authorizer.jwt[*].jwt_configuration)])))
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
  name          = var.api_stage_name
  deployment_id = aws_apigatewayv2_deployment.main_deployment.id
}