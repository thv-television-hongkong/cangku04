#!/usr/bin/env bash
# =====================================================================================
# 打包、安装、审批、提交链码 charity-cc（Go 语言）
#   ./04-deploy-chaincode.sh [版本号] [序列号]
# 默认：版本 1.1，序列号 2（与 database/data.sql 中 contract_record 记录一致）
# 背书策略：AND('Org1MSP.peer','Org2MSP.peer')
# =====================================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

CHANNEL_NAME=charitychannel
CC_NAME=charity-cc
CC_VERSION=${1:-1.1}
CC_SEQUENCE=${2:-2}
CC_SRC_PATH=/opt/gopath/src/github.com/campus/charity-chaincode
POLICY="AND('Org1MSP.peer','Org2MSP.peer')"

ORDERER_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/campus.edu/orderers/orderer.campus.edu/msp/tlscacerts/tlsca.campus.edu-cert.pem
ORG1_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt
ORG2_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.campus.edu/peers/peer0.org2.campus.edu/tls/ca.crt

echo "==> 打包链码 ${CC_NAME}:${CC_VERSION}"
docker exec cli peer lifecycle chaincode package ${CC_NAME}.tar.gz \
  --path ${CC_SRC_PATH} --lang golang --label ${CC_NAME}_${CC_VERSION}

install_cc() {
  local mspid=$1 org=$2 peer=$3 address=$4
  echo "==> 在 ${peer} 安装链码"
  docker exec -e CORE_PEER_LOCALMSPID=${mspid} \
    -e CORE_PEER_TLS_ENABLED=true \
    -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/${org}/peers/${peer}/tls/ca.crt \
    -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/${org}/users/Admin@${org}/msp \
    -e CORE_PEER_ADDRESS=${address} \
    cli peer lifecycle chaincode install ${CC_NAME}.tar.gz
}

install_cc Org1MSP org1.campus.edu peer0.org1.campus.edu peer0.org1.campus.edu:7051
install_cc Org1MSP org1.campus.edu peer1.org1.campus.edu peer1.org1.campus.edu:8051
install_cc Org2MSP org2.campus.edu peer0.org2.campus.edu peer0.org2.campus.edu:9051
install_cc Org2MSP org2.campus.edu peer1.org2.campus.edu peer1.org2.campus.edu:10051

echo "==> 查询链码包 ID（Org1）"
PKG_ID=$(docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer lifecycle chaincode queryinstalled --output json | \
  python3 -c "import sys,json;d=json.load(sys.stdin);print([i['package_id'] for i in d['installed_chaincodes'] if i['label']=='${CC_NAME}_${CC_VERSION}'][0])")
echo "    包ID = ${PKG_ID}"

echo "==> Org1 审批链码定义"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer lifecycle chaincode approveformyorg -o orderer.campus.edu:7050 \
  --channelID ${CHANNEL_NAME} --name ${CC_NAME} --version ${CC_VERSION} \
  --package-id ${PKG_ID} --sequence ${CC_SEQUENCE} --tls --cafile ${ORDERER_CA} \
  --signature-policy "${POLICY}" --init-required

echo "==> Org2 审批链码定义"
docker exec -e CORE_PEER_LOCALMSPID=Org2MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG2_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.campus.edu/users/Admin@org2.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org2.campus.edu:9051 \
  cli peer lifecycle chaincode approveformyorg -o orderer.campus.edu:7050 \
  --channelID ${CHANNEL_NAME} --name ${CC_NAME} --version ${CC_VERSION} \
  --package-id ${PKG_ID} --sequence ${CC_SEQUENCE} --tls --cafile ${ORDERER_CA} \
  --signature-policy "${POLICY}" --init-required

echo "==> 检查提交就绪状态"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer lifecycle chaincode checkcommitreadiness --channelID ${CHANNEL_NAME} \
  --name ${CC_NAME} --version ${CC_VERSION} --sequence ${CC_SEQUENCE} --tls --cafile ${ORDERER_CA} --output json

echo "==> 提交链码定义"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer lifecycle chaincode commit -o orderer.campus.edu:7050 \
  --channelID ${CHANNEL_NAME} --name ${CC_NAME} --version ${CC_VERSION} \
  --sequence ${CC_SEQUENCE} --tls --cafile ${ORDERER_CA} \
  --peerAddresses peer0.org1.campus.edu:7051 --tlsRootCertFiles ${ORG1_CA} \
  --peerAddresses peer0.org2.campus.edu:9051 --tlsRootCertFiles ${ORG2_CA} \
  --signature-policy "${POLICY}" --init-required

echo "==> 调用 InitLedger 初始化链码"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode invoke -o orderer.campus.edu:7050 --tls --cafile ${ORDERER_CA} \
  -C ${CHANNEL_NAME} -n ${CC_NAME} --isInit \
  --peerAddresses peer0.org1.campus.edu:7051 --tlsRootCertFiles ${ORG1_CA} \
  --peerAddresses peer0.org2.campus.edu:9051 --tlsRootCertFiles ${ORG2_CA} \
  -c '{"function":"InitLedger","Args":[]}'

sleep 3

echo "==> 校验链码版本"
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_CA} \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode query -C ${CHANNEL_NAME} -n ${CC_NAME} -c '{"function":"GetChaincodeVersion","Args":[]}'

echo "==> 链码部署完成：${CC_NAME} ${CC_VERSION} (sequence ${CC_SEQUENCE})"
