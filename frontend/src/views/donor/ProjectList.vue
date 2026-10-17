<template>
  <div class="app-container">
    <el-card shadow="never" class="filter-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="项目名称 / 编号" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="帮扶类别">
          <el-select v-model="query.category" clearable placeholder="全部" style="width: 150px">
            <el-option label="教育帮扶" value="EDUCATION" />
            <el-option label="医疗救助" value="MEDICAL" />
            <el-option label="困难帮扶" value="POVERTY" />
            <el-option label="突发救助" value="DISASTER" />
            <el-option label="其他公益" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="query.sort" style="width: 140px">
            <el-option label="最新发布" value="latest" />
            <el-option label="热度最高" value="hot" />
            <el-option label="筹款最多" value="progress" />
            <el-option label="即将截止" value="deadline" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="project in list" :key="project.projectId">
        <project-card
          :project="project"
          :can-donate="role === 'STUDENT'"
          @detail="goDetail"
          @favorite="favorite(project)"
          @share="share(project)"
          @donate="openDonate(project)"
        />
      </el-col>
    </el-row>

    <div class="table-pagination">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :page-sizes="[9, 18, 36]"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <!-- 捐款 / 捐赠（extends 取消捐赠） -->
    <el-dialog title="创建模拟捐赠订单" v-model="donateVisible" width="480px">
      <el-form :model="donateForm" label-width="100px">
        <el-form-item label="捐赠项目">
          <span>{{ currentProject.title }}</span>
        </el-form-item>
        <el-form-item label="捐赠金额">
          <el-input-number v-model="donateForm.amount" :min="1" :precision="2" :step="50" style="width: 200px" />
          <span style="margin-left: 8px">元</span>
        </el-form-item>
        <el-form-item label="快速金额">
          <el-button v-for="amount in [50, 100, 200, 500]" :key="amount" size="small"
                     @click="donateForm.amount = amount">{{ amount }}</el-button>
        </el-form-item>
        <el-form-item label="支付方式"><el-tag type="warning">模拟支付 · 不会扣款</el-tag></el-form-item>
        <el-form-item label="匿名捐赠">
          <el-switch v-model="donateForm.anonymous" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="捐赠留言">
          <el-input v-model="donateForm.remark" type="textarea" :rows="2" maxlength="100" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="创建订单后直接模拟支付，无需上传凭证，学校财务确认后计入筹款并提交摘要存证。已确认捐赠需通过退款申请处理。"
        />
      </el-form>
      <template #footer><div>
        <el-button @click="donateVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitDonate">创建订单</el-button>
      </div></template>
    </el-dialog>

    <!-- 捐赠成功凭证 -->
    <el-dialog title="捐赠成功，存证凭证已生成" v-model="receiptVisible" width="560px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="捐赠单号">{{ receipt.donationNo }}</el-descriptions-item>
        <el-descriptions-item label="捐赠金额">￥{{ formatAmount(receipt.amount) }}</el-descriptions-item>
        <el-descriptions-item label="交易 ID（链上凭证）">
          <span class="chain-tag">{{ receipt.txId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="区块高度">{{ receipt.blockHeight }}</el-descriptions-item>
        <el-descriptions-item label="存证哈希">
          <span class="hash-text">{{ receipt.dataHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="可取消截止时间">
          {{ formatTime(receipt.cancelDeadline) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer><div>
        <el-button @click="receiptVisible = false">关闭</el-button>
        <el-button type="primary" @click="$router.push('/donor/donations')">查看我的捐赠记录</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import ProjectCard from '@/components/ProjectCard.vue'
import { listProjects, toggleFavorite, shareProject } from '@/api/project'
import { createOrder, requestKey } from '@/api/finance'
import { formatAmount, formatTime } from '@/utils/format'

export default {
  name: 'ProjectList',
  components: { ProjectCard },
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { keyword: '', category: '', sort: 'latest', current: 1, size: 9 },
      donateVisible: false,
      receiptVisible: false,
      submitting: false,
      currentProject: {},
      donateForm: { projectId: null, amount: 100, requestKey: '', payChannel: 'CAMPUS_CARD', anonymous: 0, remark: '' },
      receipt: {}
    }
  },
  computed: {
    role() {
      return this.$store.getters.role
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
        const res = await listProjects(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    search() {
      this.query.current = 1
      this.load()
    },
    reset() {
      this.query = { keyword: '', category: '', sort: 'latest', current: 1, size: 9 }
      this.load()
    },
    goDetail(projectId) {
      this.$router.push('/donor/project/' + projectId)
    },
    async favorite(project) {
      const res = await toggleFavorite(project.projectId)
      this.$message.success(res.message || '操作成功')
      this.load()
    },
    async share(project) {
      await shareProject(project.projectId, 'LINK')
      const link = window.location.origin + '/#/donor/project/' + project.projectId
      this.$alert(link, '项目分享链接', { confirmButtonText: '复制链接' }).then(() => {
        const input = document.createElement('input')
        input.value = link
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        document.body.removeChild(input)
      })
    },
    openDonate(project) {
      this.currentProject = project
      this.donateForm.projectId = project.projectId
      this.donateForm.amount = 100
      this.donateForm.requestKey=requestKey()
      this.donateVisible = true
    },
    async submitDonate() {
      this.submitting = true
      try {
        const res = await createOrder({ ...this.donateForm, anonymous: Boolean(this.donateForm.anonymous) })
        this.donateVisible = false
        this.$message.success('订单已创建，请在我的模拟订单中完成模拟支付')
        await this.$router.push('/donor/orders')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>
