<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="13">
        <el-card shadow="never">
          <template #header><div>查看区块链存证信息（extends 存证哈希验证）</div></template>
          <el-form :inline="true">
            <el-form-item label="业务类型">
              <el-select v-model="query.bizType" style="width: 180px">
                <el-option v-for="item in bizTypes" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务ID">
              <el-input v-model="query.bizId" style="width: 150px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doQuery">查询存证</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="message" :title="message" type="success" :closable="false" show-icon />
          <evidence-table :records="records" @verify="verifyRow" />
        </el-card>
      </el-col>
      <el-col :span="11">
        <el-card shadow="never">
          <template #header><div>存证哈希验证（哈希验真）</div></template>
          <hash-compare :default-biz-type="query.bizType" :default-biz-id="query.bizId" />
        </el-card>
        <el-card shadow="never" style="margin-top: 16px">
          <template #header><div>区块链存证总览</div></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="存证总条数">{{ overview.evidenceTotal || 0 }}</el-descriptions-item>
            <el-descriptions-item label="涉及项目数">{{ overview.projectCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="最大区块高度">{{ overview.maxBlockHeight || '-' }}</el-descriptions-item>
            <el-descriptions-item label="链式完整性">
              <el-tag size="small" :type="overview.chainIntact ? 'success' : 'danger'">
                {{ overview.chainIntact ? '完整未篡改' : '存在断裂' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="链码版本">{{ overview.chaincodeVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="背书策略">{{ overview.endorsementPolicy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最新链式哈希">
              <span class="hash-text">{{ overview.latestHash }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="section-title">存证业务分布</div>
          <stat-chart type="pie" :data="bizChart" :height="220" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import EvidenceTable from '@/components/EvidenceTable.vue'
import HashCompare from '@/components/HashCompare.vue'
import StatChart from '@/components/StatChart.vue'
import { auditEvidenceQuery, auditChainOverview } from '@/api/audit'
import settings from '@/settings'

export default {
  name: 'AuditEvidence',
  components: { EvidenceTable, HashCompare, StatChart },
  data() {
    return {
      bizTypes: settings.bizTypes,
      query: { bizType: 'DONATION', bizId: '' },
      records: [],
      message: '',
      overview: {}
    }
  },
  computed: {
    bizChart() {
      const byType = this.overview.byBizTypeLabel || this.overview.byBizType || {}
      return Object.keys(byType).map(k => ({ name: k, value: byType[k] }))
    }
  },
  created() {
    this.loadOverview()
  },
  methods: {
    async doQuery() {
      const res = await auditEvidenceQuery({ bizType: this.query.bizType, bizId: this.query.bizId })
      const data = res.data || {}
      this.records = data.records || []
      this.message = data.message || ''
    },
    verifyRow(row) {
      this.query.bizType = row.bizType
      this.query.bizId = row.bizId
    },
    async loadOverview() {
      try {
        const res = await auditChainOverview()
        this.overview = res.data || {}
      } catch (e) {
        this.overview = {}
      }
    }
  }
}
</script>
