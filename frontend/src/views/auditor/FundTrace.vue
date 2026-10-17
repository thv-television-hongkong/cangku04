<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header><div>资金全链路追踪（include 资金链追踪）</div></template>
      <el-form :inline="true">
        <el-form-item label="项目ID">
          <el-input v-model="projectId" placeholder="请输入项目ID" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">追踪资金链路</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="资金链路：捐赠记录 → 资金提取 → 资金使用凭证，各环节哈希均已上链存证，可逐笔核验。"
      />
    </el-card>

    <el-card v-if="summary" shadow="never" style="margin-top: 16px">
      <template #header><div>资金链路汇总</div></template>
      <el-row :gutter="16">
        <el-col :span="6"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(summary.donationTotal) }}</div><div class="stat-label">捐赠合计（{{ summary.donationCount || 0 }} 笔）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(summary.withdrawalTotal) }}</div><div class="stat-label">提取合计（{{ summary.withdrawalCount || 0 }} 笔）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(summary.fundUsageTotal) }}</div><div class="stat-label">使用凭证（{{ summary.fundUsageCount || 0 }} 笔）</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">{{ summary.evidenceCount || 0 }}</div><div class="stat-label">链上存证节点</div></div></el-col>
      </el-row>
      <el-descriptions :column="3" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="最新区块高度">{{ summary.latestBlockHeight || '-' }}</el-descriptions-item>
        <el-descriptions-item label="链式完整性">
          <el-tag size="small" :type="chainIntact ? 'success' : 'danger'">{{ chainIntact ? '完整' : '存在断裂' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最新链式哈希"><span class="hash-text">{{ summary.latestChainHash }}</span></el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="timeline.length" shadow="never" style="margin-top: 16px">
      <template #header><div>资金全链路溯源时间线</div></template>
      <trace-timeline :nodes="timeline" />
    </el-card>
  </div>
</template>

<script>
import TraceTimeline from '@/components/TraceTimeline.vue'
import { auditFundTrace } from '@/api/audit'
import { formatAmount } from '@/utils/format'

export default {
  name: 'AuditFundTrace',
  components: { TraceTimeline },
  data() {
    return {
      loading: false,
      projectId: this.$route.query.projectId || '',
      summary: null,
      timeline: [],
      chainIntact: true
    }
  },
  created() {
    if (this.projectId) this.load()
  },
  methods: {
    formatAmount,
    async load() {
      if (!this.projectId) {
        this.$message.warning('请输入项目ID')
        return
      }
      this.loading = true
      try {
        const res = await auditFundTrace(this.projectId)
        const data = res.data || {}
        this.timeline = data.timeline || []
        this.chainIntact = data.chainIntact
        this.summary = (data.chainInfo && data.chainInfo.evidenceCount !== undefined)
          ? data.chainInfo
          : {
            evidenceCount: data.evidenceCount,
            donationCount: data.donationCount,
            donationTotal: data.donationTotal,
            withdrawalCount: data.withdrawalCount,
            withdrawalTotal: data.withdrawalTotal,
            fundUsageCount: data.fundUsageCount,
            fundUsageTotal: data.fundUsageTotal,
            latestBlockHeight: data.latestBlockHeight,
            latestChainHash: data.latestChainHash
          }
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
