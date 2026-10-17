<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="6" v-for="item in cards" :key="item.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="never"><stat-chart type="pie" title="项目状态分布" :data="statusChart" :height="280" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><stat-chart type="pie" title="帮扶类别分布" :data="categoryChart" :height="280" /></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never"><stat-chart type="bar" title="用户角色分布" :data="roleChart" :height="280" /></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never"><stat-chart type="line" title="近 30 天捐赠趋势（元）" :data="trendChart" :height="300" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><div>存证与预警概览</div></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="链上存证条数">{{ data.evidenceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="存证来源">{{ data.chainSource || '-' }}</el-descriptions-item>
            <el-descriptions-item label="链码版本">{{ data.chaincodeVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="未处理预警">{{ data.warningUnhandled || 0 }}</el-descriptions-item>
            <el-descriptions-item label="待处理投诉">{{ data.complaintPending || 0 }}</el-descriptions-item>
            <el-descriptions-item label="目标金额合计">￥{{ formatAmount(data.targetAmount) }}</el-descriptions-item>
            <el-descriptions-item label="已筹金额合计">￥{{ formatAmount(data.raisedAmount) }}</el-descriptions-item>
            <el-descriptions-item label="捐赠笔数">{{ data.donationCount || 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <div class="section-title">预警级别分布</div>
          <stat-chart type="bar" :data="warningChart" :height="180" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import StatChart from '@/components/StatChart.vue'
import { statistics } from '@/api/admin'
import { formatAmount } from '@/utils/format'

export default {
  name: 'AdminStatistics',
  components: { StatChart },
  data() {
    return { loading: false, data: {} }
  },
  computed: {
    cards() {
      const d = this.data || {}
      return [
        { label: '注册用户数', value: d.userCount || 0 },
        { label: '公益项目数', value: d.projectCount || 0 },
        { label: '累计筹款（元）', value: formatAmount(d.raisedAmount) },
        { label: '链上存证（条）', value: d.evidenceCount || 0 }
      ]
    },
    statusChart() {
      return (this.data.projectByStatus || []).map(i => ({ name: this.statusLabel(i.name), value: i.value }))
    },
    categoryChart() {
      const map = { EDUCATION: '教育帮扶', MEDICAL: '医疗救助', POVERTY: '困难帮扶', DISASTER: '突发救助', OTHER: '其他公益' }
      return (this.data.projectByCategory || []).map(i => ({ name: map[i.name] || i.name, value: i.value }))
    },
    roleChart() {
      const map = { STUDENT: '普通学生', CLUB: '公益社团', ADMIN: '平台管理员', AUDITOR: '监管审计' }
      return (this.data.userByRole || []).map(i => ({ name: map[i.name] || i.name, value: i.value }))
    },
    trendChart() {
      return (this.data.donationTrend || []).map(i => ({ name: i.name, value: i.value }))
    },
    warningChart() {
      return (this.data.warningByLevel || []).map(i => ({ name: this.warningLabel(i.name), value: i.value }))
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
        const res = await statistics()
        this.data = res.data || {}
      } finally {
        this.loading = false
      }
    },
    statusLabel(status) {
      const map = { DRAFT: '草稿', PENDING: '待审核', REJECTED: '审核驳回', ONLINE: '众筹中', OFFLINE: '已下架', FINISHED: '已完成', SETTLED: '已结算' }
      return map[status] || status
    },
    warningLabel(level) {
      const map = { LOW: '低风险', MID: '中风险', HIGH: '高风险' }
      return map[level] || level
    }
  }
}
</script>
