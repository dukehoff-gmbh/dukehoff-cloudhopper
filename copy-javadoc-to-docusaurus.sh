#!/bin/bash
# Regenerates the Javadoc under docs/static/api (served at https://cloudhopper.eu/api/<module>/).
# Run from the project root after "mvn install -DskipTests". English output whatever the
# local locale; links between modules point to /api/<module>/ on the site instead of the
# project URL that javadoc derives for them.
set -euo pipefail

modules=(
  deployment-config-api
  deployment-config-generator-api
  deployment-config-generator-generic
  deployment-config-processor
  generator-aws-terraform
  generator-azure-terraform
  generator-gcp-terraform
  provider-aws
  provider-azure
  provider-gcp
)

JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US" \
  mvn -B -ntp -q org.apache.maven.plugins:maven-javadoc-plugin:3.11.2:javadoc \
    -pl "$(IFS=,; echo "${modules[*]}")" \
    -Ddoclint=none \
    "-Dbottom=Copyright &#169; 2024&#8211;2026 Dukehoff GmbH. Licensed under the GNU GPL v3 with the Classpath Exception."

for module in "${modules[@]}"; do
  rm -rf "docs/static/api/$module"
  mkdir -p "docs/static/api/$module"
  cp -R "$module/target/reports/apidocs/." "docs/static/api/$module/"
done

find docs/static/api -name '*.html' -print0 \
  | xargs -0 perl -pi -e 's#https://cloudhopper\.eu/([a-z-]+)/apidocs/#/api/$1/#g'
