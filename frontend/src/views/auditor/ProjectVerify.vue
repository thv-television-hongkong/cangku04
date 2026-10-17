<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>查看众筹项目（include 项目真实性核验）</div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="项目状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="众筹中" value="ONLINE" />
            <el-option label="已完成" value="FINISHED" />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="项目名称 / 编号" style="width: 220px" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="projectNo" label="项目编号" width="170" />
        <el-table-column prop="title" label="项目标题" min-width="160" />
        <el-table-column prop="clubName" label="发起社团" width="140" />
        <el-table-column prop="beneficiaryMasked" label="帮扶对象(脱敏)" width="130" />
        <el-table-column label="目标/已筹" width="170">
          <template #default="scope">
            <div>￥{{ formatAmount(scope.row.currentAmount) }} / ￥{{ formatAmount(scope.row.targetAmount) }}</div>
            <el-progress :percentage="scope.row.fundingPercent || 0" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="链上存证" min-width="180">
          <template #default="scope">
            <div v-if="scope.row.txId" class="chain-tag">{{ scope.row.txId }}</div>
            <div v-if="scope.row.blockHeight">区块高度：{{ scope.row.blockHeight }}</div>
            <span v-if="!scope.row.txId" class="legal-tip">未上链</span>
          </template>
        </el-table-column>
        <el-table-column label="核验操作" width="220" fixed="right">
          <template #default="scope">
            <el-button type="text" size="small" @click="verifyProject(scope.row)">真实性核验</el-button>
            <el-button type="text" size="small" @click="fundTrace(scope.row)">资金链追踪</el-button>
            <el-button type="text" size="small" @click="$router.push('/donor/project/' + scope.row.projectId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog title="项目真实性核验结果" v-model="verifyVisible" width="820px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="项目编号">{{ detail.projectNo }}</el-descriptions-item>
        <el-descriptions-item label="项目标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="发起社团">{{ detail.clubName }}</el-descriptions-item>
        <el-descriptions-item label="帮扶对象(脱敏)">{{ detail.beneficiaryMasked }}</el-descriptions-item>
        <el-descriptions-item label="链上存证条数">{{ (detail.evidenceList || []).length }}</el-descriptions-item>
        <el-descriptions-item label="审核记录数">{{ (detail.auditList || []).length }}</el-descriptions-item>
        <el-descriptions-item label="项目摘要哈希" :span="2">
          <span class="hash-text">{{ detail.dataHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="存证交易ID" :span="2">
          <span class="chain-tag">{{ detail.txId }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">审核记录（真实性 / 合规性核验依据）</div>
      <el-table :data="detail.auditList || []" border size="small">
        <el-table-column prop="auditType" label="审核类型" width="120" />
        <el-table-column prop="result" label="结果" width="100" />
        <el-table-column prop="opinion" label="审核意见" min-width="240" />
        <el-table-column prop="auditorName" label="审核人" width="120" />
        <el-table-column prop="auditTime" label="审核时间" width="170" />
      </el-table>

      <div class="section-title">链上存证明细</div>
      <evidence-table :records="detail.evidenceList || []" />
    </el-dialog>
  </div>
</template>

<script>
import EvidenceTable from '@/components/EvidenceTable.vue'
import { auditProjects, auditProjectDetail } from '@/api/audit'
import { formatAmount } from '@/utils/format'

export default {
  name: 'ProjectVerify',
  components: { EvidenceTable },
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, status: '', keyword: '' },
      verifyVisible: false,
      detail: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatAmount,
    async load() {
      this.loading = true
      try {
        const res = await auditProjects(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    statusType(status) {
      const map = { DRAFT: 'info', PENDING: 'warning', REJECTED: 'danger', ONLINE: 'success', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[status] || 'info'
    },
    async verifyProject(row) {
      const res = await auditProjectDetail(row.projectId)
      this.detail = res.data || {}
      this.verifyVisible = true
    },
    fundTrace(row) {
      this.$router.push('/auditor/fund-trace?projectId=' + row.projectId)
    }
  }
}
</script>
