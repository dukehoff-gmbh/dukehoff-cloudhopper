<#-- with SnapStart, the triggers invoke the published version through the alias "live" -->
<#-- SnapStart exists only for the Java runtime, not for a native image (-Aruntime=native) -->
<#assign snapStart = (runtime!"jvm") != "native" && (handlerInfo.extensionsMap["x-aws-snapstart"]!"") == "PublishedVersions">
<#assign lambdaTarget = snapStart?then("aws_lambda_alias.${handlerInfo.functionId}_live", "aws_lambda_function.${handlerInfo.functionId}")>
<#if eventSource == "STORAGE">
<#assign fid = handlerInfo.functionId>
<#if eventCreate>
# S3 bucket names are global across all AWS accounts. Terraform does not delete a bucket that
# still contains objects.
resource "aws_s3_bucket" "${fid}_bucket" {
  bucket = "${"$"}{var.cloudhopper_name_prefix}${eventName}"
}
<#assign bucketId = "aws_s3_bucket.${fid}_bucket.id">
<#assign bucketArn = "aws_s3_bucket.${fid}_bucket.arn">
<#else>
data "aws_s3_bucket" "${fid}_bucket" {
  bucket = "${eventName}"
}
<#assign bucketId = "data.aws_s3_bucket.${fid}_bucket.id">
<#assign bucketArn = "data.aws_s3_bucket.${fid}_bucket.arn">
</#if>

resource "aws_lambda_permission" "${fid}_storage" {
  # a prefix, so that a new permission can exist next to the one it replaces
  statement_id_prefix = "AllowS3Invoke-"
  action         = "lambda:InvokeFunction"
  function_name  = aws_lambda_function.${fid}.function_name
<#if snapStart>
  qualifier      = aws_lambda_alias.${fid}_live.name
</#if>
  principal      = "s3.amazonaws.com"
  source_arn     = ${bucketArn}
  source_account = data.aws_caller_identity.current.account_id

  # switching SnapStart or the runtime moves the permission from the alias to the function (or
  # back): the new one is created before the old one is removed, so no request is refused
  lifecycle {
    create_before_destroy = true
  }
}

# Invokes the function for every object created in the bucket. S3 keeps one notification
# configuration per bucket: this resource replaces any notifications the bucket already has.
resource "aws_s3_bucket_notification" "${fid}_storage" {
  bucket = ${bucketId}

  lambda_function {
    lambda_function_arn = ${lambdaTarget}.arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.${fid}_storage]
}

output "${fid}_bucket" {
  value = ${bucketId}
}
</#if>
