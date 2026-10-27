# Run this from project root
mkdir -p docs/static/api

for module in \
  deployment-config-api \
  deployment-config-generator \
  generator-aws-terraform \
  generator-azure-terraform \
  generator-gcp-terraform
do
  if [ -d "$module/target/site/apidocs" ]; then
    mkdir -p "docs/static/api/$module"
    cp -r "$module/target/site/apidocs/"* "docs/static/api/$module/"
  fi
done

