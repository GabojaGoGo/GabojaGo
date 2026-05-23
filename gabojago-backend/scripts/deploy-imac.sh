#!/bin/bash
set -e

# Tailscale IP는 로컬/외부 모두 동작 — 항상 이 IP 사용
IMAC="jyy@100.104.150.127"
BACKEND_SRC="/Users/jyy/tripmate/backend/"
BACKEND_DEST="~/tripmate/backend/"
DOCKER="/usr/local/bin/docker"

echo "▶ [1/3] Syncing files to iMac (via Tailscale)..."
rsync -avz --exclude='build/' --exclude='.gradle/' --exclude='scripts/' \
  "$BACKEND_SRC" "$IMAC:$BACKEND_DEST"

echo "▶ [2/3] Building Docker image on iMac..."
ssh "$IMAC" "cd ~/tripmate/backend && $DOCKER compose down && $DOCKER compose build"

echo "▶ [3/3] Starting containers on iMac..."
ssh "$IMAC" "cd ~/tripmate/backend && $DOCKER compose up -d"

echo "✓ Backend started → http://100.104.150.127:8080"
