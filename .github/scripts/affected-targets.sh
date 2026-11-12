#!/usr/bin/env bash
# Reads the changed files of a pull request (one path per line) from stdin and
# prints which parts of the PR check have to run, as key=value lines for
# $GITHUB_OUTPUT:
#
#   code=true|false        build, unit tests and archetype test
#   <target>=true|false    TCK of that target (aws, azure, gcp, springboot, knative)
#
# A path that belongs to exactly one target runs only that target's TCK. A path
# that this table does not know runs everything, so a gap in the table costs
# build minutes, never coverage.
set -euo pipefail

TARGETS=(aws azure gcp springboot knative)

# path prefixes that belong to one target; knative builds on the springboot-http generator
# and runs its application, so a Spring Boot change also affects knative
target_of() {
  case "$1" in
    generator-aws-terraform/*|provider-aws/*|test/system-tests/generator-aws-terraform/*|test/tck/generator-bindings/aws-terraform/*)
      echo aws ;;
    generator-azure-terraform/*|provider-azure/*|test/system-tests/generator-azure-terraform/*|test/tck/generator-bindings/azure-terraform/*)
      echo azure ;;
    generator-gcp-terraform/*|provider-gcp/*|test/system-tests/generator-gcp-terraform/*|test/tck/generator-bindings/gcp-terraform/*)
      echo gcp ;;
    generator-springboot/*|provider-springboot/*|test/system-tests/generator-springboot/*|test/tck/generator-bindings/springboot-http/*)
      echo "springboot knative" ;;
    generator-knative/*|test/knative/*|test/system-tests/generator-knative/*|test/tck/generator-bindings/knative/*)
      echo knative ;;
    *) echo "" ;;
  esac
}

code=false
all=false
hit=" "   # targets with a change, space separated

while IFS= read -r path; do
  [ -z "$path" ] && continue
  case "$path" in
    # documentation only: the site and Markdown files at the top level
    docs/*) continue ;;
    */*) ;;
    *.md) continue ;;
  esac
  code=true
  case "$path" in
    # built and tested by the build and the archetype test; no TCK deploys them
    demo/*|maven-archetype/*) continue ;;
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
