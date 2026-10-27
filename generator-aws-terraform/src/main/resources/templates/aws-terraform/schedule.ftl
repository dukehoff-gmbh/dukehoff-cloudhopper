resource "aws_cloudwatch_event_rule" "${handlerInfo.functionId}_schedule" {
  name                = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}-schedule"
<#assign parts = scheduleExpression?split(" ")>
<#if parts?size == 5>
  <#-- convert 5-part UNIX cron to 6-part AWS cron: replace weekday with ? and add * for year -->
  <#assign awsCron = parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " ? *">
<#else>
  <#-- assume it's already a valid AWS cron expression -->
  <#assign awsCron = scheduleExpression>
</#if>

schedule_expression = "cron(${awsCron})"
}

resource "aws_cloudwatch_event_target" "${handlerInfo.functionId}_target" {
  rule      = aws_cloudwatch_event_rule.${handlerInfo.functionId}_schedule.name
  target_id = "${handlerInfo.functionId}-lambda"
  arn       = aws_lambda_function.${handlerInfo.functionId}.arn
}

resource "aws_lambda_permission" "${handlerInfo.functionId}_invoke_permission" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.${handlerInfo.functionId}.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.${handlerInfo.functionId}_schedule.arn
}
