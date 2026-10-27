#!/bin/bash
set -e  # Exit immediately on any error

# Configuration
RESOURCE_GROUP_NAME="my-cloudhopper-project-test-rg"
FUNCTION_APP_NAME="my-shared-function-app"
STORAGE_ACCOUNT_NAME="mycloudhopperprojecttest"
BLOB_CONTAINER_NAME="function-container"
ZIP_FILE_NAME="system-tests-generator-azure-terraform-1.0-SNAPSHOT-azure.zip"

# 1. Get Storage Account Key
echo "🔑 Fetching Storage Account Key..."
STORAGE_ACCOUNT_KEY=$(az storage account keys list \
    --account-name "$STORAGE_ACCOUNT_NAME" \
    --resource-group "$RESOURCE_GROUP_NAME" \
    --query "[0].value" \
    --output tsv)

# 2. Generate SAS Token for ZIP
echo "🔒 Generating SAS token for deployment package..."
SAS_TOKEN=$(az storage blob generate-sas \
    --account-name "$STORAGE_ACCOUNT_NAME" \
    --account-key "$STORAGE_ACCOUNT_KEY" \
    --container-name "$BLOB_CONTAINER_NAME" \
    --name "$ZIP_FILE_NAME" \
    --permissions r \
    --expiry $(date -u -d "1 day" '+%Y-%m-%dT%H:%MZ') \
    --output tsv)

# 3. Build the full blob URL with SAS token
BLOB_URL="https://${STORAGE_ACCOUNT_NAME}.blob.core.windows.net/${BLOB_CONTAINER_NAME}/${ZIP_FILE_NAME}?${SAS_TOKEN}"

# 4. Update Function App settings
echo "🔧 Updating WEBSITE_RUN_FROM_PACKAGE setting..."
az functionapp config appsettings set \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RESOURCE_GROUP_NAME" \
  --settings WEBSITE_RUN_FROM_PACKAGE="$BLOB_URL"

# 5. Restart the Function App
echo "🔄 Restarting Function App..."
az functionapp restart --name "$FUNCTION_APP_NAME" --resource-group "$RESOURCE_GROUP_NAME"

echo "✅ Function App updated and restarted successfully!"