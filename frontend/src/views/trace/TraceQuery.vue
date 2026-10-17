<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>多维度溯源查询（项目 ID / 交易 ID / 区块高度 / 业务 ID）</div></template>
      <el-form :inline="true" :model="query">
        <el-form-item label="查询方式">
          <el-radio-group v-model="dimension">
            <el-radio-button label="PROJECT">项目ID</el-radio-button>
            <el-radio-button label="PROJECT_NO">项目编号</el-radio-button>
            <el-radio-button label="ORDER_NO">订单编号</el-radio-button>
            <el-radio-button label="EVIDENCE_NO">存证编号</el-radio-button>
            <el-radio-button label="TX">交易ID</el-radio-button>
            <el-radio-button label="BLOCK">区块高度</el-radio-button>
            <el-radio-button label="BIZ">业务ID</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="dimension === 'PROJECT'" label="项目ID">
          <el-input v-model="query.projectId" placeholder="请输入项目ID" style="width: 220px" />
        </el-form-item>
        <el-form-item v-if="['PROJECT_NO','ORDER_NO','EVIDENCE_NO'].includes(dimension)" label="编号"><el-input v-model="identifier" placeholder="输入完整编号" style="width:320px" /></el-form-item>
        <el-form-item v-if="dimension === 'TX'" label="交易ID">
          <el-input v-model="query.txId" placeholder="Fabric 交易 ID" style="width: 340px" />
        </el-form-item>
        <el-form-item v-if="dimension === 'BLOCK'" label="区块高度">
          <el-input v-model="query.blockHeight" placeholder="请输入区块高度" style="width: 220px" />
        </el-form-item>
        <template v-if="dimension === 'BIZ'">
          <el-form-item label="业务类型">
            <el-select v-model="query.bizType" style="width: 180px">
              <el-option v-for="item in bizTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务ID">
            <el-input v-model="query.bizId" style="width: 180px" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="doQuery">溯源查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="result.message" :title="result.message" type="success" :closable="false" show-icon />

      <el-descriptions v-if="result.chainInfo" :column="4" border size="small" style="margin-top: 16px">
        <el-descriptions-item label="存证总条数">{{ result.chainInfo.totalEvidence || result.chainInfo.evidenceCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="链上举证来源">{{ result.chainInfo.chainSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="链码版本">{{ result.chainInfo.chaincodeVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="链式完整性">
          <el-tag size="small" :type="result.chainIntact ? 'success' : 'danger'">
            {{ result.chainIntact ? '完整未篡改' : '存在断裂' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="result.records && result.records.length" shadow="never" style="margin-top: 16px">
      <template #header><div>
        <span>溯源结果（{{ result.dimension }} 维度）</span>
        <el-button type="text" style="float: right" @click="exportData">导出存证数据</el-button>
      </div></template>
      <evidence-table :records="result.records" @verify="verify" />
    </el-card>

    <el-card v-if="result.timeline && result.timeline.length" shadow="never" style="margin-top: 16px">
      <template #header><div>项目全流程溯源时间线（链上存证 + 链下业务）</div></template>
      <trace-timeline :nodes="result.timeline" />
    </el-card>
  </div>
</template>

<script>
import EvidenceTable from '@/components/EvidenceTable.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import { traceQuery, exportEvidence } from '@/api/trace'
import settings from '@/settings'

export default {
  name: 'TraceQuery',
  components: { EvidenceTable, TraceTimeline },
  data() {
    return {
      dimension: 'PROJECT', identifier: '',
      loading: false,
      bizTypes: settings.bizTypes,
      query: { projectId: '', txId: '', blockHeight: '', bizType: 'DONATION', bizId: '' },
      result: {}
    }
  },
  created() {
    const projectId = this.$route.query.projectId
    if (projectId) {
      this.dimension = 'PROJECT'
      this.query.projectId = projectId
      this.doQuery()
    }
  },
  methods: {
    async doQuery() {
      this.loading = true
      try {
        const payload = {}
        if (this.dimension === 'PROJECT') payload.projectId = this.query.projectId
        if (this.dimension === 'PROJECT_NO') payload.projectNo = this.identifier
        if (this.dimension === 'ORDER_NO') payload.orderNo = this.identifier
        if (this.dimension === 'EVIDENCE_NO') payload.evidenceNo = this.identifier
        if (this.dimension === 'TX') payload.txId = this.query.txId
        if (this.dimension === 'BLOCK') payload.blockHeight = this.query.blockHeight
        if (this.dimension === 'BIZ') {
          payload.bizType = this.query.bizType
          payload.bizId = this.query.bizId
        }
        const res = await traceQuery(payload)
        this.result = res.data || {}
      } finally {
        this.loading = false
      }
    },
    reset() {
      this.query = { projectId: '', txId: '', blockHeight: '', bizType: 'DONATION', bizId: '' }
      this.result = {}
    },
    verify(row) {
      this.$router.push({ path: '/trace/verify', query: { bizType: row.bizType, bizId: row.bizId, hash: row.dataHash } })
    },
    async exportData() {
      const res = await exportEvidence({})
      const rows = res.data || []
      if (rows.length === 0) {
        this.$message.warning('暂无可导出的存证数据')
        return
      }
      const headers = Object.keys(rows[0])
      const csv = [headers.join(',')].concat(rows.map(r => headers.map(h => '"' + String(r[h] === null || r[h] === undefined ? '' : r[h]).replace(/"/g, '""') + '"').join(','))).join('\n')
      const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = '链上存证数据_' + new Date().getTime() + '.csv'
      link.click()
      this.$message.success('存证数据已导出 ' + rows.length + ' 条')
    }
  }
}
</script>
