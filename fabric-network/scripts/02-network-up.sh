#!/usr/bin/env bash
# =====================================================================================
# 启动 Fabric 测试网络（1 Orderer + 2 Org + 4 Peer + 2 CouchDB）
#   ./02-network-up.sh
# =====================================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> 启动 Fabric 容器"
docker compose -f docker-compose-fabric.yml up -d

echo "==> 等待容器就绪"
sleep 8

echo "==> 容器状态"
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "orderer|peer|couchdb|cli" || true

echo "==> 网络启动完成，通道名：charitychannel"
