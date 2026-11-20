<#-- the properties of this function, and those without a default -->
<#assign configNames><#list handlerInfo.configProperties as p>"${p.name}"<#sep>, </#sep></#list></#assign>
<#assign requiredNames><#list handlerInfo.configProperties?filter(p -> p.required) as p>"${p.name}"<#sep>, </#sep></#list></#assign>
# Function-specific resources

resource "google_cloudfunctions2_function" "${handlerInfo.functionId}" {
  name        = "${handlerInfo.functionId}"
  location    = var.gcp_region
  description = "Cloud function for ${handlerInfo.functionId}"

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
    min_instance_count = ${handlerInfo.minInstances?c}
    environment_variables = merge({
      FUNCTION_MEMORY_MB       = ${handlerInfo.memory}
      FUNCTION_TIMEOUT_SECONDS = ${handlerInfo.timeout}
    }, { for name, value in var.cloudhopper_config : upper(replace(name, "/[^A-Za-z0-9]/", "_")) => value if contains([${configNames}], name) })
  }
<#if requiredNames?has_content>
  lifecycle {
    precondition {
      condition     = alltrue([for name in [${requiredNames}] : contains(keys(var.cloudhopper_config), name)])
      error_message = "Function ${handlerInfo.functionId} reads configuration properties without a default that cloudhopper_config does not set: ${"$"}{join(", ", [for name in [${requiredNames}] : name if !contains(keys(var.cloudhopper_config), name)])}"
    }
  }
</#if>
}


resource "google_cloud_run_v2_service_iam_member" "${handlerInfo.functionId}_invoker" {
  project        = google_cloudfunctions2_function.${handlerInfo.functionId}.project
  location       = google_cloudfunctions2_function.${handlerInfo.functionId}.location
  name           = google_cloudfunctions2_function.${handlerInfo.functionId}.service_config[0].service

  role   = "roles/run.invoker"
  member = "serviceAccount:${"$"}{var.cloudscheduler_service_account_email}"
}


output "${handlerInfo.functionId}_direct_url" {
  value = google_cloudfunctions2_function.${handlerInfo.functionId}.service_config[0].uri
}

output "${handlerInfo.functionId}_min_instance_count" {
  value = google_cloudfunctions2_function.${handlerInfo.functionId}.service_config[0].min_instance_count
}
