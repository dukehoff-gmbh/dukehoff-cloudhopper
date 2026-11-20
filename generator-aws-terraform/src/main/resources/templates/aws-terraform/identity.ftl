<#assign identity = handlerInfo.identity>
# The execution role of the functions with identity "${identity}". It has the logging permissions
# and, attached by the trigger templates, the permissions of the functions' own triggers. Attach
# further permissions in your own .tf files to aws_iam_role.identity_${identity}.
# The role name stays the same as long as the identity does; foreign trust policies can refer to it.
resource "aws_iam_role" "identity_${identity}" {
  name = "${"$"}{var.cloudhopper_name_prefix}${"$"}{var.project_name}-${identity}"

  lifecycle {
    precondition {
      condition     = length("${"$"}{var.cloudhopper_name_prefix}${"$"}{var.project_name}-${identity}") <= 64
      error_message = "The IAM role name of identity \"${identity}\" (cloudhopper_name_prefix + project_name + \"-${identity}\") is longer than the 64 characters IAM allows. Shorten project_name or the prefix, or give the function a shorter identity with @Function(identity = \"...\")."
    }
  }

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "identity_${identity}_logs" {
  role       = aws_iam_role.identity_${identity}.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

output "identity_${identity}_role_name" {
  value = aws_iam_role.identity_${identity}.name
}

output "identity_${identity}_role_arn" {
  value = aws_iam_role.identity_${identity}.arn
}
