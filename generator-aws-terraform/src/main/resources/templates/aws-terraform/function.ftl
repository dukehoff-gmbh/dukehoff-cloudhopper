<#-- the queues this function sends to (@Queue): the project's queues carry the name prefix -->
<#function queueName q><#return q.existing?then(q.name, r"${var.cloudhopper_name_prefix}" + q.name)></#function>
<#-- the properties of this function, and those without a default -->
<#assign configNames><#list handlerInfo.configProperties as p>"${p.name}"<#sep>, </#sep></#list></#assign>
<#assign requiredNames><#list handlerInfo.configProperties?filter(p -> p.required) as p>"${p.name}"<#sep>, </#sep></#list></#assign>
<#-- with SnapStart, the triggers invoke the published version through the alias "live" -->
<#-- with -Aruntime=native, the functions run a native image on the custom runtime: one
     executable for all functions, built for the architecture -AnativeArchitecture -->
<#assign native = (runtime!"jvm") == "native">
<#assign artifact = native?then("${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}-native.zip", "${handlerInfo.artifactId}-${handlerInfo.version}-${handlerInfo.classifier}.jar")>
<#-- SnapStart exists only for the Java runtime; a native image starts fast without it -->
<#assign snapStart = !native && (handlerInfo.extensionsMap["x-aws-snapstart"]!"") == "PublishedVersions">
<#assign lambdaTarget = snapStart?then("aws_lambda_alias.${handlerInfo.functionId}_live", "aws_lambda_function.${handlerInfo.functionId}")>
resource "aws_lambda_function" "${handlerInfo.functionId}" {
  function_name = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}"
  role          = aws_iam_role.identity_${handlerInfo.identity}.arn
  handler       = "${handlerWrapperFullyQualifiedName}$Auto::handleRequest"
  
  s3_bucket     = aws_s3_bucket.lambda_artifacts.bucket
  s3_key        = aws_s3_object.lambda_jar.key
  source_code_hash = filebase64sha256("${"$"}{local.cloudhopper_artifact_dir}/${artifact}")

  timeout = ${handlerInfo.timeout}
  memory_size = ${handlerInfo.memory}
  <#-- look up the AWS architecture extension (falls back to empty string) -->
  <#assign awsArch = handlerInfo.extensionsMap["x-aws-architecture"]!>
<#if native>
  # native image (bootstrap): eu.cloudhopper.mc.provider.aws.NativeRuntime creates the handler
  runtime       = "provided.al2023"
  architectures = ["${nativeArchitecture}"]
<#else>
  runtime = "java21"
  <#if awsArch?has_content>
  architectures = ["${awsArch}"]
  </#if>
</#if>
  depends_on = [aws_cloudwatch_log_group.${handlerInfo.functionId}_logs]
<#if snapStart>

  # every deployment publishes a version; Lambda takes the SnapStart snapshot of it
  publish = true
  snap_start {
    apply_on = "PublishedVersions"
  }
</#if>

  environment {
    variables = merge({
      # lets the runtime report bucket and queue names without the prefix
      CLOUDHOPPER_NAME_PREFIX = var.cloudhopper_name_prefix
    }, { for name, value in local.cloudhopper_config : upper(replace(name, "/[^A-Za-z0-9]/", "_")) => value if contains([${configNames}], name) }, {
<#list handlerInfo.publishedQueues as q>
      # @Queue(name = "${q.name}")
      ${q.urlEnvironmentName} = "https://sqs.${"$"}{data.aws_region.cloudhopper.region}.amazonaws.com/${"$"}{data.aws_caller_identity.current.account_id}/${queueName(q)}"
</#list>
<#list handlerInfo.buckets as b>
      # @Bucket(name = "${b.name}"), defined in api_buckets.tf
      ${b.nameEnvironmentName} = local.cloudhopper_bucket_${b.id}
</#list>
<#list handlerInfo.tables as t>
      # @Table(name = "${t.name}"), defined in api_tables.tf
      ${t.nameEnvironmentName} = local.cloudhopper_table_${t.id}
</#list>
<#if configNames?has_content>
      # the secrets of the properties the function reads, read by the runtime from Secrets Manager
      CLOUDHOPPER_SECRETS = jsonencode({ for name, ref in local.cloudhopper_secrets : name => ref if contains([${configNames}], name) })
</#if>
    })
  }
