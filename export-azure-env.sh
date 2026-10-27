#!/bin/bash
set -e

AZURE_PROFILE="$HOME/.azure/azureProfile.json"

if [ ! -f "$AZURE_PROFILE" ]; then
  echo "❌ No azureProfile.json found at $AZURE_PROFILE"
  exit 1
fi

# Extract first subscription with a valid subscriptionId
SUBSCRIPTION_ID=$(jq -r '.subscriptions[] | select(.subscriptionId != null) | .subscriptionId' "$AZURE_PROFILE" | head -n 1)
TENANT_ID=$(jq -r '.subscriptions[] | select(.tenantId != null) | .tenantId' "$AZURE_PROFILE" | head -n 1)

if [ -z "$SUBSCRIPTION_ID" ]; then
  echo "❌ No valid subscriptionId found in $AZURE_PROFILE"
  echo "👉 Run 'az login' and/or 'az account set --subscription <id>'"
  exit 1
fi

echo "✅ Extracted tenantId=$TENANT_ID and subscriptionId=$SUBSCRIPTION_ID"

# Export for Terraform Azure Provider
export ARM_SUBSCRIPTION_ID="$SUBSCRIPTION_ID"
export ARM_TENANT_ID="$TENANT_ID"

echo "ℹ️ Set ARM_SUBSCRIPTION_ID and ARM_TENANT_ID from Azure CLI profile"
echo "⚠️ You still need to set ARM_CLIENT_ID and ARM_CLIENT_SECRET manually (service principal)"
