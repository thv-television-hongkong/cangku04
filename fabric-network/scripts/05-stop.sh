#!/usr/bin/env bash
# =====================================================================================
# 停止 / 清理 Fabric 测试网络
#   ./05-stop.sh          # 停止容器，保留账本数据
#   ./05-stop.sh --clean  # 停止容器并删除账本卷（链上数据将清空，谨慎使用）
# =====================================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "${1:-}" == "--clean" ]; then
  echo "==> 停止并删除容器与账本卷（链上存证数据将被清空）"
  docker compose -f docker-compose-fabric.yml down -v --remove-orphans
else
  echo "==> 停止容器（账本卷保留）"
  docker compose -f docker-compose-fabric.yml down --remove-orphans
fi
echo "==> 完成"
