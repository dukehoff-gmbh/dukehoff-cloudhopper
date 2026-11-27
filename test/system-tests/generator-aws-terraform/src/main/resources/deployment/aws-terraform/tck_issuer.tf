# The identity provider of the authentication test: the TCK creates an RSA key for each run and
# passes its public key set as tck_jwks. This bucket publishes it with an OpenID discovery
# document, so that the JWT authorizer of API Gateway can read it; the bucket URL is the issuer.
variable "tck_jwks" {
  description = "The public key set of the TCK's issuer, as JSON"
  type        = string
  default     = "{\"keys\":[]}"
}

resource "aws_s3_bucket" "tck_issuer" {
  bucket        = "${var.cloudhopper_name_prefix}cloudhopper-tck-issuer-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

# only the two documents below are public
resource "aws_s3_bucket_public_access_block" "tck_issuer" {
  bucket                  = aws_s3_bucket.tck_issuer.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "tck_issuer" {
  bucket = aws_s3_bucket.tck_issuer.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicIssuerDocuments"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource = [
        "${aws_s3_bucket.tck_issuer.arn}/.well-known/openid-configuration",
        "${aws_s3_bucket.tck_issuer.arn}/jwks.json",
      ]
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.tck_issuer]
}

locals {
  tck_issuer = "https://${aws_s3_bucket.tck_issuer.bucket_regional_domain_name}"
}

resource "aws_s3_object" "tck_issuer_discovery" {
  bucket       = aws_s3_bucket.tck_issuer.id
  key          = ".well-known/openid-configuration"
  content_type = "application/json"
  content      = jsonencode({ issuer = local.tck_issuer, jwks_uri = "${local.tck_issuer}/jwks.json" })
}

resource "aws_s3_object" "tck_issuer_jwks" {
  bucket       = aws_s3_bucket.tck_issuer.id
  key          = "jwks.json"
  content_type = "application/json"
  content      = var.tck_jwks
}

# The issuer reaches the functions and the authorizer only once its documents are public.
resource "terraform_data" "tck_issuer" {
  input      = local.tck_issuer
  depends_on = [aws_s3_bucket_policy.tck_issuer, aws_s3_object.tck_issuer_discovery, aws_s3_object.tck_issuer_jwks]
}

output "tck_issuer" {
  value = local.tck_issuer
}
