# Function for API Gateway HTTP trigger (path parameter extraction)
resource "google_cloudfunctions2_function" "${handlerInfo.functionId}_api" {
  name        = "${handlerInfo.functionId}-api"
  location    = var.gcp_region
  description = "API Cloud function for ${handlerInfo.functionId}"

  build_config {
    runtime     = "java21"
    entry_point = "${handlerWrapperFullyQualifiedName}"
    source {
      storage_source {
        bucket = google_storage_bucket.function_bucket.name
        object = google_storage_bucket_object.shared_function_archive.name
      }
    }
  }

  service_config {
    service_account_email = google_service_account.identity_${handlerInfo.identity}.email
    available_memory   = "${handlerInfo.memory}Mi"
    timeout_seconds    = ${handlerInfo.timeout}
    min_instance_count = ${handlerInfo.minInstances?c}
    # the same environment as the function itself (function.ftl)
    environment_variables = local.cloudhopper_function_${handlerInfo.functionId}_environment
<#if handlerInfo.configProperties?has_content>

    dynamic "secret_environment_variables" {
      for_each = local.cloudhopper_function_${handlerInfo.functionId}_secrets
      content {
        key        = upper(replace(secret_environment_variables.key, "/[^A-Za-z0-9]/", "_"))
        project_id = length(split("/", secret_environment_variables.value)) == 4 ? split("/", secret_environment_variables.value)[1] : var.gcp_project_id
        secret     = element(split("/", secret_environment_variables.value), length(split("/", secret_environment_variables.value)) - 1)
        version    = "latest"
      }
    }
</#if>
  }
<#if handlerInfo.configProperties?has_content>

  # Cloud Functions checks the access to the secrets when it deploys the function
  depends_on = [google_secret_manager_secret_iam_member.identity_${handlerInfo.identity}_secrets]
</#if>
}

# IAM permission for the API function
resource "google_cloudfunctions2_function_iam_member" "${handlerInfo.functionId}_api_invoker" {
  project        = google_cloudfunctions2_function.${handlerInfo.functionId}_api.project
  location       = google_cloudfunctions2_function.${handlerInfo.functionId}.location
  cloud_function = google_cloudfunctions2_function.${handlerInfo.functionId}_api.name

  role   = "roles/cloudfunctions.invoker"
  member = "allUsers"
}


output "${handlerInfo.functionId}_url" {
  value = "https://${"$"}{google_api_gateway_gateway.main.default_hostname}${path}"
}