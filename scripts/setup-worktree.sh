#!/usr/bin/env bash
set -uo pipefail

# Orca worktree hook으로 사용할 수 있는 안전한 로컬 준비 상태 점검기다.
# 비밀 환경 파일의 값은 읽거나 복사하지 않으며, 기본 실행은 설치·빌드·서비스 기동을 하지 않는다.

repository_root="$(git rev-parse --show-toplevel)"
install_frontend=false

usage() {
  cat <<'EOF'
Usage: ./scripts/setup-worktree.sh [--install-frontend]

기본 실행은 도구와 환경 파일의 존재 여부만 점검합니다.
--install-frontend를 지정하면 gabojago-frontend에서 flutter pub get을 실행합니다.
EOF
}

for argument in "$@"; do
  case "$argument" in
    --install-frontend) install_frontend=true ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "지원하지 않는 옵션: $argument" >&2
      usage >&2
      exit 2
      ;;
  esac
done

missing_tools=0
check_command() {
  local command_name="$1"
  local label="$2"
  if command -v "$command_name" >/dev/null 2>&1; then
    echo "[OK] $label"
  else
    echo "[필요] $label"
    missing_tools=1
  fi
}

check_environment_file() {
  local relative_path="$1"
  if [[ -f "$repository_root/$relative_path" ]]; then
    echo "[OK] $relative_path"
  else
    echo "[설정 필요] $relative_path (값은 docs/local-development.md 참고)"
  fi
}

echo "== GabojaGO worktree 준비 상태 =="
echo "경로: $repository_root"
echo

echo "[필수 도구]"
check_command git "Git"
if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | awk -F '[\".]' '/version/ { print $2; exit }')"
  if [[ "$java_version" == "21" ]]; then
    echo "[OK] JDK 21"
  else
    echo "[필요] JDK 21 (현재: ${java_version:-확인 불가})"
    missing_tools=1
  fi
else
  echo "[필요] JDK 21"
  missing_tools=1
fi
check_command flutter "Flutter SDK"
check_command docker "Docker CLI"
check_command python3 "Python 3 (data-import 실행 시 필요)"

if [[ -x "$repository_root/gabojago-backend/gradlew" ]]; then
  echo "[OK] Backend Gradle Wrapper"
else
  echo "[필요] Backend Gradle Wrapper 실행 권한"
  missing_tools=1
fi

echo
echo "[로컬 환경 파일]"
check_environment_file "gabojago-backend/.env"
check_environment_file "gabojago-frontend/.env"
check_environment_file "data-import/.env"

echo
echo "[선택 점검]"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "[OK] Docker daemon 연결"
else
  echo "[안내] Docker daemon이 실행 중이지 않거나 접근할 수 없습니다. Docker가 필요한 작업에서만 Desktop을 시작하세요."
fi

if [[ "$install_frontend" == true ]]; then
  if command -v flutter >/dev/null 2>&1; then
    echo
    echo "[실행] flutter pub get"
    (cd "$repository_root/gabojago-frontend" && flutter pub get)
  else
    echo "Flutter SDK가 없어 frontend 의존성을 설치할 수 없습니다." >&2
    exit 1
  fi
fi

echo
if (( missing_tools )); then
  echo "필수 도구가 누락되었습니다. 설치 후 다시 실행하세요." >&2
  exit 1
fi

echo "점검 완료: 환경 파일이 없는 경우에도 코드 탐색·수정은 가능하며, 실행 전에만 해당 .env를 별도로 준비하세요."
