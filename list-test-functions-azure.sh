#!/bin/bash

# Required parameters
RESOURCE_GROUP="my-cloudhopper-project-test-rg"
FUNCTION_APP="my-shared-function-app"

echo "🔍 Listing Azure Function trigger URLs for Function App: $FUNCTION_APP"

# List all functions inside the Function App
FUNCTIONS=$(az functionapp function list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$FUNCTION_APP" \
  --query "[].name" \
  --output tsv)

if [ -z "$FUNCTIONS" ]; then
  echo "❌ No functions found."
  exit 1
fi

# Loop over each function and display the invoke URL
for FUNCTION in $FUNCTIONS; do
  echo "----------------------------------------"
  echo "🔹 Function: $FUNCTION"
  URL=$(az functionapp function show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$FUNCTION_APP" \
    --function-name "$FUNCTION" \
    --query "invoke_url_template" \
    --output tsv)

  if [ -n "$URL" ]; then
    echo "✅ Trigger URL: $URL"
  else
    echo "⚠️  No HTTP trigger URL found (maybe not an HTTP-triggered function?)"
  fi
done

echo "✅ Done listing all trigger URLs."

