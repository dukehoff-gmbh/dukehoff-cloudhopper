#!/usr/bin/env bash
# Reads the changed files of a pull request (one path per line) from stdin and
# prints which parts of the PR check have to run, as key=value lines for
# $GITHUB_OUTPUT:
#
#   code=true|false        build, unit tests and archetype test
#   <target>=true|false    TCK of that target (aws, azure, gcp, springboot, knative, scaleway)
#   awsnative=true|false   TCK of aws-terraform as a native image: only for changes to the AWS
#                          runtime, generator and TCK (a native build takes minutes); a manual
#                          run of the workflow runs it too
#   springnative=true|false TCK of knative with the Spring Boot application as a native image:
#                          only for changes to the Spring Boot runtime and generator and the
#                          Knative TCK, and on manual runs
#
# A path that belongs to exactly one target runs only that target's TCK. A path
# that this table does not know runs everything, so a gap in the table costs
# build minutes, never coverage.
set -euo pipefail

TARGETS=(aws azure gcp springboot knative scaleway)

# path prefixes that belong to one target; knative and scaleway build on the springboot-http
# generator and run its application, so a Spring Boot change also affects them
target_of() {
  case "$1" in
    # scaleway-terraform sends to its queues with the SQS client of provider-aws-publisher;
    # knative and scaleway-terraform use buckets with the S3 client of provider-aws-blobstore
    provider-aws/*)
      echo "aws awsnative knative scaleway" ;;
    provider-aws-publisher/*)
      echo "aws awsnative scaleway" ;;
    provider-aws-blobstore/*)
      echo "aws awsnative knative scaleway" ;;
    provider-aws-keyvalue/*)
      echo "aws awsnative" ;;
    # knative and scaleway-terraform keep tables in PostgreSQL
    provider-jdbc-keyvalue/*)
      echo "knative scaleway" ;;
    generator-aws-terraform/*|test/system-tests/generator-aws-terraform/*|test/tck/generator-bindings/aws-terraform/*)
      echo "aws awsnative" ;;
    # builds and smoke-tests the native image of aws-terraform
    cloudhopper-maven-plugin/*)
      echo awsnative ;;
    generator-azure-terraform/*|provider-azure/*|provider-azure-publisher/*|provider-azure-blobstore/*|provider-azure-keyvalue/*|test/system-tests/generator-azure-terraform/*|test/tck/generator-bindings/azure-terraform/*)
      echo azure ;;
    generator-gcp-terraform/*|provider-gcp/*|provider-gcp-publisher/*|provider-gcp-blobstore/*|provider-gcp-keyvalue/*|test/system-tests/generator-gcp-terraform/*|test/tck/generator-bindings/gcp-terraform/*)
      echo gcp ;;
    generator-springboot/*|provider-springboot/*|test/system-tests/generator-springboot/*|test/tck/generator-bindings/springboot-http/*)
      echo "springboot springnative knative scaleway" ;;
    generator-knative/*|test/knative/*|test/system-tests/generator-knative/*|test/tck/generator-bindings/knative/*)
      echo "knative springnative" ;;
    generator-scaleway-terraform/*|test/system-tests/generator-scaleway-terraform/*|test/tck/generator-bindings/scaleway-terraform/*)
      echo scaleway ;;
    *) echo "" ;;
  esac
}

code=false
all=false
hit=" "   # targets with a change, space separated

while IFS= read -r path; do
  [ -z "$path" ] && continue
  case "$path" in
    # documentation only: the site, Markdown files at the top level and the
    # short README of each module (deeper READMEs, e.g. in the archetype, are code)
    docs/*) continue ;;
    */*/*) ;;
    */README.md) continue ;;
    */*) ;;
    *.md) continue ;;
  esac
  code=true
  case "$path" in
    # built and tested by the build and the archetype test; no TCK deploys them
    # (skills/ is the Agent Skill the archetype packages)
    maven-archetype/*|skills/*) continue ;;
  esac
  target=$(target_of "$path")
  if [ -n "$target" ]; then
    hit="$hit$target "
  else
    all=true
  fi
done

echo "code=$code"
for t in "${TARGETS[@]}"; do
  if [ "$all" = true ] || [[ "$hit" == *" $t "* ]]; then echo "$t=true"; else echo "$t=false"; fi
done
if [[ "$hit" == *" awsnative "* ]]; then echo "awsnative=true"; else echo "awsnative=false"; fi
if [[ "$hit" == *" springnative "* ]]; then echo "springnative=true"; else echo "springnative=false"; fi
