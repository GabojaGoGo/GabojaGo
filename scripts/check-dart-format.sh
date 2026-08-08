#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
cd "$repository_root"
dart_files=()

base_ref="${FORMAT_BASE_REF:-origin/develop}"
if ! git rev-parse --verify --quiet "$base_ref" >/dev/null; then
  echo "format check: $base_ref 를 찾을 수 없어 전체 Dart 파일을 검사합니다."
  dart_files=(gabojago-frontend/lib gabojago-frontend/test)
else
  merge_base="$(git merge-base "$base_ref" HEAD)"
  while IFS= read -r file; do
    [[ -n "$file" ]] && dart_files+=("$file")
  done < <(
    {
      git diff --name-only --diff-filter=ACMR "$merge_base"...HEAD
      git diff --name-only --diff-filter=ACMR
      git diff --cached --name-only --diff-filter=ACMR
    } | sort -u | grep -E '^gabojago-frontend/(lib|test)/.*\.dart$' || true
  )
fi

if (( ${#dart_files[@]} == 0 )); then
  echo "format check: 검사할 변경 Dart 파일이 없습니다."
  exit 0
fi

paths=()
for file in "${dart_files[@]}"; do
  paths+=("${file#gabojago-frontend/}")
done

cd gabojago-frontend
dart format --output=none --set-exit-if-changed "${paths[@]}"
