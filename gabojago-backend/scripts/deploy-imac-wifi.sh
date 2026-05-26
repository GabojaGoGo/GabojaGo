#!/bin/bash
set -e

# =====================================================
# 협업자용 배포 스크립트 — 같은 WiFi 환경 전용
# iMac WiFi IP: 192.168.1.182
# 사전 조건: SSH 공개키가 iMac ~/.ssh/authorized_keys에 등록되어 있어야 함
# =====================================================

IMAC_USER="${IMAC_USER:-jyy}"           # 다른 계정이면 IMAC_USER=xxx 환경변수로 오버라이드
IMAC="${IMAC_USER}@192.168.1.182"
BACKEND_SRC="$(cd "$(dirname "$0")/.." && pwd)/"   # 스크립트 위치 기준 자동 탐지
BACKEND_DEST="~/tripmate/gabojago-backend/"
DOCKER="/usr/local/bin/docker"

echo "▶ [1/3] Syncing files to iMac (via WiFi: 192.168.1.182)..."
rsync -avz --delete \
  --exclude='build/' \
  --exclude='.gradle/' \
  --exclude='scripts/' \
  --exclude='.env' \
  "$BACKEND_SRC" "$IMAC:$BACKEND_DEST"

echo "▶ [2/3] Building Docker image on iMac..."
ssh "$IMAC" "cd ~/tripmate/gabojago-backend && $DOCKER compose down && $DOCKER compose build"

echo "▶ [3/3] Starting containers on iMac..."
ssh "$IMAC" "cd ~/tripmate/gabojago-backend && $DOCKER compose up -d"

echo "✓ Backend started → http://192.168.1.182:8080"
