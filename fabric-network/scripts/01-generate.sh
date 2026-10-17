#!/usr/bin/env bash
# =====================================================================================
# 生成 Fabric 身份证书与通道制品
#   ./01-generate.sh
# 依赖：cryptogen / configtxgen（Fabric 2.4 bin 目录已加入 PATH）
# =====================================================================================
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
echo "==> 工作目录: ${ROOT}"

export FABRIC_CFG_PATH=${ROOT}

# 清理旧制品（仅清理本项目生成的目录，避免误删）
if [ -d "${ROOT}/organizations" ]; then
  echo "==> 清理旧的 organizations"
  rm -rf "${ROOT}/organizations"
fi
if [ -d "${ROOT}/channel-artifacts" ]; then
  echo "==> 清理旧的 channel-artifacts"
  rm -rf "${ROOT}/channel-artifacts"
fi
mkdir -p "${ROOT}/channel-artifacts" "${ROOT}/organizations"

# 1. 生成组织与节点身份
echo "==> cryptogen 生成身份证书"
cryptogen generate --config=./crypto-config.yaml --output=./organizations

# 2. 生成创世块
echo "==> 生成系统通道创世块 genesis.block"
configtxgen -profile CharityGenesis -channelID system-channel -outputBlock ./channel-artifacts/genesis.block

# 3. 生成通道创建交易
echo "==> 生成通道交易 charitychannel.tx"
configtxgen -profile CharityChannel -outputCreateChannelTx ./channel-artifacts/charitychannel.tx -channelID charitychannel

# 4. 生成锚节点更新交易
echo "==> 生成 Org1 锚节点交易"
configtxgen -profile CharityChannel -outputAnchorPeersUpdate ./channel-artifacts/Org1MSPanchors.tx -channelID charitychannel -asOrg Org1MSP
echo "==> 生成 Org2 锚节点交易"
configtxgen -profile CharityChannel -outputAnchorPeersUpdate ./channel-artifacts/Org2MSPanchors.tx -channelID charitychannel -asOrg Org2MSP

echo "==> 身份与通道制品生成完成"
ls -l ./channel-artifacts
