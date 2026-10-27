# Configure the AWS Provider
provider "aws" {
  region = var.aws_region
}

# Variables
variable "aws_region" {
  description = "The AWS region to deploy to"
  type        = string
  default     = "eu-central-1"  # Change this to your preferred default region
}

variable "project_name" {
  description = "The name of the project"
  type        = string
  default     = "systemtest"
}

variable "environment" {
  description = "The deployment environment (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
}

data "aws_caller_identity" "current" {}
#resource "aws_cognito_user_pool" "main" {
#  name = "cloudhopper-user-pool"
#}
#
#resource "aws_cognito_user_pool_client" "main" {
#  name         = "cloudhopper-app-client"
#  user_pool_id = aws_cognito_user_pool.main.id
#
#  generate_secret = false
#  allowed_oauth_flows_user_pool_client = true
#
#  explicit_auth_flows = [
#    "ALLOW_USER_PASSWORD_AUTH",
#    "ALLOW_REFRESH_TOKEN_AUTH",
#    "ALLOW_USER_SRP_AUTH"
#  ]
#
#  allowed_oauth_flows = ["code"]
#  allowed_oauth_scopes = ["email", "openid"]
#  callback_urls         = ["https://example.com/callback"]
#  logout_urls           = ["https://example.com/logout"]
#  supported_identity_providers = ["COGNITO"]
#}
#
#resource "aws_apigatewayv2_authorizer" "cognito_jwt" {
#  name                       = "CognitoJWT"
#  api_id                     = aws_apigatewayv2_api.main.id
#  authorizer_type            = "JWT"
#  identity_sources           = ["$request.header.Authorization"]
#
#  jwt_configuration {
#    audience = [aws_cognito_user_pool_client.main.id]
#    issuer   = "https://${aws_cognito_user_pool.main.endpoint}"
#  }
#}


