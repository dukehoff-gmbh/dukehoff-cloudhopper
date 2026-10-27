#!/bin/bash

set -euo pipefail

PROJECT_ID="cloudhopper-tck"
API_ID="cloudhopper-tck-api"
CONFIG_ID="v1"

echo "🔍 Checking deployed API Gateway routing for project: $PROJECT_ID"

# Step 1: Get serviceConfigId from API Gateway config
echo "➡️  Fetching serviceConfigId from API config..."
SERVICE_CONFIG_ID=$(gcloud api-gateway api-configs describe "$CONFIG_ID" \
  --api="$API_ID" \
  --project="$PROJECT_ID" \
  --format="value(serviceConfigId)")

if [[ -z "$SERVICE_CONFIG_ID" ]]; then
  echo "❌ Could not retrieve serviceConfigId for config $CONFIG_ID"
  exit 1
fi

echo "✅ Found serviceConfigId: $SERVICE_CONFIG_ID"

# Step 2: Find the managed service name
echo "➡️  Looking for managed service name..."
MANAGED_SERVICE=$(gcloud endpoints services list \
  --project="$PROJECT_ID" \
  --format="value(serviceName)" | grep "$API_ID" | head -n 1)

if [[ -z "$MANAGED_SERVICE" ]]; then
  echo "❌ Could not find managed service name for $API_ID"
  echo "   You may need to wait a minute after deployment."
  exit 1
fi

echo "✅ Found managed service: $MANAGED_SERVICE"

# Step 3: Download and parse the service config
echo "➡️  Fetching and decoding service config: $SERVICE_CONFIG_ID"
gcloud endpoints configs describe "$SERVICE_CONFIG_ID" \
  --service="$MANAGED_SERVICE" \
  --project="$PROJECT_ID" \
  --format=json > parsed-config.json

# Step 4: Print all HTTP rules
echo
echo "📦 Deployed HTTP Routes:"
jq -r '.http.rules[] | 
  "\(.selector)  →  " + 
  (if has("get") then "GET \(.get)" 
   elif has("post") then "POST \(.post)" 
   elif has("put") then "PUT \(.put)" 
   elif has("delete") then "DELETE \(.delete)" 
   else "UNKNOWN" end)' parsed-config.json
