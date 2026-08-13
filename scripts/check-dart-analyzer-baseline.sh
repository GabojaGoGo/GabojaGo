#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
baseline_file="$repository_root/tools/frontend-analyzer-baseline.txt"
current_file="$(mktemp)"
trap 'rm -f "$current_file"' EXIT

# 테스트에서는 analyzer 실행 없이 캡처한 machine 형식 출력을 주입할 수 있다.
if [[ -n "${DART_ANALYZER_MACHINE_OUTPUT_FILE:-}" ]]; then
  cat "$DART_ANALYZER_MACHINE_OUTPUT_FILE" > "$current_file"
  analyze_status=0
else
  set +e
  (cd "$repository_root/gabojago-frontend" && dart analyze --format machine --no-fatal-warnings) > "$current_file" 2>&1
  analyze_status=$?
  set -e
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

print_diagnostics() {
  local diagnostics_file="$1"
  local heading="$2"
  local severity_filter="${3:-ERROR|WARNING|INFO}"

  echo "$heading" >&2
  awk -F'|' -v severity_filter="$severity_filter" '
    $1 ~ ("^(" severity_filter ")$") {
      path = $4
      sub(/^.*gabojago-frontend\//, "gabojago-frontend/", path)
      printf "  - %s %s (%s:%s:%s) %s\n", $1, $3, path, $5, $6, $8
    }
  ' "$diagnostics_file" >&2

  if ! grep -Eq "^(${severity_filter})\\|" "$diagnostics_file"; then
    cat "$diagnostics_file" >&2
  fi
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

current_diagnostics="$(normalize "$current_file")"
new_diagnostics="$(
  awk -F'|' '
    NR == FNR {
      baseline[$2 FS $3 FS $4] = $1
      next
    }
    {
      key = $2 FS $3 FS $4
      if (($1 + 0) > (baseline[key] + 0)) print
    }
  ' "$baseline_file" - <<< "$current_diagnostics"
)"

if grep -q '^ERROR|' "$current_file"; then
  print_diagnostics "$current_file" "analyzer baseline: 오류가 발견됐습니다."
  echo "analyzer baseline: 오류는 기준선과 관계없이 실패합니다." >&2
  exit 1
fi

if [[ -n "$new_diagnostics" ]]; then
  echo "analyzer baseline: 새 warning 또는 info가 추가됐습니다:" >&2
  while IFS='|' read -r count severity code path; do
    [[ -z "$count" ]] && continue
    echo "  - ${severity} ${code} (${path}): ${count}건 증가" >&2
    echo "      현재 위치 (기준선의 같은 진단 포함):" >&2
    awk -F'|' -v severity="$severity" -v code="$code" -v path="$path" '
      $1 == severity && $3 == code {
        diagnostic_path = $4
        sub(/^.*gabojago-frontend\//, "gabojago-frontend/", diagnostic_path)
        if (diagnostic_path == path) {
          printf "      %s:%s:%s %s\n", diagnostic_path, $5, $6, $8
        }
      }
    ' "$current_file" >&2
  done <<< "$new_diagnostics"
  echo "analyzer baseline: 의도된 변경이면 기존 진단을 정리한 뒤 기준선을 갱신하세요." >&2
  exit 1
fi

if (( analyze_status != 0 )); then
  print_diagnostics "$current_file" "analyzer baseline: analyzer 실행이 실패했습니다."
  exit "$analyze_status"
fi

echo "analyzer baseline: 새 진단이 없습니다."
