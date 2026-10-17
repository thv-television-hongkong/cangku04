<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>项目审核与资金审核</div></template>
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <!-- 项目审核（include 项目合规审查） -->
        <el-tab-pane label="项目审核" name="project">
          <el-form :inline="true" class="filter-bar">
            <el-form-item label="项目状态">
              <el-select v-model="query.status" clearable placeholder="全部" style="width: 160px">
                <el-option label="待审核" value="PENDING" />
                <el-option label="众筹中" value="ONLINE" />
                <el-option label="审核驳回" value="REJECTED" />
                <el-option label="已下架" value="OFFLINE" />
                <el-option label="待结项审核" value="FINISH_PENDING" />
                <el-option label="已完成" value="FINISHED" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="query.keyword" placeholder="项目名称 / 编号" style="width: 220px" />
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadProjects">查询</el-button></el-form-item>
          </el-form>

          <el-table :data="projects" border stripe size="small" v-loading="loading">
            <el-table-column prop="projectNo" label="项目编号" width="170" />
            <el-table-column prop="title" label="项目标题" min-width="170" />
            <el-table-column prop="clubName" label="发起社团" width="140" />
            <el-table-column prop="beneficiaryMasked" label="帮扶对象(脱敏)" width="130" />
            <el-table-column label="目标金额" width="110">
              <template #default="scope">￥{{ formatAmount(scope.row.targetAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="scope">
                <el-button v-if="scope.row.status === 'PENDING'" type="text" size="small" @click="openAudit(scope.row)">审核</el-button>
                <el-button v-if="scope.row.status === 'FINISH_PENDING'" link type="primary" @click="finishAudit(scope.row,true)">通过结项</el-button>
                <el-button v-if="scope.row.status === 'FINISH_PENDING'" link type="danger" @click="finishAudit(scope.row,false)">驳回结项</el-button>
                <el-button type="text" size="small" @click="$router.push('/admin/project/' + scope.row.projectId)">详情</el-button>
                <el-button v-if="scope.row.status === 'ONLINE'" type="text" size="small" style="color:#f56c6c"
                           @click="openOffline(scope.row)">下架</el-button>
                <el-button v-if="scope.row.status === 'OFFLINE'" type="text" size="small" @click="online(scope.row)">重新上架</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无待审核项目" /></template>
          </el-table>
          <div class="table-pagination">
            <el-pagination background layout="total, prev, pager, next" :total="total"
                           v-model:current-page="query.current" :page-size="query.size" @current-change="loadProjects" />
          </div>
        </el-tab-pane>

        <!-- 资金使用凭证审核 -->
        <el-tab-pane label="资金使用凭证审核" name="usage">
          <el-table :data="usages" border stripe size="small" v-loading="loadingFund">
            <el-table-column prop="usageNo" label="凭证编号" width="150" />
            <el-table-column prop="projectId" label="项目ID" width="80" />
            <el-table-column prop="usageDesc" label="资金使用说明" />
            <el-table-column label="金额" width="120">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="文件哈希" min-width="200">
              <template #default="scope"><span class="hash-text">{{ scope.row.fileHash }}</span></template>
            </el-table-column>
            <el-table-column prop="txId" label="存证交易" min-width="180" />
            <el-table-column label="审核状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="auditType(scope.row.auditStatus)">{{ auditLabel(scope.row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="scope">
                <el-button v-if="scope.row.auditStatus === 0" type="text" size="small"
                           @click="auditUsageRow(scope.row, 1)">通过</el-button>
                <el-button v-if="scope.row.auditStatus === 0" type="text" size="small" style="color:#f56c6c"
                           @click="auditUsageRow(scope.row, 2)">驳回</el-button>
                <el-button type="text" size="small" @click="download(scope.row.fileId)">下载原件</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 资金提取审核 -->
        <el-tab-pane label="资金提取审核" name="withdrawal">
          <el-table :data="withdrawals" border stripe size="small" v-loading="loadingFund">
            <el-table-column prop="withdrawalNo" label="提取单号" width="170" />
            <el-table-column prop="projectId" label="项目ID" width="80" />
            <el-table-column prop="stage" label="阶段" width="150" />
            <el-table-column prop="reason" label="提取用途" />
            <el-table-column label="金额" width="120">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="withdrawalType(scope.row.status)">{{ withdrawalLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="txId" label="存证交易" min-width="180" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="scope">
                <el-button v-if="scope.row.status === 0" type="text" size="small"
                           @click="auditWithdrawalRow(scope.row, 1)">通过</el-button>
                <el-button v-if="scope.row.status === 1" type="text" size="small"
                           @click="auditWithdrawalRow(scope.row, 3)">确认发放</el-button>
                <el-button v-if="scope.row.status === 0" type="text" size="small" style="color:#f56c6c"
                           @click="auditWithdrawalRow(scope.row, 2)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 审核弹窗（含合规审查项） -->
    <el-dialog title="众筹项目审核（项目合规审查）" v-model="auditVisible" width="620px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="项目标题">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item label="发起社团">{{ current.clubName }}</el-descriptions-item>
        <el-descriptions-item label="帮扶对象(脱敏)">{{ current.beneficiaryMasked }}</el-descriptions-item>
        <el-descriptions-item label="目标金额">￥{{ formatAmount(current.targetAmount) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="auditForm" label-width="120px">
        <el-form-item label="真实性">
          <el-radio-group v-model="auditForm.complianceItems.authenticity">
            <el-radio label="PASS">通过</el-radio><el-radio label="REJECT">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="合规性">
          <el-radio-group v-model="auditForm.complianceItems.compliance">
            <el-radio label="PASS">通过</el-radio><el-radio label="REJECT">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="材料完整性">
          <el-radio-group v-model="auditForm.complianceItems.materialComplete">
            <el-radio label="PASS">通过</el-radio><el-radio label="REJECT">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预算合理性">
          <el-radio-group v-model="auditForm.complianceItems.budgetReasonability">
            <el-radio label="PASS">通过</el-radio><el-radio label="REJECT">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="帮扶对象核实">
          <el-radio-group v-model="auditForm.complianceItems.beneficiaryVerified">
            <el-radio label="PASS">通过</el-radio><el-radio label="REJECT">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核结论">
          <el-radio-group v-model="auditForm.result">
            <el-radio label="PASS">审核通过并上架（摘要哈希上链）</el-radio>
            <el-radio label="REJECT">审核驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3"
                    placeholder="请填写审核意见，将通知给发起社团" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit">提交审核</el-button>
      </div></template>
    </el-dialog>

    <!-- 下架弹窗 -->
    <el-dialog title="下架违规项目" v-model="offlineVisible" width="520px">
      <el-form :model="offlineForm" label-width="90px">
        <el-form-item label="项目">{{ offlineForm.projectId }} - {{ current.title }}</el-form-item>
        <el-form-item label="下架原因">
          <el-input v-model="offlineForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon
                  title="下架属于监管处置行为，处置记录将上链存证，形成不可篡改的监管链路。" />
      </el-form>
      <template #footer><div>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="danger" @click="submitOffline">确认下架</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'
import { adminListProjects, auditProject, offlineProject, onlineProject } from '@/api/admin'
import { listFundUsages, auditFundUsage, listWithdrawals, auditWithdrawal } from '@/api/fund'
import { downloadFile } from '@/api/file'
import { formatAmount } from '@/utils/format'

export default {
  name: 'ProjectAudit',
  data() {
    return {
      activeTab: 'project',
      loading: false,
      loadingFund: false,
      projects: [],
      total: 0,
      query: { current: 1, size: 10, status: 'PENDING', keyword: '' },
      usages: [],
      withdrawals: [],
      auditVisible: false,
      offlineVisible: false,
      current: {},
      auditForm: {
        projectId: null, result: 'PASS', opinion: '',
        complianceItems: { authenticity: 'PASS', compliance: 'PASS', materialComplete: 'PASS', budgetReasonability: 'PASS', beneficiaryVerified: 'PASS' }
      },
      offlineForm: { projectId: null, reason: '' }
    }
  },
  created() {
    this.loadProjects()
    this.loadFunds()
  },
  methods: {
    async finishAudit(row,pass) {
      try {const {value}=await this.$prompt(pass?'确认项目执行与资金材料齐全，可填写意见':'请填写驳回原因','学校结项审核',{inputValidator:v=>pass||!!(v&&v.trim())||'请填写原因'});await request({url:`/admin/projects/${row.projectId}/finish-audit`,method:'post',data:{result:pass?'PASS':'REJECT',opinion:value||''}});this.$message.success('结项审核完成');await this.loadProjects()} catch(e){if(e!=='cancel'&&e!=='close')console.error(e)}
    },
    formatAmount,
    onTabChange() {
      if (this.activeTab === 'project') this.loadProjects()
      else this.loadFunds()
    },
    async loadProjects() {
      this.loading = true
      try {
        const res = await adminListProjects(this.query)
        this.projects = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async loadFunds() {
      this.loadingFund = true
      try {
        const [usages, withdrawals] = await Promise.all([
          listFundUsages({ current: 1, size: 50 }),
          listWithdrawals({ current: 1, size: 50 })
        ])
        this.usages = (usages.data && usages.data.records) || []
        this.withdrawals = (withdrawals.data && withdrawals.data.records) || []
      } finally {
        this.loadingFund = false
      }
    },
    statusType(status) {
      const map = { DRAFT: 'info', PENDING: 'warning', REJECTED: 'danger', ONLINE: 'success', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[status] || 'info'
    },
    auditType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'danger' }
      return map[status] || 'info'
    },
    auditLabel(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
      return map[status] || status
    },
    withdrawalType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'primary' }
      return map[status] || 'info'
    },
    withdrawalLabel(status) {
      const map = { 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已发放' }
      return map[status] || status
    },
    openAudit(row) {
      this.current = row
      this.auditForm = {
        projectId: row.projectId, result: 'PASS', opinion: '',
        complianceItems: { authenticity: 'PASS', compliance: 'PASS', materialComplete: 'PASS', budgetReasonability: 'PASS', beneficiaryVerified: 'PASS' }
      }
      this.auditVisible = true
    },
    async submitAudit() {
      await auditProject(this.auditForm)
      this.$message.success('审核完成' + (this.auditForm.result === 'PASS' ? '，项目已上架，摘要存证将在后台提交' : ''))
      this.auditVisible = false
      await this.loadProjects()
    },
    openOffline(row) {
      this.current = row
      this.offlineForm = { projectId: row.projectId, reason: '' }
      this.offlineVisible = true
    },
    async submitOffline() {
      await offlineProject(this.offlineForm)
      this.$message.success('项目已下架，处置摘要存证将在后台提交')
      this.offlineVisible = false
      await this.loadProjects()
    },
    async online(row) {
      await onlineProject(row.projectId)
      this.$message.success('项目已重新上架')
      await this.loadProjects()
    },
    async auditUsageRow(row, status) {
      await auditFundUsage({ usageId: row.usageId, auditStatus: status, auditOpinion: status === 1 ? '凭证审核通过' : '凭证不符合要求，请重新提交' })
      this.$message.success('资金凭证审核完成')
      await this.loadFunds()
    },
    async auditWithdrawalRow(row, status) {
      await auditWithdrawal({ withdrawalId: row.withdrawalId, status, auditOpinion: status === 2 ? '提取申请不符合要求' : '审核通过' })
      this.$message.success('资金提取处理完成')
      await this.loadFunds()
    },
    download(fileId) {
      if (fileId) downloadFile(fileId)
      else this.$message.warning('该凭证未上传原始文件')
    }
  }
}
</script>
