<#assign identity = handlerInfo.identity>
# The service account of the functions with identity "${identity}". The functions run as this
# account; it has only the permissions the generated Terraform grants for their own resources
# (e.g. roles/pubsub.publisher on the topics they send to). Grant further permissions in your own
# .tf files to google_service_account.identity_${identity}.member.
# Service account IDs have 6 to 30 characters: the ID is the project name and the identity,
# shortened, with a hash of both.
locals {
  identity_${identity}_base = trim(replace(lower("${"$"}{var.project_name}-${identity}"), "/[^a-z0-9-]/", "-"), "-")
}

resource "google_service_account" "identity_${identity}" {
  account_id   = "${"$"}{can(regex("^[a-z]", local.identity_${identity}_base)) ? "" : "c"}${"$"}{trim(substr(local.identity_${identity}_base, 0, 22), "-")}-${"$"}{substr(sha1(local.identity_${identity}_base), 0, 6)}"
  display_name = "${"$"}{var.project_name} ${identity}"
  description  = "Cloudhopper identity ${identity} of ${"$"}{var.project_name}"
}

output "identity_${identity}_email" {
  value = google_service_account.identity_${identity}.email
}
