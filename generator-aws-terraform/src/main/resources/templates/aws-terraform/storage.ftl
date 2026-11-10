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
  statement_id   = "AllowS3Invoke"
  action         = "lambda:InvokeFunction"
  function_name  = aws_lambda_function.${fid}.function_name
  principal      = "s3.amazonaws.com"
  source_arn     = ${bucketArn}
  source_account = data.aws_caller_identity.current.account_id
}

# Invokes the function for every object created in the bucket. S3 keeps one notification
# configuration per bucket: this resource replaces any notifications the bucket already has.
resource "aws_s3_bucket_notification" "${fid}_storage" {
  bucket = ${bucketId}

  lambda_function {
    lambda_function_arn = aws_lambda_function.${fid}.arn
    events              = ["s3:ObjectCreated:*"]
  }

  depends_on = [aws_lambda_permission.${fid}_storage]
}

output "${fid}_bucket" {
  value = ${bucketId}
}
</#if>
