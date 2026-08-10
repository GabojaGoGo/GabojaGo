#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example to .env first." >&2
  exit 1
fi

get_env_value() {
  awk -v key="$1" '
    index($0, key "=") == 1 {
      print substr($0, length(key) + 2)
      exit
    }
  ' .env
}

require_env_value() {
  local key="$1"
  local value
  value="$(get_env_value "$key")"
  if [[ -z "$value" || "$value" == your_* || "$value" == replace_with_* ]]; then
    echo "Set $key in .env before running locally." >&2
    exit 1
  fi
  printf '%s' "$value"
}

db_password="$(require_env_value DB_PASSWORD)"
jwt_secret="$(require_env_value JWT_SECRET)"
tour_api_service_key="$(get_env_value TOUR_API_SERVICE_KEY)"
kakao_rest_api_key="$(get_env_value KAKAO_REST_API_KEY)"

if ! command -v mysqladmin >/dev/null 2>&1; then
  echo "mysqladmin not found. Install/start local MySQL first." >&2
  exit 1
fi

if ! mysqladmin ping -h 127.0.0.1 -P 3306 -uroot -p"$db_password" --silent; then
  echo "Local MySQL is not reachable at 127.0.0.1:3306 with the .env DB_PASSWORD." >&2
  echo "Try: brew services start mysql" >&2
  exit 1
fi

if command -v redis-cli >/dev/null 2>&1 && ! redis-cli -h 127.0.0.1 -p 6379 ping >/dev/null 2>&1; then
  if command -v redis-server >/dev/null 2>&1; then
    redis-server --daemonize yes --dir /private/tmp --dbfilename gabojago-redis.rdb --port 6379
  else
    echo "Local Redis is not reachable and redis-server is not installed." >&2
    exit 1
  fi
fi

mysql -h 127.0.0.1 -P 3306 -uroot -p"$db_password" -e \
  "CREATE DATABASE IF NOT EXISTS gabojago CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

env \
  DB_URL='jdbc:mysql://127.0.0.1:3306/gabojago?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
  DB_USERNAME=root \
  DB_PASSWORD="$db_password" \
  REDIS_HOST=127.0.0.1 \
  REDIS_PORT=6379 \
  JWT_SECRET="$jwt_secret" \
  TOUR_API_SERVICE_KEY="$tour_api_service_key" \
  KAKAO_REST_API_KEY="$kakao_rest_api_key" \
  ./gradlew bootRun
