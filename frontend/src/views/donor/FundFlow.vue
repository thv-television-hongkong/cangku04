<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header><div>
        <span>{{ overview.projectTitle }} - 资金流向</span>
        <el-button type="text" style="float: right" @click="$router.push('/trace/query?projectId=' + $route.params.id)">
          查看链上全流程溯源
        </el-button>
      </div></template>
      <el-row :gutter="16">
        <el-col :span="6"><el-card shadow="never"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(overview.raisedAmount) }}</div><div class="stat-label">累计筹款</div></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(overview.withdrawnAmount) }}</div><div class="stat-label">已提取</div></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(overview.usedAmount) }}</div><div class="stat-label">已审核使用</div></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(overview.balanceAmount) }}</div><div class="stat-label">待使用结余</div></div></el-card></el-col>
      </el-row>

      <div class="section-title">资金使用明细（发票 / 收据哈希均已上链）</div>
      <el-table :data="overview.usageList || []" border stripe size="small">
        <el-table-column prop="usageNo" label="凭证编号" width="150" />
        <el-table-column prop="usageDesc" label="资金使用说明" />
        <el-table-column label="金额" width="120">
          <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="使用时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.useTime) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="auditType(scope.row.auditStatus)">{{ auditLabel(scope.row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件哈希" min-width="200">
          <template #default="scope"><span class="hash-text">{{ scope.row.fileHash }}</span></template>
        </el-table-column>
        <el-table-column label="存证交易" min-width="180">
          <template #default="scope"><span class="chain-tag">{{ scope.row.txId || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button v-if="scope.row.fileId" type="text" size="small" @click="download(scope.row.fileId)">下载原件</el-button>
            <el-button type="text" size="small" @click="verify(scope.row)">哈希验真</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { fundOverview } from '@/api/fund'
import { downloadFile } from '@/api/file'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'FundFlow',
  data() {
    return { loading: false, overview: {} }
  },
  created() {
    this.load()
  },
  methods: {
    formatAmount,
    formatTime,
    async load() {
      this.loading = true
      try {
        const res = await fundOverview(this.$route.params.id)
        this.overview = res.data || {}
      } finally {
        this.loading = false
      }
    },
    download(fileId) {
      downloadFile(fileId)
    },
    verify(row) {
      this.$router.push({ path: '/trace/verify', query: { bizType: 'FUND_USAGE', bizId: row.usageId, hash: row.fileHash } })
    },
    auditLabel(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
      return map[status] || status
    },
    auditType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'danger' }
      return map[status] || 'info'
    }
  }
}
</script>
