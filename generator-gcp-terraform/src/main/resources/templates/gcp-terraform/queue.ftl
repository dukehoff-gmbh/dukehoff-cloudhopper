<#if eventSource == "QUEUE">
<#assign fid = handlerInfo.functionId>
<#if eventCreate>
resource "google_pubsub_topic" "${fid}_topic" {
  name = "${eventName}"
}
<#assign topicId = "google_pubsub_topic.${fid}_topic.id">
<#else>
data "google_pubsub_topic" "${fid}_topic" {
  name = "${eventName}"
}
<#assign topicId = "data.google_pubsub_topic.${fid}_topic.id">
</#if>

# Delivers each message on the topic to the function's Cloud Run service. The service account
# already has roles/run.invoker on it (see the function resource). A failed invocation is retried.
resource "google_eventarc_trigger" "${fid}_event" {
  name     = "${fid?lower_case}-event"
  location = var.gcp_region

  matching_criteria {
    attribute = "type"
    value     = "google.cloud.pubsub.topic.v1.messagePublished"
  }

  destination {
    cloud_run_service {
      # service_config[0].service is the full resource name; Eventarc wants the service name only
      service = element(reverse(split("/", google_cloudfunctions2_function.${fid}.service_config[0].service)), 0)
      region  = var.gcp_region
    }
  }

  transport {
    pubsub {
      topic = ${topicId}
    }
  }

  service_account = var.cloudscheduler_service_account_email
}

output "${fid}_topic" {
  value = ${topicId}
}
</#if>
