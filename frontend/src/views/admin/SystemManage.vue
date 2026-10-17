<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>系统日志、数据库备份与智能合约管理</div></template>
      <el-tabs v-model="activeTab">
        <!-- 系统日志（include 操作审计追踪） -->
        <el-tab-pane label="系统日志" name="log">
          <el-form :inline="true" class="filter-bar">
            <el-form-item label="模块">
              <el-input v-model="logQuery.module" placeholder="例如：捐赠业务" style="width: 180px" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="logQuery.keyword" placeholder="操作 / 账号 / 姓名" style="width: 200px" />
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadLogs">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="logs" border stripe size="small" v-loading="loading">
            <el-table-column prop="logId" label="ID" width="70" />
            <el-table-column prop="module" label="模块" width="130" />
            <el-table-column prop="operation" label="操作" width="200" />
            <el-table-column prop="realName" label="操作人" width="110" />
            <el-table-column prop="account" label="账号" width="120" />
            <el-table-column prop="role" label="角色" width="100" />
            <el-table-column prop="requestUri" label="请求地址" min-width="180" />
            <el-table-column prop="ip" label="IP" width="130" />
            <el-table-column label="结果" width="90">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.resultStatus === 'SUCCESS' ? 'success' : 'danger'">
                  {{ scope.row.resultStatus === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column label="操作时间" width="160">
              <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <div class="table-pagination">
            <el-pagination background layout="total, prev, pager, next" :total="logTotal"
                           v-model:current-page="logQuery.current" :page-size="logQuery.size" @current-change="loadLogs" />
          </div>
        </el-tab-pane>

        <!-- 数据库备份（可靠性需求） -->
        <el-tab-pane label="数据库备份" name="backup">
          <el-button type="primary" size="small" @click="doBackup">立即执行备份</el-button>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin: 12px 0"
            title="系统按配置的 cron 定时自动备份 MySQL 业务数据；即使业务库损坏，仍可依据链上凭证核验存证数据。"
          />
          <el-table :data="backups" border stripe size="small" v-loading="loading">
            <el-table-column prop="backupName" label="备份文件名" min-width="240" />
            <el-table-column prop="backupPath" label="存储路径" min-width="240" />
            <el-table-column label="大小" width="120">
              <template #default="scope">{{ (Number(scope.row.backupSize || 0) / 1024).toFixed(1) }} KB</template>
            </el-table-column>
            <el-table-column prop="backupType" label="类型" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMsg" label="失败原因" min-width="180" />
            <el-table-column label="备份时间" width="170">
              <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 智能合约（extends 部署合约升级） -->
        <el-tab-pane label="智能合约" name="contract">
          <el-button type="primary" size="small" @click="contractVisible = true">登记合约部署 / 升级</el-button>
          <el-descriptions :column="4" border size="small" style="margin: 12px 0">
            <el-descriptions-item label="链码名称">{{ chain.chaincodeName }}</el-descriptions-item>
            <el-descriptions-item label="通道名称">{{ chain.channelName }}</el-descriptions-item>
            <el-descriptions-item label="当前版本">{{ chain.chaincodeVersion }}</el-descriptions-item>
            <el-descriptions-item label="存证来源">{{ chain.chainSource }}</el-descriptions-item>
            <el-descriptions-item label="存证总条数">{{ chain.totalEvidence }}</el-descriptions-item>
            <el-descriptions-item label="链式完整性">
              <el-tag size="small" :type="chain.chainIntact ? 'success' : 'danger'">
                {{ chain.chainIntact ? '完整' : '断裂' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="背书策略" :span="2">{{ chain.endorsementPolicy }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="contracts" border stripe size="small" v-loading="loading">
            <el-table-column prop="chaincodeName" label="链码名称" width="140" />
            <el-table-column prop="chaincodeVersion" label="版本" width="90" />
            <el-table-column prop="sequenceNo" label="序列号" width="90" />
            <el-table-column prop="operationType" label="操作" width="100" />
            <el-table-column prop="endorsementPolicy" label="背书策略" min-width="220" />
            <el-table-column prop="txId" label="交易 ID" min-width="200" />
            <el-table-column prop="operatorName" label="操作人" width="110" />
            <el-table-column prop="description" label="说明" min-width="200" />
            <el-table-column label="操作时间" width="170">
              <template #default="scope">{{ formatTime(scope.row.operationTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 区块链节点状态 -->
        <el-tab-pane label="区块链状态" name="chain">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="是否连接真实链">{{ chain.enabled ? '是（Fabric）' : '否（模拟链）' }}</el-descriptions-item>
            <el-descriptions-item label="当前组织 MSP">{{ chain.mspId }}</el-descriptions-item>
            <el-descriptions-item label="链上最新哈希">
              <span class="hash-text">{{ chain.latestHash }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <el-table :data="chain.nodes || []" border stripe size="small" style="margin-top: 12px">
            <el-table-column prop="name" label="节点" />
            <el-table-column prop="mspId" label="所属组织" width="140" />
            <el-table-column prop="role" label="角色" width="160" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" type="success">在线</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog title="登记智能合约部署 / 升级" v-model="contractVisible" width="560px">
      <el-form :model="contractForm" label-width="120px">
        <el-form-item label="链码名称"><el-input v-model="contractForm.chaincodeName" /></el-form-item>
        <el-form-item label="链码版本"><el-input v-model="contractForm.chaincodeVersion" placeholder="例如 2.0" /></el-form-item>
        <el-form-item label="操作类型">
          <el-radio-group v-model="contractForm.operationType">
            <el-radio label="DEPLOY">首次部署</el-radio>
            <el-radio label="UPGRADE">版本升级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="链码序列号"><el-input v-model="contractForm.sequenceNo" /></el-form-item>
        <el-form-item label="背书策略">
          <el-input v-model="contractForm.endorsementPolicy" placeholder="AND('Org1MSP.peer','Org2MSP.peer')" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="contractForm.description" type="textarea" :rows="3"
                    placeholder="例如：升级链码新增全流程溯源与隐私字段校验能力" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="contractVisible = false">取消</el-button>
        <el-button type="primary" @click="saveContract">保存并上链存证</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listLogs, listBackups, runBackup, listContracts, recordContract, chainStatus } from '@/api/admin'
import { formatTime } from '@/utils/format'

export default {
  name: 'SystemManage',
  data() {
    return {
      activeTab: 'log',
      loading: false,
      logs: [],
      logTotal: 0,
      logQuery: { current: 1, size: 10, module: '', keyword: '' },
      backups: [],
      contracts: [],
      chain: {},
      contractVisible: false,
      contractForm: {
        chaincodeName: 'charity-cc',
        chaincodeVersion: '2.0',
        operationType: 'UPGRADE',
        sequenceNo: 3,
        endorsementPolicy: "AND('Org1MSP.peer','Org2MSP.peer')",
        description: ''
      }
    }
  },
  created() {
    this.loadAll()
  },
  methods: {
    formatTime,
    async loadAll() {
      await this.loadLogs()
      await this.loadBackups()
      await this.loadContracts()
    },
    async loadLogs() {
      this.loading = true
      try {
        const res = await listLogs(this.logQuery)
        this.logs = (res.data && res.data.records) || []
        this.logTotal = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async loadBackups() {
      const res = await listBackups({ current: 1, size: 20 })
      this.backups = (res.data && res.data.records) || []
    },
    async loadContracts() {
      const [contracts, chain] = await Promise.all([listContracts(), chainStatus()])
      this.contracts = contracts.data || []
      this.chain = chain.data || {}
    },
    async doBackup() {
      const res = await runBackup()
      this.$message.success('备份任务已执行：' + ((res.data || {}).status || ''))
      await this.loadBackups()
    },
    async saveContract() {
      await recordContract(this.contractForm)
      this.$message.success('维护登记已保存，摘要存证已排队；实际合约部署需执行 Fabric 生命周期脚本')
      this.contractVisible = false
      await this.loadContracts()
    }
  }
}
</script>
