#!/usr/bin/env bash
# Store "What's New" text for a release tag: one bullet per change on master since the previous
# v* tag (PR titles of merge commits, otherwise commit subjects), without dependency bumps.
# Usage: store-notes.sh <tag> [max_chars]
set -euo pipefail
tag="$1"
max="${2:-4000}"
prev="$(git describe --tags --abbrev=0 --match 'v[0-9]*' "${tag}^" 2>/dev/null || true)"
range="${prev:+${prev}..}${tag}"

lines="$(
  git log --first-parent --format='%H' "$range" | while read -r sha; do
    subject="$(git show -s --format=%s "$sha")"
    if [[ "$subject" == "Merge pull request #"* ]]; then
      line="$(git show -s --format=%b "$sha" | sed '/^[[:space:]]*$/d' | head -n 1)"
    else
      line="$subject"
    fi
    [[ -z "$line" ]] && continue
    [[ "$line" =~ ^(Update\ |chore\(deps|fix\(deps|build\(deps|Merge\ branch) ]] && continue
    echo "• $line"
  done | awk '!seen[$0]++'
)"
[[ -z "$lines" ]] && lines="• Improvements and bug fixes"

# Cut at a line boundary below the store limit (bytes as a safe upper bound for characters).
out=""
while IFS= read -r l; do
  candidate="${out:+$out$'\n'}$l"
  if (( ${#candidate} > max )); then break; fi
  out="$candidate"
done <<< "$lines"
printf '%s\n' "${out:-• Improvements and bug fixes}"
