<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header><div class="detail-header">
        <div>
          <span class="detail-title">{{ detail.title }}</span>
          <el-tag size="small" :type="statusType">{{ detail.statusLabel }}</el-tag>
          <el-tag size="small" type="info">{{ detail.categoryLabel }}</el-tag>
        </div>
        <div>
          <el-button v-if="role === 'STUDENT' && detail.status === 'ONLINE'" type="danger" @click="openDonate">我要捐赠</el-button>
          <el-button @click="favorite">
            <i :class="detail.favorited ? 'el-icon-star-on' : 'el-icon-star-off'" />
            {{ detail.favorited ? '已收藏' : '收藏项目' }}
          </el-button>
          <el-button @click="share">分享项目</el-button>
          <el-button @click="$router.push('/trace/query?projectId=' + detail.projectId)">溯源查询</el-button>
        </div>
      </div></template>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-progress :percentage="detail.fundingPercent || 0" :stroke-width="16" />
          <el-row :gutter="16" style="margin-top: 16px">
            <el-col :span="8"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(detail.currentAmount) }}</div><div class="stat-label">已筹金额</div></div></el-col>
            <el-col :span="8"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(detail.targetAmount) }}</div><div class="stat-label">目标金额</div></div></el-col>
            <el-col :span="8"><div class="stat-card"><div class="stat-value">{{ detail.donorCount || 0 }}</div><div class="stat-label">捐赠人数</div></div></el-col>
          </el-row>
        </el-col>
        <el-col :span="8">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="项目编号">{{ detail.projectNo }}</el-descriptions-item>
            <el-descriptions-item label="发起社团">{{ detail.clubName }}</el-descriptions-item>
            <el-descriptions-item label="帮扶对象（脱敏）">{{ detail.beneficiaryMasked }}</el-descriptions-item>
            <el-descriptions-item label="筹款期限">
              {{ formatTime(detail.startTime, 'YYYY-MM-DD') }} 至 {{ formatTime(detail.endTime, 'YYYY-MM-DD') }}
            </el-descriptions-item>
            <el-descriptions-item label="剩余天数">{{ detail.remainingDays }} 天</el-descriptions-item>
            <el-descriptions-item label="执行进度">{{ detail.progressPercent || 0 }}%</el-descriptions-item>
          </el-descriptions>
        </el-col>
      </el-row>

      <!-- 链上存证（include 查看区块链存证） -->
      <el-alert
        v-if="detail.txId"
        style="margin-top: 16px"
        type="success"
        :closable="false"
        show-icon
        :title="'项目摘要哈希已上链存证 · 交易 ID：' + detail.txId + ' · 区块高度：' + detail.blockHeight"
      />
      <el-alert
        v-else
        style="margin-top: 16px"
        type="warning"
        :closable="false"
        show-icon
        title="项目摘要存证待提交或处理中，可稍后刷新查看"
      />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="项目介绍" name="intro">
          <div class="section-title">项目描述</div>
          <p>{{ detail.description }}</p>
          <div class="section-title">资金使用计划</div>
          <p>{{ detail.fundsPlan }}</p>
          <div class="section-title">帮扶对象情况（已脱敏）</div>
          <p>{{ detail.beneficiaryDesc }}</p>
        </el-tab-pane>

        <el-tab-pane label="资金流向" name="fund">
          <el-row :gutter="16">
            <el-col :span="6" v-for="item in fundCards" :key="item.label">
              <el-card shadow="never"><div class="stat-card"><div class="stat-value">￥{{ formatAmount(item.value) }}</div><div class="stat-label">{{ item.label }}</div></div></el-card>
            </el-col>
          </el-row>
          <el-table :data="detail.fundUsageList || []" border size="small" style="margin-top: 16px">
            <el-table-column prop="usageNo" label="凭证编号" width="150" />
            <el-table-column prop="usageDesc" label="资金使用说明" />
            <el-table-column label="金额" width="120">
              <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="审核状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="auditType(scope.row.auditStatus)">{{ auditLabel(scope.row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="文件哈希" min-width="180">
              <template #default="scope"><span class="hash-text">{{ scope.row.fileHash }}</span></template>
            </el-table-column>
            <el-table-column label="交易 ID" min-width="160">
              <template #default="scope"><span class="chain-tag">{{ scope.row.txId || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="原始文件" width="100">
              <template #default="scope">
                <el-button v-if="scope.row.fileId" type="text" size="small"
                           @click="download(scope.row.fileId)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="项目进度" name="progress">
          <el-timeline>
            <el-timeline-item v-for="item in detail.progressList || []" :key="item.progressId"
                              :timestamp="formatTime(item.createTime)" placement="top">
              <el-card shadow="never">
                <div><b>{{ item.title }}</b>（完成 {{ item.progressPercent }}%）</div>
                <div>{{ item.content }}</div>
                <div v-if="item.txId" class="chain-tag">存证交易：{{ item.txId }} · 区块高度：{{ item.blockHeight }}</div>
              </el-card>
            </el-timeline-item>
            <el-timeline-item v-if="!(detail.progressList || []).length" type="info">暂无进度更新</el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="帮扶材料" name="material">
          <el-table :data="detail.materialList || []" border size="small">
            <el-table-column prop="materialName" label="材料名称" />
            <el-table-column prop="materialType" label="类型" width="130" />
            <el-table-column label="文件哈希" min-width="200">
              <template #default="scope"><span class="hash-text">{{ scope.row.fileHash }}</span></template>
            </el-table-column>
            <el-table-column label="是否上链" width="100">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.onChain ? 'success' : 'info'">{{ scope.row.onChain ? '已上链' : '未上链' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="txId" label="交易 ID" min-width="160" />
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button v-if="scope.row.fileId" type="text" size="small" @click="download(scope.row.fileId)">下载原件</el-button>
                <el-button type="text" size="small" @click="verifyMaterial(scope.row)">哈希验真</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="链上存证" name="evidence">
          <evidence-table :records="detail.evidenceList || []" @verify="verifyEvidence" />
        </el-tab-pane>

        <el-tab-pane label="审核记录" name="audit">
          <el-timeline>
            <el-timeline-item v-for="item in detail.auditList || []" :key="item.auditId"
                              :timestamp="formatTime(item.auditTime)" placement="top"
                              :type="item.result === 'PASS' ? 'success' : (item.result === 'REJECT' || item.result === 'OFFLINE' ? 'danger' : 'primary')">
              <div>{{ item.auditType }} - {{ item.result || '提交' }}</div>
              <div>{{ item.opinion }}</div>
            </el-timeline-item>
            <el-timeline-item v-if="!(detail.auditList || []).length" type="info">暂无审核记录</el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="项目评价" name="review">
          <el-form v-if="canReview" :inline="true">
            <el-form-item label="评分">
              <el-rate v-model="reviewForm.rating" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="reviewForm.content" placeholder="写下您的评价与反馈" style="width: 320px" />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="reviewForm.anonymous" :true-label="1" :false-label="0">匿名评价</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitReview">提交评价</el-button>
            </el-form-item>
          </el-form>
          <el-divider />
          <div v-for="item in detail.reviewList || []" :key="item.reviewId" class="review-item">
            <el-rate :model-value="item.rating" disabled />
            <span class="review-user">{{ item.reviewer }}</span>
            <span class="review-time">{{ formatTime(item.createTime) }}</span>
            <div>{{ item.content }}</div>
            <div v-if="item.replyContent" class="review-reply">社团回复：{{ item.replyContent }}</div>
          </div>
          <el-empty v-if="!(detail.reviewList || []).length" description="暂无评价" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog title="创建模拟捐赠订单" v-model="donateVisible" width="460px">
      <el-form :model="donateForm" label-width="100px">
        <el-form-item label="捐赠金额">
          <el-input-number v-model="donateForm.amount" :min="1" :precision="2" style="width: 200px" /> 元
        </el-form-item>
        <el-form-item label="匿名捐赠">
          <el-switch v-model="donateForm.anonymous" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="留言">
          <el-input v-model="donateForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="donateVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitDonate">创建订单</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import EvidenceTable from '@/components/EvidenceTable.vue'
