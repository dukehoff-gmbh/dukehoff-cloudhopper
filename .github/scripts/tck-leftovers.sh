#!/usr/bin/env bash
# Lists cloud resources that a TCK run left behind. Read-only: it deletes nothing.
# Prints every finding and exits 1 if there is any, so the scheduled workflow fails.
#
# What counts as a TCK resource:
#   AWS (shared account): resources tagged cloudhopper-purpose=tck (default_tags of the TCK's main.tf)
#   Azure: the TCK's resource group
#   GCP: functions, gateways, scheduler jobs, Eventarc triggers, topics, buckets and Firestore
#        databases other than (default) in the TCK project
#        (buckets that Cloud Functions manage itself, gcf-*, are not counted)
#   Scaleway: container and registry namespaces and Serverless SQL Databases in the TCK project
#
# Each cloud is checked only when its credentials are present.
set -uo pipefail

# a file, so that findings from command substitutions (subshells) count too
findings_file=$(mktemp)
trap 'rm -f "$findings_file"' EXIT
report() {
  echo "::warning::$1" >&2
  echo "$1" >> "$findings_file"
}

# Runs a listing command; a failing listing is a finding too, so that missing permissions or
# expired credentials do not pass as "nothing left".
listing() {
  local what="$1"; shift
  local out
  if ! out=$("$@" 2>&1); then
    report "could not list $what: $(echo "$out" | tail -1)"
    return
  fi
  echo "$out"
}

if [ -n "${AWS_ACCESS_KEY_ID:-}" ]; then
  echo "▶ AWS (${AWS_REGION:-default region})"
  for arn in $(listing "AWS tagged resources" aws resourcegroupstaggingapi get-resources \
      --tag-filters Key=cloudhopper-purpose,Values=tck \
      --query 'ResourceTagMappingList[].ResourceARN' --output text); do
    report "AWS: $arn"
  done
fi

if [ -n "${ARM_CLIENT_ID:-}" ]; then
  echo "▶ Azure"
  az login --service-principal -u "$ARM_CLIENT_ID" -p "$ARM_CLIENT_SECRET" --tenant "$ARM_TENANT_ID" --output none
  az account set --subscription "$ARM_SUBSCRIPTION_ID"
  group="${AZURE_TCK_RESOURCE_GROUP:-my-cloudhopper-project-test-rg}"
  if [ "$(az group exists --name "$group")" = "true" ]; then
    for id in $(az resource list --resource-group "$group" --query '[].id' --output tsv); do report "Azure: $id"; done
    report "Azure: resource group $group"
  fi
fi

if [ -n "${GCP_KEY_FILE:-}" ]; then
  project="${GCP_PROJECT_ID:-cloudhopper-tck}"
  region="${GCP_REGION:-us-central1}"
  echo "▶ GCP ($project, $region)"
  gcloud auth activate-service-account --key-file="$GCP_KEY_FILE" --quiet
  list() { listing "GCP $1 $2" gcloud "$@" --project "$project" --format='value(name)'; }
  for name in $(list functions list --v2 --regions "$region"); do report "GCP function: $name"; done
  for name in $(list api-gateway gateways list --location "$region"); do report "GCP gateway: $name"; done
  for name in $(list scheduler jobs list --location "$region"); do report "GCP scheduler job: $name"; done
  for name in $(list eventarc triggers list --location "$region"); do report "GCP Eventarc trigger: $name"; done
  for name in $(list pubsub topics list); do report "GCP topic: $name"; done
  for name in $(list firestore databases list); do
    case "$name" in */"(default)") ;; *) report "GCP Firestore database: $name" ;; esac
  done
  for name in $(list storage buckets list); do
    case "$name" in gcf-*) ;; *) report "GCP bucket: $name" ;; esac
  done
fi

if [ -n "${SCW_SECRET_KEY:-}" ]; then
  echo "▶ Scaleway ($SCW_DEFAULT_PROJECT_ID)"
  scw_list() {
    local body
    body=$(listing "Scaleway $1" curl -sSf -H "X-Auth-Token: $SCW_SECRET_KEY" \
      "https://api.scaleway.com/$1?project_id=$SCW_DEFAULT_PROJECT_ID&page_size=100") || return
    echo "$body" | jq -r "${2:-.namespaces}[]?.name" 2>/dev/null
  }
  for name in $(scw_list containers/v1beta1/regions/fr-par/namespaces); do report "Scaleway container namespace: $name"; done
  for name in $(scw_list registry/v1/regions/fr-par/namespaces); do report "Scaleway registry namespace: $name"; done
  for name in $(scw_list serverless-sqldb/v1alpha1/regions/fr-par/databases .databases); do report "Scaleway Serverless SQL Database: $name"; done
fi

findings=$(wc -l < "$findings_file" | tr -d ' ')
if [ "$findings" -gt 0 ]; then
  echo "❌ $findings finding(s): leftover TCK resources, or clouds that could not be checked"
  exit 1
fi
echo "✅ no leftover TCK resources"
