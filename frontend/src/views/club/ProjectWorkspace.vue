<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header><div>
        <span>项目执行管理 - {{ detail.title }}</span>
        <div style="float: right">
          <el-tag size="small" :type="statusType">{{ detail.statusLabel }}</el-tag>
          <el-button v-if="detail.status==='ONLINE'" type="primary" link @click="applyFinish">申请结项</el-button>
          <el-button type="text" @click="$router.push('/donor/project/' + $route.params.id)">查看项目详情</el-button>
        </div>
      </div></template>
      <el-row :gutter="16">
        <el-col :span="6"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(detail.currentAmount) }}</div><div class="stat-label">已筹金额</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(detail.targetAmount) }}</div><div class="stat-label">目标金额</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">{{ detail.donorCount || 0 }}</div><div class="stat-label">捐赠人数</div></div></el-col>
        <el-col :span="6"><div class="stat-card"><div class="stat-value">{{ detail.progressPercent || 0 }}%</div><div class="stat-label">执行进度</div></div></el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="审核状态" name="audit">
          <el-timeline>
            <el-timeline-item v-for="item in audits" :key="item.auditId" :timestamp="formatTime(item.auditTime)"
                              :type="item.result === 'PASS' ? 'success' : (item.result === 'REJECT' || item.result === 'OFFLINE' ? 'danger' : 'primary')">
              <div>{{ item.auditType }} - {{ item.result || '已提交' }}</div>
              <div>{{ item.opinion }}</div>
              <div v-if="item.complianceItems" class="legal-tip">
                合规审查项：{{ JSON.stringify(item.complianceItems) }}
              </div>
            </el-timeline-item>
            <el-timeline-item v-if="!audits.length" type="info">暂无审核记录</el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="捐赠记录" name="donation">
          <div class="section-title">捐赠明细（捐赠人名称已脱敏展示）</div>
          <el-table :data="donations" border size="small">
            <el-table-column prop="donorName" label="捐赠人" width="140" />
            <el-table-column prop="total" label="累计金额" width="130">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.total) }}</span></template>
            </el-table-column>
            <el-table-column prop="times" label="捐赠次数" width="100" />
            <el-table-column label="最近捐赠" width="180">
              <template #default="scope">{{ formatTime(scope.row.lastTime) }}</template>
            </el-table-column>
          </el-table>
          <div class="section-title">资金流向</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="累计筹款">￥{{ formatAmount(fundFlow.currentAmount) }}</el-descriptions-item>
            <el-descriptions-item label="已提取">￥{{ formatAmount(fundFlow.withdrawTotal) }}</el-descriptions-item>
            <el-descriptions-item label="已使用">￥{{ formatAmount(fundUsageTotal) }}</el-descriptions-item>
            <el-descriptions-item label="可提取余额">￥{{ formatAmount(balance) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="项目进度" name="progress">
          <el-form :inline="true">
            <el-form-item label="进度标题"><el-input v-model="progressForm.title" style="width: 220px" /></el-form-item>
            <el-form-item label="完成度">
              <el-input-number v-model="progressForm.progressPercent" :min="0" :max="100" /> %
            </el-form-item>
            <el-form-item label="说明"><el-input v-model="progressForm.content" style="width: 320px" /></el-form-item>
            <el-form-item>
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="onProgressFile" action="#">
                <el-button size="small" icon="el-icon-upload2">附件</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="saveProgress">提交进度并上链</el-button></el-form-item>
          </el-form>
          <el-timeline>
            <el-timeline-item v-for="item in progressList" :key="item.progressId" :timestamp="formatTime(item.createTime)">
              <div><b>{{ item.title }}</b>（{{ item.progressPercent }}%）</div>
              <div>{{ item.content }}</div>
              <div v-if="item.txId" class="chain-tag">存证交易：{{ item.txId }} · 区块高度：{{ item.blockHeight }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="项目材料" name="material">
          <el-form :inline="true">
            <el-form-item label="材料类型">
              <el-select v-model="materialForm.materialType" style="width: 160px">
                <el-option label="项目计划书" value="PROJECT_PLAN" />
                <el-option label="证明材料" value="PROOF" />
                <el-option label="帮扶材料" value="BENEFICIARY" />
                <el-option label="其他材料" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="材料名称"><el-input v-model="materialForm.materialName" style="width: 220px" /></el-form-item>
            <el-form-item label="选择文件">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="onMaterialFile" action="#">
                <el-button size="small" icon="el-icon-upload2">上传材料</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="saveMaterial">提交并上链存证</el-button></el-form-item>
          </el-form>
          <el-table :data="materials" border size="small">
            <el-table-column prop="materialName" label="材料名称" />
            <el-table-column prop="materialType" label="类型" width="130" />
            <el-table-column label="文件哈希" min-width="200">
              <template #default="scope"><span class="hash-text">{{ scope.row.fileHash }}</span></template>
            </el-table-column>
            <el-table-column prop="txId" label="交易 ID" min-width="180" />
            <el-table-column prop="blockHeight" label="区块高度" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="资金提取" name="withdrawal">
          <el-form :inline="true">
            <el-form-item label="提取金额">
              <el-input-number v-model="withdrawalForm.amount" :min="1" :precision="2" /> 元
            </el-form-item>
            <el-form-item label="阶段说明"><el-input v-model="withdrawalForm.stage" style="width: 200px" /></el-form-item>
            <el-form-item label="提取用途"><el-input v-model="withdrawalForm.reason" style="width: 300px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="applyWithdrawal">申请提取并上链存证</el-button></el-form-item>
          </el-form>
          <el-table :data="withdrawals" border size="small">
            <el-table-column prop="withdrawalNo" label="提取单号" width="180" />
            <el-table-column label="金额" width="120">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
            </el-table-column>
            <el-table-column prop="stage" label="阶段" width="150" />
            <el-table-column prop="reason" label="用途" />
            <el-table-column label="状态" width="110">
              <template #default="scope">
                <el-tag size="small" :type="withdrawalType(scope.row.status)">{{ withdrawalLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="txId" label="交易 ID" min-width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="资金使用凭证" name="usage">
          <el-form :inline="true">
            <el-form-item label="金额"><el-input-number v-model="usageForm.amount" :min="1" :precision="2" /> 元</el-form-item>
            <el-form-item label="使用说明"><el-input v-model="usageForm.usageDesc" style="width: 320px" /></el-form-item>
            <el-form-item label="发票 / 收据">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="onUsageFile" action="#">
                <el-button size="small" icon="el-icon-upload2">上传凭证</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="uploadUsage">提交并上链存证</el-button></el-form-item>
          </el-form>
          <el-table :data="usages" border size="small">
            <el-table-column prop="usageNo" label="凭证编号" width="150" />
            <el-table-column prop="usageDesc" label="使用说明" />
            <el-table-column label="金额" width="120">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="审核状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="usageType(scope.row.auditStatus)">{{ usageLabel(scope.row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="txId" label="交易 ID" min-width="180" />
            <el-table-column label="溯源" width="150">
              <template #default="scope">
                <el-button type="text" size="small"
                           @click="$router.push('/trace/query?projectId=' + $route.params.id)">查看存证溯源</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import {
  getProject, clubProjectAudits, clubProjectDonations, clubProgress, addProgress,
  clubMaterials, uploadMaterial, finishProject
} from '@/api/project'
import { applyWithdrawal, listWithdrawals, uploadFundUsage, listFundUsages, fundOverview } from '@/api/fund'
import { uploadFile } from '@/api/file'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'ProjectWorkspace',
  data() {
    return {
      loading: false,
      activeTab: 'audit',
      detail: {},
      fundFlow: {},
      audits: [],
      donations: [],
      progressList: [],
      materials: [],
      withdrawals: [],
      usages: [],
      progressForm: { projectId: null, title: '', content: '', progressPercent: 0, fileId: null, fileHash: '' },
      materialForm: { projectId: null, materialType: 'PROOF', materialName: '', fileId: null, fileName: '', fileHash: '', fileSize: 0 },
      withdrawalForm: { projectId: null, amount: 1000, reason: '', stage: '' },
      usageForm: { projectId: null, amount: 1000, usageDesc: '', fileId: null, fileName: '', fileHash: '' }
    }
  },
  computed: {
    statusType() {
      const map = { DRAFT: 'info', PENDING: 'warning', REJECTED: 'danger', ONLINE: 'success', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[this.detail.status] || 'info'
    },
    fundUsageTotal() {
      return (this.usages || []).filter(u => u.auditStatus === 1)
        .reduce((sum, u) => sum + Number(u.amount || 0), 0)
    },
    balance() {
      return (Number(this.fundFlow.currentAmount || 0)) - (Number(this.fundFlow.withdrawTotal || 0))
    }
  },
  async created() {
    const id = this.$route.params.id
    this.progressForm.projectId = id
    this.materialForm.projectId = id
    this.withdrawalForm.projectId = id
    this.usageForm.projectId = id
    await this.loadAll()
  },
  methods: {
    async applyFinish() {
      try { await this.$confirm('提交后等待学校审核，期间继续保留项目执行和财务记录。','申请结项'); await finishProject(this.detail.projectId);this.$message.success('结项申请已提交学校审核');await this.loadAll() } catch(e) { if(e!=='cancel'&&e!=='close') console.error(e) }
    },
    formatAmount,
    formatTime,
    async loadAll() {
      this.loading = true
      try {
        const id = this.$route.params.id
        const [detail, audits, donations, progress, materials, withdrawals, usages, overview] = await Promise.all([
          getProject(id),
          clubProjectAudits(id),
          clubProjectDonations(id, 50),
          clubProgress(id),
          clubMaterials(id),
          listWithdrawals({ projectId: id, current: 1, size: 50 }),
          listFundUsages({ projectId: id, current: 1, size: 50 }),
          fundOverview(id)
        ])
        this.detail = detail.data || {}
        this.audits = audits.data || []
        this.donations = donations.data || []
        this.progressList = progress.data || []
        this.materials = materials.data || []
        this.withdrawals = (withdrawals.data && withdrawals.data.records) || []
        this.usages = (usages.data && usages.data.records) || []
        this.fundFlow = overview.data || {}
      } finally {
        this.loading = false
      }
    },
    async onProgressFile(file) {
      const res = await uploadFile(file.raw, 'PROJECT_PROGRESS', this.$route.params.id)
      this.progressForm.fileId = res.data.fileId
      this.progressForm.fileHash = res.data.fileHash
    },
    async onMaterialFile(file) {
      const res = await uploadFile(file.raw, 'PROJECT_MATERIAL', this.$route.params.id)
      this.materialForm.fileId = res.data.fileId
      this.materialForm.fileName = res.data.fileName
      this.materialForm.fileHash = res.data.fileHash
      this.materialForm.fileSize = res.data.fileSize
      if (!this.materialForm.materialName) this.materialForm.materialName = res.data.fileName
    },
    async onUsageFile(file) {
      const res = await uploadFile(file.raw, 'FUND_USAGE', this.$route.params.id)
      this.usageForm.fileId = res.data.fileId
      this.usageForm.fileName = res.data.fileName
      this.usageForm.fileHash = res.data.fileHash
    },
    async saveProgress() {
      if (!this.progressForm.title) {
        this.$message.warning('请填写进度标题')
        return
      }
      await addProgress(this.progressForm)
      this.$message.success('进度已保存，摘要存证已排队；请稍后刷新查看回执')
      this.progressForm = { projectId: this.$route.params.id, title: '', content: '', progressPercent: 0, fileId: null, fileHash: '' }
      await this.loadAll()
    },
    async saveMaterial() {
      if (!this.materialForm.fileHash) {
        this.$message.warning('请先上传材料文件')
        return
      }
      await uploadMaterial(this.materialForm)
      this.$message.success('材料已保存，摘要存证已排队；请稍后刷新查看回执')
      this.materialForm = { projectId: this.$route.params.id, materialType: 'PROOF', materialName: '', fileId: null, fileName: '', fileHash: '', fileSize: 0 }
      await this.loadAll()
    },
    async applyWithdrawal() {
      await applyWithdrawal(this.withdrawalForm)
      this.$message.success('资金提取申请已提交并上链存证')
      await this.loadAll()
    },
    async uploadUsage() {
      if (!this.usageForm.fileHash) {
        this.$message.warning('请先上传发票 / 收据')
        return
      }
      await uploadFundUsage(this.usageForm)
      this.$message.success('资金使用凭证已提交并上链存证')
      this.usageForm = { projectId: this.$route.params.id, amount: 1000, usageDesc: '', fileId: null, fileName: '', fileHash: '' }
      await this.loadAll()
    },
    withdrawalLabel(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已发放' }
      return map[status] || status
    },
    withdrawalType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'primary' }
      return map[status] || 'info'
    },
    usageLabel(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
      return map[status] || status
    },
    usageType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'danger' }
      return map[status] || 'info'
    }
  }
}
</script>
