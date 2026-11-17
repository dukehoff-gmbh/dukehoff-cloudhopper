#!/usr/bin/env bash
# The documentation lives in one place: the Docusaurus site under docs/docs.
# Fails when a module grows its own docs directory again or when a module README
# turns from a pointer to the site into a second copy of its documentation.
set -euo pipefail

MAX_LINES=15
fail=0

while IFS= read -r dir; do
  echo "::error file=$dir::module documentation belongs in docs/docs, not in $dir"
  fail=1
done < <(git ls-files | grep -E '^[^/]+/docs/' | grep -v '^docs/' | cut -d/ -f1-2 | sort -u)

while IFS= read -r readme; do
  lines=$(wc -l < "$readme" | tr -d " ")
  if [ "$lines" -gt "$MAX_LINES" ]; then
    echo "::error file=$readme::$readme has $lines lines; keep module READMEs to a short pointer to the site (max $MAX_LINES)"
    fail=1
  fi
done < <(git ls-files | grep -E '^[^/]+/README\.md$')

if [ "$fail" -eq 0 ]; then
  echo "docs layout ok"
fi
exit "$fail"
