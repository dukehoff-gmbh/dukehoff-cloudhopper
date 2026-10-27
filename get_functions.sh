#!/bin/bash
set -euo pipefail

PROJECT_ID="cloudhopper-tck"
REGION="us-central1"
API_ID="cloudhopper-tck-api"
CONFIG_ID="v1"

echo "🔍 Project: $PROJECT_ID"
echo "🔹 Region: $REGION"
echo

# Step 1: List all deployed Cloud Functions
echo "🚀 Deployed Cloud Functions in $REGION:"
gcloud functions list \
  --project="$PROJECT_ID" \
  --regions="$REGION" \
  --format="table(name, status, httpsTrigger.url)"
echo

# Step 2: Get managed service name from config
SERVICE_NAME=$(gcloud endpoints services list \
  --project="$PROJECT_ID" \
  --format="value(serviceName)" | grep "$API_ID" | head -n 1)

if [[ -z "$SERVICE_NAME" ]]; then
  echo "❌ Could not find managed service name for $API_ID"
  exit 1
fi

# Step 3: Get serviceConfigId from API Gateway config
CONFIG_VERSION=$(gcloud api-gateway api-configs describe "$CONFIG_ID" \
  --api="$API_ID" \
  --project="$PROJECT_ID" \
  --format="value(serviceConfigId)")

echo "📦 API Gateway Config: $SERVICE_NAME / $CONFIG_VERSION"
echo

# Step 4: Extract HTTP paths
echo "📂 Deployed HTTP Paths (API Gateway):"
gcloud endpoints configs describe "$CONFIG_VERSION" \
  --service="$SERVICE_NAME" \
  --project="$PROJECT_ID" \
  --format="json" |
jq -r '.http.rules[] |
  (if has("get") then "GET " + .get
   elif has("post") then "POST " + .post
   elif has("put") then "PUT " + .put
   elif has("delete") then "DELETE " + .delete
   else "UNKNOWN METHOD" end)'