import {
  getProject, toggleFavorite, shareProject, reviewProject
} from '@/api/project'
import { createOrder, requestKey } from '@/api/finance'
import { downloadFile } from '@/api/file'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'ProjectDetail',
  components: { EvidenceTable },
  data() {
    return {
      loading: false,
      detail: {},
      activeTab: 'intro',
      donateVisible: false,
      submitting: false,
      donateForm: { projectId: null, amount: 100, requestKey: '', anonymous: 0, remark: '', payChannel: 'CAMPUS_CARD' },
      reviewForm: { projectId: null, rating: 5, content: '', anonymous: 0 }
    }
  },
  computed: {
    role() {
      return this.$store.getters.role
    },
    statusType() {
      const map = { ONLINE: 'success', PENDING: 'warning', REJECTED: 'danger', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[this.detail.status] || 'info'
    },
    canReview() {
      return this.role === 'STUDENT'
    },
    fundCards() {
      const flow = this.detail.fundFlow || {}
      return [
        { label: '已筹金额', value: flow.currentAmount || this.detail.currentAmount },
        { label: '已提取金额', value: flow.withdrawTotal },
        { label: '已使用金额', value: flow.usageTotal },
        { label: '捐赠人数', value: this.detail.donorCount }
      ]
    }
  },
  async created() {
    await this.load()
  },
  methods: {
    formatAmount,
    formatTime,
    async load() {
      this.loading = true
      try {
        const id = this.$route.params.id
        const res = await getProject(id)
        this.detail = res.data || {}
        this.donateForm.projectId = id
        this.reviewForm.projectId = id
      } finally {
        this.loading = false
      }
    },
    async favorite() {
      const res = await toggleFavorite(this.detail.projectId)
      this.detail.favorited = res.data
      this.$message.success(res.message || '操作成功')
    },
    async share() {
      await shareProject(this.detail.projectId, 'LINK')
      const link = window.location.origin + '/#/donor/project/' + this.detail.projectId
      this.$alert(link, '项目分享链接', { confirmButtonText: '确定' })
    },
    async openDonate() {
      await this.$store.dispatch('user/fetchProfile')
      if (this.$store.getters.userInfo.authStatus !== 2) {
        this.$message.warning('请先在校园身份认证页面完成认证')
        return this.$router.push('/donor/identity')
      }
      this.donateForm.requestKey=requestKey()
      this.donateVisible = true
    },
    async submitDonate() {
      if (this.submitting) return
      this.submitting = true
      try {
      await createOrder({ ...this.donateForm, anonymous: Boolean(this.donateForm.anonymous) })
      this.donateVisible = false
      this.$message.success('订单已创建，请在我的模拟订单中完成模拟支付')
      await this.$router.push('/donor/orders')
      } finally { this.submitting=false }
    },
    async submitReview() {
      await reviewProject(this.reviewForm)
      this.$message.success('评价提交成功')
      await this.load()
    },
    download(fileId) {
      downloadFile(fileId)
    },
    verifyMaterial(row) {
      this.$router.push({ path: '/trace/verify', query: { bizType: 'MATERIAL', bizId: row.materialId, hash: row.fileHash } })
    },
    verifyEvidence(row) {
      this.$router.push({ path: '/trace/verify', query: { bizType: row.bizType, bizId: row.bizId, hash: row.dataHash } })
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

<style scoped>
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-title {
  font-size: 20px;
  font-weight: bold;
  margin-right: 10px;
}
.review-item {
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
}
.review-user {
  margin: 0 10px;
  color: #409eff;
}
.review-time {
  color: #909399;
  font-size: 12px;
}
.review-reply {
  margin-top: 6px;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
}
</style>
