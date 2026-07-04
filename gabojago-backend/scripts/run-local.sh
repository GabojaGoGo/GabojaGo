#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v mysqladmin >/dev/null 2>&1; then
  echo "mysqladmin not found. Install/start local MySQL first." >&2
  exit 1
fi

if ! mysqladmin ping -h 127.0.0.1 -P 3306 -uroot -proot --silent; then
  echo "Local MySQL is not reachable at 127.0.0.1:3306 with root/root." >&2
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

mysql -h 127.0.0.1 -P 3306 -uroot -proot -e \
  "CREATE DATABASE IF NOT EXISTS gabojago CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

TOUR_API_SERVICE_KEY="$(awk -F= '$1=="TOUR_API_SERVICE_KEY"{sub(/^[^=]*=/,""); print}' .env)"
KAKAO_REST_API_KEY="$(awk -F= '$1=="KAKAO_REST_API_KEY"{sub(/^[^=]*=/,""); print}' .env)"

env \
  DB_URL='jdbc:mysql://127.0.0.1:3306/gabojago?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
  DB_USERNAME=root \
  DB_PASSWORD=root \
  REDIS_HOST=127.0.0.1 \
  REDIS_PORT=6379 \
  JWT_SECRET='local-test-jwt-secret-32-bytes-minimum-for-hs256-2026' \
  TOUR_API_SERVICE_KEY="$TOUR_API_SERVICE_KEY" \
  KAKAO_REST_API_KEY="$KAKAO_REST_API_KEY" \
  ./gradlew bootRun
