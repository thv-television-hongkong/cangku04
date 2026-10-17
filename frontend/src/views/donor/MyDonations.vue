<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div class="workspace-heading"><div>我的捐赠记录（含存证凭证与项目反馈）<p>仅新版财务确认后的有效捐赠支持全额测试退款。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option label="历史支付记录" value="PAID" />
            <el-option label="财务已确认" value="CONFIRMED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="donationNo" label="捐赠单号" width="170" />
        <el-table-column prop="projectTitle" label="捐赠项目" min-width="180" />
        <el-table-column label="金额" width="110">
          <template #default="scope"><span class="fund-amount">￥{{ formatAmount(scope.row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="捐赠时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.donateTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="存证凭证（交易ID / 区块高度）" min-width="230">
          <template #default="scope">
            <div class="chain-tag">{{ evidenceLabel(scope.row.evidenceStatus) }}</div>
            <div v-if="scope.row.txId" class="chain-tag">{{ scope.row.txId }}</div><div>区块高度：{{ scope.row.blockHeight ?? '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="项目反馈" min-width="200">
          <template #default="scope">
            <div>{{ scope.row.projectFeedback || '暂无进度反馈' }}</div>
            <div class="legal-tip">{{ formatTime(scope.row.projectProgressTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.refundEligible" type="primary" link @click="refund(scope.row)">申请退款</el-button>
            <el-tag v-if="scope.row.refundStatus === 'REFUNDED'" type="info">已退款</el-tag>
            <el-tag v-if="scope.row.refundStatus === 'PROCESSING'" type="warning">退款处理中</el-tag>
            <p v-if="!scope.row.refundEligible" class="legal-tip">{{ scope.row.refundUnavailableReason }}</p><el-button v-if="scope.row.refundStatus==='PROCESSING' || scope.row.refundStatus==='REFUNDED'" link type="primary" @click="$router.push('/donor/refunds')">查看退款进度</el-button><el-button type="text" size="small" @click="showReceipt(scope.row)">存证凭证</el-button>
            <el-button v-if="scope.row.cancellable" type="text" size="small" style="color: #f56c6c"
                       @click="openCancel(scope.row)">取消捐赠</el-button>
            <el-button type="text" size="small" @click="$router.push('/donor/project/' + scope.row.projectId)">项目详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无捐赠记录" /></template>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog title="申请全额测试退款" v-model="refundVisible" width="min(600px,94vw)" :close-on-click-modal="!refundBusy" :close-on-press-escape="!refundBusy" :show-close="!refundBusy">
      <el-alert title="仅内部测试，不发生真实资金退回。学校审核通过后还需登记完成凭证。" type="warning" :closable="false" style="margin-bottom:18px" />
      <el-descriptions border :column="1"><el-descriptions-item label="公益项目">{{ refundRow.projectTitle }}</el-descriptions-item><el-descriptions-item label="原捐赠单号">{{ refundRow.donationNo }}</el-descriptions-item><el-descriptions-item label="原捐赠金额">￥{{ formatAmount(refundRow.amount) }}</el-descriptions-item><el-descriptions-item label="全额退款金额">￥{{ formatAmount(refundRow.amount) }}</el-descriptions-item></el-descriptions>
      <el-form label-position="top" style="margin-top:18px"><el-form-item label="退款原因（必填）"><el-input v-model="refundForm.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit :disabled="refundBusy" placeholder="请说明申请全额测试退款的原因" /></el-form-item></el-form>
      <template #footer><el-button :disabled="refundBusy" @click="refundVisible=false">取消</el-button><el-button type="primary" :loading="refundBusy" @click="submitRefund">提交退款申请</el-button></template>
    </el-dialog>
    <el-dialog title="存证凭证" v-model="receiptVisible" width="560px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="存证状态">{{ evidenceLabel(current.evidenceStatus) }}</el-descriptions-item><el-descriptions-item label="存证业务">捐赠记录（DONATION）</el-descriptions-item>
        <el-descriptions-item label="存证数据哈希"><span class="hash-text">{{ current.dataHash }}</span></el-descriptions-item>
        <el-descriptions-item label="交易 ID"><span class="chain-tag">{{ current.txId }}</span></el-descriptions-item>
        <el-descriptions-item label="区块高度">{{ current.blockHeight }}</el-descriptions-item>
        <el-descriptions-item label="存证来源">{{ current.chainSource }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><div>
        <el-button @click="receiptVisible = false">关闭</el-button>
        <el-button type="primary" @click="verify">前往哈希验真</el-button>
      </div></template>
    </el-dialog>

    <el-dialog title="取消捐赠（限定时间内可撤回）" v-model="cancelVisible" width="460px">
      <el-form :model="cancelForm" label-width="90px">
        <el-form-item label="捐赠单号">{{ cancelForm.donationNo }}</el-form-item>
        <el-form-item label="取消原因">
          <el-input v-model="cancelForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon
                  title="此入口仅兼容历史未确认记录；新版有效捐赠需申请退款。历史记录保留，取消事件进入存证队列。" />
      </el-form>
      <template #footer><div>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" @click="submitCancel">确认撤回</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { myDonations, cancelDonation } from '@/api/donation'
import refreshWorkspace from '@/utils/refreshWorkspace'
import { applyRefund, requestKey, evidenceLabel } from '@/api/finance'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'MyDonations',
  mixins: [refreshWorkspace],
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, status: '' },
      receiptVisible: false,
      refundVisible:false, refundBusy:false, refundRow:{}, refundForm:{reason:'', requestKey:''},
      cancelVisible: false,
      current: {},
      cancelForm: { donationId: null, donationNo: '', reason: '' }
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatAmount,
    formatTime,
    evidenceLabel,
    refund(row) {
      if(!row.refundEligible)return this.$message.warning(row.refundUnavailableReason)
      this.refundRow=row;this.refundForm={donationId:row.donationId,reason:'',requestKey:requestKey()};this.refundVisible=true
    },
    async submitRefund() {
      if(this.refundBusy)return
      if(!this.refundForm.reason.trim())return this.$message.warning('请填写退款原因')
      this.refundBusy=true
      try{await applyRefund(this.refundForm);this.$message.success('退款申请已保存，等待学校审核；可在退款申请记录查看进度');this.refundVisible=false;await this.load()}finally{this.refundBusy=false}
    },
    async load() {
      this.loading = true
      try {
        const res = await myDonations(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    statusType(status) {
      const map = { PAID: 'warning', CONFIRMED: 'success', CANCELLED: 'info' }
      return map[status] || 'info'
    },
    showReceipt(row) {
      this.current = row
      this.receiptVisible = true
    },
    verify() {
      this.$router.push({ path: '/trace/verify', query: { bizType: 'DONATION', bizId: this.current.donationId, hash: this.current.dataHash } })
    },
    openCancel(row) {
      this.cancelForm = { donationId: row.donationId, donationNo: row.donationNo, reason: '' }
      this.cancelVisible = true
    },
    async submitCancel() {
      await cancelDonation({ donationId: this.cancelForm.donationId, reason: this.cancelForm.reason })
      this.$message.success('历史未确认记录已取消，撤回事件已进入存证队列')
      this.cancelVisible = false
      await this.load()
    }
  }
}
</script>
