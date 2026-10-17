<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>审计报告管理（include 导出存证数据）</div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="报告类型">
          <el-select v-model="form.reportType" style="width: 160px">
            <el-option label="综合审计" value="COMPREHENSIVE" />
            <el-option label="项目审计" value="PROJECT" />
            <el-option label="资金审计" value="FUND" />
            <el-option label="存证审计" value="EVIDENCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目ID">
          <el-input v-model="form.projectId" placeholder="选填，指定项目审计" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="generating" @click="generate">生成审计报告</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="reportNo" label="报告编号" width="170" />
        <el-table-column prop="title" label="报告标题" min-width="220" />
        <el-table-column prop="reportType" label="类型" width="110" />
        <el-table-column prop="evidenceCount" label="存证条数" width="100" />
        <el-table-column prop="donationCount" label="捐赠笔数" width="100" />
        <el-table-column label="涉及金额" width="130">
          <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="哈希核验结论" width="140">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.verifyResult === 'ALL_MATCH' ? 'success' : 'warning'">
              {{ ({ ALL_MATCH:'摘要一致', NO_EVIDENCE:'无存证', MISMATCH:'存在不一致', UNVERIFIED:'尚未核验完成', UNVERIFIED_OR_MISMATCH:'需重新核验' })[scope.row.verifyResult] || '待核验' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="txId" label="报告存证交易" min-width="200" />
        <el-table-column prop="generateUserName" label="生成人" width="110" />
        <el-table-column label="生成时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.generateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="scope">
            <el-button type="text" size="small" @click="viewSummary(scope.row)">报告摘要</el-button>
            <el-button type="text" size="small" @click="exportRows(scope.row)">导出存证</el-button>
            <el-button type="text" size="small" @click="downloadReport(scope.row)">下载报告</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无审计报告" /></template>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog title="审计报告摘要" v-model="summaryVisible" width="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="报告编号">{{ current.reportNo }}</el-descriptions-item>
        <el-descriptions-item label="报告类型">{{ current.reportType }}</el-descriptions-item>
        <el-descriptions-item label="统计区间" :span="2">
          {{ formatTime(current.startTime) }} 至 {{ formatTime(current.endTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="报告文件哈希" :span="2">
          <span class="hash-text">{{ current.fileHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="报告存证交易" :span="2">
          <span class="chain-tag">{{ current.txId }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <div class="section-title">报告摘要</div>
      <p>{{ current.summary }}</p>
    </el-dialog>
  </div>
</template>

<script>
import { generateReport, listReports, exportReport } from '@/api/audit'
import { downloadFile } from '@/api/file'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'AuditReport',
  data() {
    return {
      loading: false,
      generating: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10 },
      form: { reportType: 'COMPREHENSIVE', projectId: null },
      summaryVisible: false,
      current: {}
    }
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
        const res = await listReports(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async generate() {
      this.generating = true
      try {
        const params = { reportType: this.form.reportType }
        if (this.form.projectId) params.projectId = this.form.projectId
        const res = await generateReport(params)
        this.$message.success('报告已生成，摘要存证已进入队列；请稍后刷新查看回执')
        await this.load()
      } finally {
        this.generating = false
      }
    },
    viewSummary(row) {
      this.current = row
      this.summaryVisible = true
    },
    async exportRows(row) {
      const res = await exportReport(row.reportId)
      const rows = res.data || []
      if (rows.length === 0) {
        this.$message.warning('该报告暂无可导出的存证数据')
        return
      }
      const headers = Object.keys(rows[0])
      const csv = [headers.join(',')].concat(
        rows.map(r => headers.map(h => '"' + String(r[h] === null || r[h] === undefined ? '' : r[h]).replace(/"/g, '""') + '"').join(','))
      ).join('\n')
      const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = row.reportNo + '_存证数据.csv'
      link.click()
      this.$message.success('已导出 ' + rows.length + ' 条存证数据')
    },
    downloadReport(row) {
      if (row.fileId) {
        downloadFile(row.fileId)
      } else {
        this.$message.warning('报告文件不存在')
      }
    }
  }
}
</script>
