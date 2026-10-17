<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>异常交易预警（规则命中记录）</div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="处理状态">
          <el-select v-model="query.handleStatus" clearable placeholder="全部" style="width: 150px">
            <el-option label="未处理" :value="0" />
            <el-option label="已处理" :value="1" />
            <el-option label="已忽略" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警级别">
          <el-select v-model="query.level" clearable placeholder="全部" style="width: 130px">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MID" />
            <el-option label="高" value="HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-row :gutter="16" style="margin-bottom: 12px">
        <el-col :span="6" v-for="item in levelCards" :key="item.name">
          <el-card shadow="never"><div class="stat-card"><div class="stat-value">{{ item.value }}</div><div class="stat-label">{{ item.name }}</div></div></el-card>
        </el-col>
      </el-row>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="warningId" label="ID" width="70" />
        <el-table-column prop="ruleName" label="命中规则" width="200" />
        <el-table-column prop="bizType" label="业务类型" width="130" />
        <el-table-column prop="bizId" label="业务ID" width="100" />
        <el-table-column prop="projectId" label="项目ID" width="90" />
        <el-table-column label="预警级别" width="110">
          <template #default="scope">
            <el-tag size="small" :type="levelType(scope.row.warningLevel)">{{ levelLabel(scope.row.warningLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warningContent" label="预警内容" min-width="280" />
        <el-table-column prop="warningValue" label="触发值" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.handleStatus)">{{ statusLabel(scope.row.handleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleOpinion" label="处理意见" min-width="180" />
        <el-table-column label="预警时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.handleStatus === 0" type="text" size="small"
                       @click="openHandle(scope.row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无预警记录" /></template>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog title="处理异常预警" v-model="handleVisible" width="520px">
      <el-form :model="handleForm" label-width="100px">
        <el-form-item label="预警内容">{{ handleForm.content }}</el-form-item>
        <el-form-item label="处理结果">
          <el-radio-group v-model="handleForm.handleStatus">
            <el-radio :label="1">已处理</el-radio>
            <el-radio :label="2">忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="handleForm.opinion" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">提交</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listWarnings, handleWarning, warningStatistics } from '@/api/audit'
import { formatTime } from '@/utils/format'

export default {
  name: 'WarningList',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, handleStatus: 0, level: '' },
      statistics: [],
      handleVisible: false,
      handleForm: { warningId: null, content: '', handleStatus: 1, opinion: '' }
    }
  },
  computed: {
    levelCards() {
      const map = { LOW: '低风险预警', MID: '中风险预警', HIGH: '高风险预警' }
      return (this.statistics || []).map(i => ({ name: map[i.name] || i.name, value: i.value }))
    }
  },
  created() {
    this.load()
    this.loadStatistics()
  },
  methods: {
    formatTime,
    async load() {
      this.loading = true
      try {
        const res = await listWarnings(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async loadStatistics() {
      try {
        const res = await warningStatistics()
        this.statistics = res.data || []
      } catch (e) {
        this.statistics = []
      }
    },
    levelType(level) {
      const map = { LOW: 'info', MID: 'warning', HIGH: 'danger' }
      return map[level] || 'info'
    },
    levelLabel(level) {
      const map = { LOW: '低', MID: '中', HIGH: '高' }
      return map[level] || level
    },
    statusType(status) {
      const map = { 0: 'danger', 1: 'success', 2: 'info' }
      return map[status] || 'info'
    },
    statusLabel(status) {
      const map = { 0: '未处理', 1: '已处理', 2: '已忽略' }
      return map[status] || status
    },
    openHandle(row) {
      this.handleForm = { warningId: row.warningId, content: row.warningContent, handleStatus: 1, opinion: '' }
      this.handleVisible = true
    },
    async submitHandle() {
      await handleWarning(this.handleForm.warningId, this.handleForm.handleStatus, this.handleForm.opinion)
      this.$message.success('预警已处理')
      this.handleVisible = false
      await this.load()
      await this.loadStatistics()
    }
  }
}
</script>
