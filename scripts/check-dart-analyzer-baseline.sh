#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
baseline_file="$repository_root/tools/frontend-analyzer-baseline.txt"
current_file="$(mktemp)"
trap 'rm -f "$current_file"' EXIT

set +e
(cd "$repository_root/gabojago-frontend" && dart analyze --format machine --no-fatal-warnings) > "$current_file" 2>&1
analyze_status=$?
set -e

if grep -q '^ERROR|' "$current_file"; then
  cat "$current_file"
  echo "analyzer baseline: 오류가 있어 기준선 비교를 중단합니다." >&2
  exit 1
fi

normalize() {
  awk -F'|' '
    /^(WARNING|INFO)\|/ {
      path = $4
      sub(/^.*gabojago-frontend\//, "gabojago-frontend/", path)
      print $1 "|" $3 "|" path
    }
  ' "$1" | sort | uniq -c | awk '{ count=$1; $1=""; sub(/^ /, ""); print count "|" $0 }'
}

if [[ "${1:-}" == "--write-baseline" ]]; then
  mkdir -p "$(dirname "$baseline_file")"
  normalize "$current_file" > "$baseline_file"
  echo "analyzer baseline updated: $baseline_file"
  exit 0
fi

if [[ ! -f "$baseline_file" ]]; then
  echo "analyzer baseline file is missing: $baseline_file" >&2
  exit 1
fi

if ! diff -u "$baseline_file" <(normalize "$current_file"); then
  echo "analyzer baseline: 새 warning 또는 info가 추가됐습니다." >&2
  exit 1
fi

if (( analyze_status != 0 )); then
  cat "$current_file"
  exit "$analyze_status"
fi

echo "analyzer baseline: 새 진단이 없습니다."
