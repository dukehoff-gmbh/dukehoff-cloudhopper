<#if eventSource == "STORAGE">
<#assign fid = handlerInfo.functionId>
<#if eventCreate>
# Cloud Storage bucket names are global across all projects. Terraform does not delete a bucket
# that still contains objects. The Eventarc trigger must be in the bucket's location.
resource "google_storage_bucket" "${fid}_bucket" {
  name                        = "${eventName}"
  location                    = var.gcp_region
  uniform_bucket_level_access = true
}
<#assign bucketName = "google_storage_bucket.${fid}_bucket.name">
<#else>
# Existing bucket "${eventName}"; it must be in the region var.gcp_region.
data "google_storage_bucket" "${fid}_bucket" {
  name = "${eventName}"
}
<#assign bucketName = "data.google_storage_bucket.${fid}_bucket.name">
</#if>

# Delivers an event for every object created or overwritten in the bucket to the function's
# Cloud Run service. Requires the Cloud Storage service agent to have roles/pubsub.publisher and
# the trigger's service account roles/eventarc.eventReceiver (see the GCP prerequisites).
resource "google_eventarc_trigger" "${fid}_storage" {
  name     = "${fid?lower_case}-storage"
  location = var.gcp_region

  matching_criteria {
    attribute = "type"
    value     = "google.cloud.storage.object.v1.finalized"
  }
  matching_criteria {
    attribute = "bucket"
    value     = ${bucketName}
  }

  destination {
    cloud_run_service {
      # service_config[0].service is the full resource name; Eventarc wants the service name only
      service = element(reverse(split("/", google_cloudfunctions2_function.${fid}.service_config[0].service)), 0)
      region  = var.gcp_region
    }
  }

  service_account = var.cloudscheduler_service_account_email
}

output "${fid}_bucket" {
  value = ${bucketName}
}
</#if>
