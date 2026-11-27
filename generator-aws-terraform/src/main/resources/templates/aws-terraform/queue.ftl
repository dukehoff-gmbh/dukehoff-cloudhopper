<#-- with SnapStart, the triggers invoke the published version through the alias "live" -->
<#-- SnapStart exists only for the Java runtime, not for a native image (-Aruntime=native) -->
<#assign snapStart = (runtime!"jvm") != "native" && (handlerInfo.extensionsMap["x-aws-snapstart"]!"") == "PublishedVersions">
<#assign lambdaTarget = snapStart?then("aws_lambda_alias.${handlerInfo.functionId}_live", "aws_lambda_function.${handlerInfo.functionId}")>
<#if eventSource == "QUEUE">
<#assign fid = handlerInfo.functionId>
<#-- Lambda requires the queue's visibility timeout to be at least the function timeout; AWS recommends six times it -->
<#assign visibility = handlerInfo.timeout * 6>
<#if visibility < 30><#assign visibility = 30></#if>
<#if eventCreate>
resource "aws_sqs_queue" "${fid}_queue" {
  name                       = "${"$"}{var.cloudhopper_name_prefix}${eventName}"
  visibility_timeout_seconds = ${visibility?c}
}
<#assign queueArn = "aws_sqs_queue.${fid}_queue.arn">
<#assign queueUrl = "aws_sqs_queue.${fid}_queue.url">
<#else>
# Existing queue "${eventName}"; its visibility timeout must be at least the function timeout.
data "aws_sqs_queue" "${fid}_queue" {
  name = "${eventName}"
}
<#assign queueArn = "data.aws_sqs_queue.${fid}_queue.arn">
<#assign queueUrl = "data.aws_sqs_queue.${fid}_queue.url">
</#if>

resource "aws_iam_role_policy" "${fid}_queue_access" {
  name = "${"$"}{var.cloudhopper_name_prefix}${fid}-queue-access"
  role = aws_iam_role.identity_${handlerInfo.identity}.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:ChangeMessageVisibility"]
      Resource = ${queueArn}
    }]
  })
}

# One message per invocation; failed messages are reported individually and delivered again.
resource "aws_lambda_event_source_mapping" "${fid}_queue" {
  event_source_arn        = ${queueArn}
  function_name           = ${lambdaTarget}.arn
  batch_size              = 1
  function_response_types = ["ReportBatchItemFailures"]

  depends_on = [aws_iam_role_policy.${fid}_queue_access]
}

output "${fid}_queue_url" {
  value = ${queueUrl}
}
</#if>
