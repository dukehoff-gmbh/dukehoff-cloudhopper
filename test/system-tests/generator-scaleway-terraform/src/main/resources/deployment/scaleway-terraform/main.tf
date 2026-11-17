# TCK setup next to the generated Terraform. Credentials and the project come from
# ~/.config/scw/config.yaml or SCW_ACCESS_KEY, SCW_SECRET_KEY and SCW_DEFAULT_PROJECT_ID.
provider "scaleway" {
  region = "fr-par"
  zone   = "fr-par-1"
}

variable "registry_namespace" {
  description = "Registry namespace for the TCK image; the name is unique across Scaleway"
  type        = string
  default     = "dukehoff-cloudhopper-tck"
}

# created first (apply -target) so that the binding can push the image before the container exists
resource "scaleway_registry_namespace" "tck" {
  name      = var.registry_namespace
  is_public = false
}

# read-only access to the logs, for fetchLogs
resource "scaleway_cockpit_token" "tck" {
  name = "${var.container_name}-logs"

  scopes {
    query_logs = true
  }
}

data "scaleway_cockpit_source" "logs" {
  origin = "scaleway"
  type   = "logs"
}

output "registry_endpoint" {
  value = scaleway_registry_namespace.tck.endpoint
}

output "logs_url" {
  value = data.scaleway_cockpit_source.logs.url
}

output "logs_token" {
  value     = scaleway_cockpit_token.tck.secret_key
  sensitive = true
}

# the binding sends the queue messages with these credentials
output "queue_access_key" {
  value     = scaleway_mnq_sqs_credentials.cloudhopper.access_key
  sensitive = true
}

output "queue_secret_key" {
  value     = scaleway_mnq_sqs_credentials.cloudhopper.secret_key
  sensitive = true
}

output "queue_endpoint" {
  value = scaleway_mnq_sqs.cloudhopper.endpoint
}
