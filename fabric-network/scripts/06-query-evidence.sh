#!/usr/bin/env bash
# Read-only ledger query. No MySQL or Spring Boot dependency.
# Usage: bash 06-query-evidence.sh tx|event KEY > receipt.json
set -euo pipefail
mode=${1:?Usage: 06-query-evidence.sh tx|event KEY}
key=${2:?Missing transaction ID or event key}
case "$mode" in
  tx) function_name=QueryByTxId ;;
  event) function_name=QueryByEvent ;;
  *) echo 'mode must be tx or event' >&2; exit 2 ;;
esac
request=$(python3 -c 'import json,sys;print(json.dumps({"Args":[sys.argv[1],sys.argv[2]]}))' "$function_name" "$key")
crypto=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE="$crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt" \
  -e CORE_PEER_MSPCONFIGPATH="$crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp" \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode query -C "${CHANNEL_NAME:-charitychannel}" -n "${CC_NAME:-charity-cc}" -c "$request"
