# Fabric 测试网络搭建与链码部署手册（普通 PC 可完成）

> 适用环境：Windows 10/11 + WSL2 + Docker Desktop，或 Ubuntu 20.04/22.04 实验室服务器。
> 建议配置：4 核 8G 内存（最低 2 核 4G 可跑通，但链码容器启动较慢）。

## 一、环境准备

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Docker | 20.10+ | Windows 使用 Docker Desktop 并启用 WSL2 后端 |
| Docker Compose | v2 | `docker compose` 命令 |
| Go | 1.18+ | 编译链码 |
| Fabric Binaries | 2.4.x | `cryptogen` `configtxgen` `peer` |
| Fabric Docker Images | 2.4 | `hyperledger/fabric-peer/orderer/tools:2.4`、`couchdb:3.2.2` |

### 1. 安装 Fabric 二进制

```bash
# Linux / WSL2
curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh | bash -s -- --fabric-version 2.4.7 --ca-version 1.5.5 binary
sudo cp fabric-samples/bin/* /usr/local/bin/
export FABRIC_CFG_PATH=$(pwd)
```

### 2. 拉取镜像

```bash
docker pull hyperledger/fabric-orderer:2.4
docker pull hyperledger/fabric-peer:2.4
docker pull hyperledger/fabric-tools:2.4
docker pull hyperledger/fabric-ccenv:2.4
docker pull couchdb:3.2.2
```

### 3. 配置 hosts（Windows 或 Linux 均建议）

```
127.0.0.1 orderer.campus.edu
127.0.0.1 peer0.org1.campus.edu
127.0.0.1 peer1.org1.campus.edu
127.0.0.1 peer0.org2.campus.edu
127.0.0.1 peer1.org2.campus.edu
127.0.0.1 ca.org1.campus.edu
127.0.0.1 ca.org2.campus.edu
```

Windows 路径：`C:\Windows\System32\drivers\etc\hosts`；Linux 路径：`/etc/hosts`。

## 二、启动网络（四步）

在 `fabric-network` 目录下执行：

```bash
cd fabric-network

# 1. 生成组织身份与通道制品
bash scripts/01-generate.sh

# 2. 启动容器（1 Orderer + 2 Org + 4 Peer + 2 CouchDB + cli）
bash scripts/02-network-up.sh

# 3. 创建通道 charitychannel 并让 4 个 Peer 加入
bash scripts/03-create-channel.sh

# 4. 打包 / 安装 / 审批 / 提交链码 charity-cc，并调用 InitLedger
bash scripts/04-deploy-chaincode.sh 1.1 1
```

> Windows 用户请在 **WSL2 / Git Bash** 中执行以上脚本；`docker exec ... cli` 命令在 PowerShell 中亦可直接运行。

停止网络：

```bash
bash scripts/05-stop.sh          # 保留账本数据
bash scripts/05-stop.sh --clean  # 清空账本数据（谨慎）
```

## 三、验证网络与链码

```bash
# 查看容器
docker ps --format "table {{.Names}}\t{{.Status}}"

# 查询链码版本（应为 1.1）
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode query -C charitychannel -n charity-cc -c '{"function":"GetChaincodeVersion","Args":[]}'

# 写入一条捐赠存证
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode invoke -o orderer.campus.edu:7050 --tls \
  --cafile /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/campus.edu/orderers/orderer.campus.edu/msp/tlscacerts/tlsca.campus.edu-cert.pem \
  -C charitychannel -n charity-cc \
  --peerAddresses peer0.org1.campus.edu:7051 \
  --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt \
  --peerAddresses peer0.org2.campus.edu:9051 \
  --tlsRootCertFiles /opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.campus.edu/peers/peer0.org2.campus.edu/tls/ca.crt \
  -c '{"function":"CreateDonationEvidence","Args":["DON001","PRJ001","4444444444444444444444444444444444444444444444444444444444444444","500.00","5555555555555555555555555555555555555555555555555555555555555555","1111111111111111111111111111111111111111111111111111111111111111","20210001","STUDENT"]}'

# 查询存证
docker exec -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_TLS_ENABLED=true \
  -e CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/peers/peer0.org1.campus.edu/tls/ca.crt \
  -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp \
  -e CORE_PEER_ADDRESS=peer0.org1.campus.edu:7051 \
  cli peer chaincode query -C charitychannel -n charity-cc -c '{"function":"QueryByBizId","Args":["DONATION","DON001"]}'
```

## 四、链码单元测试（无需 Fabric 网络）

```bash
cd chaincode
go mod tidy
go test ./... -v
```

测试覆盖：存证写入与查询、交易ID查询、哈希验真（含篡改检测）、隐私字段拒绝、项目全流程溯源、
链式完整性校验、区块高度索引查询、幂等上链、项目状态变更（下架）溯源。

## 五、后端连接配置

后端 `backend/src/main/resources/application.yml` 中：

```yaml
fabric:
  enabled: true
  msp-id: Org1MSP
  channel-name: charitychannel
  chaincode-name: charity-cc
  connection-profile: ../fabric-network/connection-org1.json
  wallet-path: ../fabric-network/wallet
  identity-label: admin
  # 业务身份私钥/证书目录（由 fabric-ca 或 cryptogen 生成）
  cert-path: ../fabric-network/organizations/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp/signcerts/Admin@org1.campus.edu-cert.pem
  key-path: ../fabric-network/organizations/peerOrganizations/org1.campus.edu/users/Admin@org1.campus.edu/msp/keystore
```

> 若无 Docker 环境，可设置 `fabric.enabled: false`，后端启用内置模拟联盟链
> （MockChainClient，链式哈希串联 + 业务数据库快照），便于先完成业务联调。
> MOCK 不具备独立于业务库的多节点共识保障。真实网络需要另外生成组织身份、部署链码，
> 并配置 Gateway 连接与 TLS；本手册的真实网络未在此次公开发布中进行运行验收。

## 六、常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `cryptogen: command not found` | Fabric bin 未加入 PATH | 将 `fabric-samples/bin` 复制到 `/usr/local/bin` 或加入 PATH |
| 链码容器启动超时 | 首次拉取 `fabric-ccenv` 镜像慢 | 提前 `docker pull hyperledger/fabric-ccenv:2.4`，并调大 `CORE_CHAINCODE_STARTUPTIMEOUT` |
| `docker exec cli` 提示容器未启动 | 网络未启动 | 先执行 `02-network-up.sh`，`docker ps` 确认 cli 在运行 |
| 通道创建失败 `could not connect to orderer` | hosts 未配置或端口占用 | 检查 hosts 与 7050 端口 |
| 背书失败 `endorsement policy failure` | Org2 未安装链码 | 重新执行 `04-deploy-chaincode.sh`，确认 4 个 Peer 均安装成功 |
| 内存不足 | 容器过多 | 先只启动 orderer + peer0.org1 + peer0.org2 联调，再补齐其余节点 |