<#if configNames?has_content>
  lifecycle {
<#if requiredNames?has_content>
    precondition {
      condition     = alltrue([for name in [${requiredNames}] : contains(keys(local.cloudhopper_config), name) || contains(keys(local.cloudhopper_secrets), name)])
      error_message = "Function ${handlerInfo.functionId} reads configuration properties without a default that neither cloudhopper_config, cloudhopper_secrets nor cloudhopper_extra_config/cloudhopper_extra_secrets sets: ${"$"}{join(", ", [for name in [${requiredNames}] : name if !contains(keys(local.cloudhopper_config), name) && !contains(keys(local.cloudhopper_secrets), name)])}"
    }
</#if>
    precondition {
      condition     = length(local.cloudhopper_duplicate_properties) == 0
      error_message = "Properties are set more than once in cloudhopper_config, cloudhopper_secrets, cloudhopper_extra_config and cloudhopper_extra_secrets: ${"$"}{join(", ", local.cloudhopper_duplicate_properties)}"
    }
  }
</#if>
}
<#if configNames?has_content>

# Read access to the secrets the function reads (cloudhopper_secrets), granted to its identity.
# A secret given by name matches its ARN with the random suffix Secrets Manager appends.
locals {
  ${handlerInfo.functionId}_secret_arns = [for name, ref in local.cloudhopper_secrets :
    startswith(ref, "arn:") ? ref : "arn:aws:secretsmanager:${"$"}{data.aws_region.cloudhopper.region}:${"$"}{data.aws_caller_identity.current.account_id}:secret:${"$"}{ref}-??????"
  if contains([${configNames}], name)]
}

resource "aws_iam_role_policy" "${handlerInfo.functionId}_secrets" {
  count = length(local.${handlerInfo.functionId}_secret_arns) > 0 ? 1 : 0
  name  = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}-secrets"
  role  = aws_iam_role.identity_${handlerInfo.identity}.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = local.${handlerInfo.functionId}_secret_arns
    }]
  })
}
</#if>

# The log group Lambda writes to, created before the function so that Lambda does not
# create it on the first invocation (without retention, outside of Terraform's state).
# Deployments made before this group was managed here already have it; import it once:
#   terraform import 'aws_cloudwatch_log_group.${handlerInfo.functionId}_logs' /aws/lambda/<function name>
resource "aws_cloudwatch_log_group" "${handlerInfo.functionId}_logs" {
  name              = "/aws/lambda/${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}"
  retention_in_days = var.log_retention_days
}
<#if snapStart>

# Lambda creates the SnapStart snapshot after publishing; until then the version is Pending
# and every invocation fails. The alias moves to a new version only after this wait, so the
# triggers keep invoking the previous, active version during a deployment. Needs the AWS CLI
# with the same credentials as Terraform.
resource "terraform_data" "${handlerInfo.functionId}_version_active" {
  input            = aws_lambda_function.${handlerInfo.functionId}.version
  # a new version replaces this resource, which runs the wait again
  triggers_replace = aws_lambda_function.${handlerInfo.functionId}.version

  provisioner "local-exec" {
    command = "aws lambda wait published-version-active --region ${"$"}{aws_lambda_function.${handlerInfo.functionId}.region} --function-name ${"$"}{aws_lambda_function.${handlerInfo.functionId}.function_name} --qualifier ${"$"}{aws_lambda_function.${handlerInfo.functionId}.version}"
  }
}

# The triggers invoke the latest active version through this alias, so that new instances
# start from its SnapStart snapshot.
resource "aws_lambda_alias" "${handlerInfo.functionId}_live" {
  name             = "live"
  function_name    = aws_lambda_function.${handlerInfo.functionId}.function_name
  function_version = terraform_data.${handlerInfo.functionId}_version_active.output
}
</#if>
<#if handlerInfo.publishedQueues?has_content>

# The queues the function sends to (@Queue); granted to its identity.
resource "aws_iam_role_policy" "${handlerInfo.functionId}_publish" {
  name = "${"$"}{var.cloudhopper_name_prefix}${handlerInfo.functionId}-publish"
  role = aws_iam_role.identity_${handlerInfo.identity}.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:SendMessage"]
      Resource = [
<#list handlerInfo.publishedQueues as q>
        "arn:aws:sqs:${"$"}{data.aws_region.cloudhopper.region}:${"$"}{data.aws_caller_identity.current.account_id}:${queueName(q)}"<#sep>,</#sep>
</#list>
      ]
    }]
  })
}
</#if>
