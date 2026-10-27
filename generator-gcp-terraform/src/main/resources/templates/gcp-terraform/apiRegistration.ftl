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
    available_memory   = "${handlerInfo.memory}Mi"
    timeout_seconds    = ${handlerInfo.timeout}
    environment_variables = {
      FUNCTION_MEMORY_MB       = ${handlerInfo.memory}
      FUNCTION_TIMEOUT_SECONDS = ${handlerInfo.timeout}
    }
  }
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