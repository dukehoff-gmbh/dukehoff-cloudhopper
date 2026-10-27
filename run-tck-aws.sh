#!/bin/bash
set -e

if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || [ -z "$AWS_REGION" ]; then
  echo "❌ AWS credentials not set"
  exit 1
fi

echo "✅ AWS credentials detected."

# Build Docker image only if necessary
if [[ "$1" == "--rebuild" ]]; then
  echo "🔄 Rebuilding Docker image..."
  docker build -f test/system-tests/generator-aws-terraform/Dockerfile.dockerfile \
    -t cloudhopper-tck-aws .
else
  echo "📦 Skipping Docker rebuild (pass --rebuild to force)"
fi

echo "🚀 Running containerized TCK for AWS"
docker run --rm \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  -e AWS_ACCESS_KEY_ID \
  -e AWS_SECRET_ACCESS_KEY \
  -e AWS_REGION \
  -e TF_VAR_cloudhopper_name_prefix \
  cloudhopper-tck-aws bash -c "
    echo '🔍 Recompiling only system tests...'
    mvn -f /workspace/pom.xml install -DskipTests -pl test/system-tests/generator-aws-terraform &&
    echo '▶ Running TCK...'
    cd /workspace/test/system-tests/generator-aws-terraform/
    mvn exec:java \
      -Dexec.mainClass=eu.cloudhopper.mc.test.system.tests.generator.aws.terraform.TckLauncher \
      -Dexec.classpathScope=test
  "