#!/bin/bash
set -e

IMAC="jyy@100.104.150.127"
DOCKER="/usr/local/bin/docker"

echo "▶ Restarting backend on iMac (via Tailscale)..."
ssh "$IMAC" "cd ~/tripmate/backend && $DOCKER compose restart backend"

echo "✓ Backend restarted → http://100.104.150.127:8080"
