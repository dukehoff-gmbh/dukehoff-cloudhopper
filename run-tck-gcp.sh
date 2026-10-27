#!/bin/bash
set -e
GCP_PROJECT_ID="cloudhopper-tck"
GCP_REGION="us-central1"

# Ensure GCP credentials are available
if [ -z "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
  echo "❌ GOOGLE_APPLICATION_CREDENTIALS is not set. Please export it to point to your service account key JSON."
  exit 1
fi

if [ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
  echo "❌ GOOGLE_APPLICATION_CREDENTIALS does not point to a valid file."
  exit 1
fi

echo "✅ GCP credentials detected at: $GOOGLE_APPLICATION_CREDENTIALS"

# Optional rebuild of Docker image
if [[ "$1" == "--rebuild" ]]; then
  echo "🔄 Rebuilding Docker image..."
  docker build -f test/system-tests/generator-gcp-terraform/Dockerfile.dockerfile \
    -t cloudhopper-tck-gcp .
else
  echo "📦 Skipping Docker rebuild (pass --rebuild to force)"
fi

echo "🚀 Running containerized TCK for GCP"
docker run --rm \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -v "$GOOGLE_APPLICATION_CREDENTIALS":/root/gcp-key.json \
  -w /workspace \
  -e GOOGLE_APPLICATION_CREDENTIALS=/root/gcp-key.json \
  -e GOOGLE_CLOUD_PROJECT=$GCP_PROJECT_ID \
  -e GCP_PROJECT_ID=$GCP_PROJECT_ID \
  -e GCP_REGION=$GCP_REGION \
  cloudhopper-tck-gcp bash -c "
    echo '🔍 Recompiling only system tests...'
    mvn -f /workspace/pom.xml install -DskipTests -pl test/system-tests/generator-gcp-terraform &&
    echo '▶ Running TCK...'
    cd /workspace/test/system-tests/generator-gcp-terraform/
    mvn exec:java \
      -Dexec.mainClass=eu.cloudhopper.mc.test.system.tests.generator.gcp.terraform.TckLauncher \
      -Dexec.classpathScope=test
  "