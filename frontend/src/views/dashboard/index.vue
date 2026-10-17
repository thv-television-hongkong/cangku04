<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="6" v-for="item in statCards" :key="item.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><div>链上存证概览</div></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="存证来源">{{ chain.chainSource || '-' }}</el-descriptions-item>
            <el-descriptions-item label="链码版本">{{ chain.chaincodeVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="通道名称">{{ chain.channelName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="链码名称">{{ chain.chaincodeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="存证总条数">{{ chain.totalEvidence || 0 }}</el-descriptions-item>
            <el-descriptions-item label="链式完整性">
              <el-tag size="small" :type="chain.chainIntact ? 'success' : 'danger'">
                {{ chain.chainIntact ? '完整未篡改' : '存在断裂' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="背书策略" :span="2">
              {{ chain.endorsementPolicy || "AND('Org1MSP.peer','Org2MSP.peer')" }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><div>存证业务分布</div></template>
          <stat-chart type="pie" :data="bizTypeChart" :height="260" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><div>
        <span>最新公益众筹项目</span>
        <el-button type="text" style="float: right" @click="$router.push('/donor/projects')">查看更多</el-button>
      </div></template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="project in projects" :key="project.projectId">
          <project-card
            :project="project"
            :can-donate="role === 'STUDENT'"
            @detail="id => $router.push('/donor/project/' + id)"
            @favorite="toggleFavorite(project.projectId)"
            @share="share(project)"
            @donate="id => $router.push('/donor/project/' + id)"
          />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import ProjectCard from '@/components/ProjectCard.vue'
import StatChart from '@/components/StatChart.vue'
import { listProjects, toggleFavorite, shareProject } from '@/api/project'
import { chainOverview } from '@/api/trace'
import { dashboard } from '@/api/admin'
import { formatAmount } from '@/utils/format'

export default {
  name: 'Dashboard',
  components: { ProjectCard, StatChart },
  data() {
    return {
      chain: {},
      data: {},
      projects: []
    }
  },
  computed: {
    role() {
      return this.$store.getters.role
    },
    statCards() {
      const stats = this.data || {}
      return [
        { label: '公益项目总数', value: stats.projectCount || 0 },
        { label: '累计筹款金额（元）', value: formatAmount(stats.raisedAmount || stats.donationTotal || 0) },
        { label: '捐赠笔数', value: stats.donationCount || 0 },
        { label: '链上存证条数', value: stats.evidenceCount || this.chain.totalEvidence || 0 }
      ]
    },
    bizTypeChart() {
      const byType = (this.chain.stats && this.chain.stats.byBizType) || this.chain.byBizType || {}
      const labelMap = {
        PROJECT: '众筹项目',
        DONATION: '捐赠记录',
        FUND_USAGE: '资金凭证',
        MATERIAL: '帮扶材料',
        WITHDRAWAL: '资金提取',
        PROJECT_PROGRESS: '项目进度',
        PROJECT_STATUS: '状态变更',
        AUDIT_REPORT: '审计报告'
      }
      return Object.keys(byType).map(k => ({ name: labelMap[k] || k, value: byType[k] }))
    }
  },
  async created() {
    await this.loadChain()
    await this.loadProjects()
    if (this.role === 'ADMIN' || this.role === 'AUDITOR') {
      await this.loadStatistics()
    }
  },
  methods: {
    async loadChain() {
      try {
        const res = await chainOverview()
        this.chain = res.data || {}
      } catch (e) {
        this.chain = {}
      }
    },
    async loadProjects() {
      const res = await listProjects({ current: 1, size: 3, sort: 'hot' })
      this.projects = (res.data && res.data.records) || []
    },
    async loadStatistics() {
      try {
        const res = await dashboard()
        this.data = res.data || {}
      } catch (e) {
        this.data = {}
      }
    },
    async toggleFavorite(projectId) {
      const res = await toggleFavorite(projectId)
      this.$message.success(res.message || '操作成功')
    },
    async share(project) {
      await shareProject(project.projectId, 'LINK')
      const link = window.location.origin + '/#/donor/project/' + project.projectId
      this.$alert(link, '项目分享链接（已记录分享行为）', { confirmButtonText: '知道了' })
    }
  }
}
</script>
