#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
frontend_dir="$repository_root/gabojago-frontend"
created_files=()

cleanup() {
  local file
  for file in "${created_files[@]:-}"; do
    [[ -z "$file" ]] && continue
    rm -f "$file"
  done
}
trap cleanup EXIT

# pubspec의 asset 선언은 테스트에서도 파일 존재를 요구한다. 로컬 .env는
# 보존하고, 없는 파일만 테스트 실행 동안 빈 placeholder로 만든다.
for filename in .env; do
  file="$frontend_dir/$filename"
  if [[ ! -e "$file" ]]; then
    : > "$file"
    created_files+=("$file")
  fi
done

cd "$frontend_dir"
flutter test
