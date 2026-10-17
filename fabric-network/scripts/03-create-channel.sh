#!/usr/bin/env bash
# =====================================================================================
# 创建并加入通道 charitychannel
#   ./03-create-channel.sh
# 说明：通过 cli 容器执行 peer channel 命令，Org1 / Org2 四个 Peer 全部加入通道
# =====================================================================================
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
CHANNEL_NAME=charitychannel

echo "==> 使用 Org1 Admin 创建通道 ${CHANNEL_NAME}"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer channel create -o orderer.campus.edu:7050 -c ${CHANNEL_NAME} \
  -f ./channel-artifacts/${CHANNEL_NAME}.tx \
  --outputBlock ./channel-artifacts/${CHANNEL_NAME}.block \
  --tls --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/campus.edu/orderers/orderer.campus.edu/msp/tlscacerts/tlsca.campus.edu-cert.pem

join_peer() {
  local mspid=$1 org=$2 peer=$3 address=$4
  echo "==> ${peer} 加入通道"
  docker exec -e CORE_PEER_LOCALMSPID=${mspid} \
    -e CORE_PEER_TLS_ENABLED=true \
    -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/${org}/peers/${peer}/tls/ca.crt \
    -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/${org}/users/Admin@${org}/msp \
    -e CORE_PEER_ADDRESS=${address} \
    cli peer channel join -b ./channel-artifacts/${CHANNEL_NAME}.block
}

join_peer Org1MSP org1.campus.edu peer0.org1.campus.edu peer0.org1.campus.edu:7051
join_peer Org1MSP org1.campus.edu peer1.org1.campus.edu peer1.org1.campus.edu:8051
join_peer Org2MSP org2.campus.edu peer0.org2.campus.edu peer0.org2.campus.edu:9051
join_peer Org2MSP org2.campus.edu peer1.org2.campus.edu peer1.org2.campus.edu:10051

echo "==> 更新锚节点"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 cli \
  peer channel update -o orderer.campus.edu:7050 -c ${CHANNEL_NAME} \
  -f ./channel-artifacts/Org1MSPanchors.tx --tls \
  --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/campus.edu/orderers/orderer.campus.edu/msp/tlscacerts/tlsca.campus.edu-cert.pem

docker exec -e CORE_PEER_LOCALMSPID=Org2MSP \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.campus.edu/users/Admin@org2.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org2.campus.edu:9051 cli \
  peer channel update -o orderer.campus.edu:7050 -c ${CHANNEL_NAME} \
  -f ./channel-artifacts/Org2MSPanchors.tx --tls \
  --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/campus.edu/orderers/orderer.campus.edu/msp/tlscacerts/tlsca.campus.edu-cert.pem

echo "==> 通道创建完成：${CHANNEL_NAME}"
