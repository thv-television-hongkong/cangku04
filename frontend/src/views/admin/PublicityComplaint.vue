<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>公益信息公示与投诉纠纷处理</div></template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="公益信息公示" name="publicity">
          <el-button type="primary" size="small" @click="publicityVisible = true">发布校内公示</el-button>
          <el-table :data="publicityList" border stripe size="small" style="margin-top: 12px" v-loading="loading">
            <el-table-column prop="title" label="公示标题" min-width="220" />
            <el-table-column prop="publicityType" label="类型" width="120" />
            <el-table-column prop="projectId" label="项目ID" width="90" />
            <el-table-column prop="content" label="公示内容" min-width="280" />
            <el-table-column prop="publishUserName" label="发布人" width="120" />
            <el-table-column label="发布时间" width="160">
              <template #default="scope">{{ formatTime(scope.row.publishTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.status === 1 ? 'success' : 'info'">
                  {{ scope.row.status === 1 ? '公示中' : '已撤回' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="scope">
                <el-button v-if="scope.row.status === 1" type="text" size="small"
                           @click="revoke(scope.row)">撤回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="投诉与纠纷" name="complaint">
          <el-table :data="complaints" border stripe size="small" v-loading="loading">
            <el-table-column prop="complaintNo" label="投诉单号" width="170" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="complaintType" label="类型" width="130" />
            <el-table-column prop="projectId" label="项目ID" width="90" />
            <el-table-column prop="content" label="投诉内容" min-width="220" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="handleResult" label="处理结果" min-width="200" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="scope">
                <el-button v-if="scope.row.status === 0 || scope.row.status === 1" type="text" size="small"
                           @click="openHandle(scope.row)">处理</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无投诉" /></template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog title="发布校内公益公示" v-model="publicityVisible" width="620px">
      <el-form :model="publicityForm" label-width="100px">
        <el-form-item label="公示标题"><el-input v-model="publicityForm.title" /></el-form-item>
        <el-form-item label="公示类型">
          <el-select v-model="publicityForm.publicityType" style="width: 100%">
            <el-option label="项目公示" value="PROJECT" />
            <el-option label="资金公示" value="FUND" />
            <el-option label="通知公告" value="ANNOUNCEMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目ID"><el-input v-model="publicityForm.projectId" placeholder="选填" /></el-form-item>
        <el-form-item label="公示内容">
          <el-input v-model="publicityForm.content" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="publicityVisible = false">取消</el-button>
        <el-button type="primary" @click="publish">发布公示</el-button>
      </div></template>
    </el-dialog>

    <el-dialog title="处理投诉与纠纷（extends 通知相关方）" v-model="handleVisible" width="560px">
      <el-form :model="handleForm" label-width="100px">
        <el-form-item label="投诉标题">{{ handleForm.title }}</el-form-item>
        <el-form-item label="处理状态">
          <el-radio-group v-model="handleForm.status">
            <el-radio :label="2">已解决</el-radio>
            <el-radio :label="3">驳回投诉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="handleForm.result" type="textarea" :rows="3"
                    placeholder="处理结果将同时通知投诉人与被投诉项目所属社团" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">提交并通知相关方</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listPublicity, publishPublicity, revokePublicity, listComplaints, handleComplaint } from '@/api/admin'
import { formatTime } from '@/utils/format'

export default {
  name: 'PublicityComplaint',
  data() {
    return {
      activeTab: 'publicity',
      loading: false,
      publicityList: [],
      complaints: [],
      publicityVisible: false,
      handleVisible: false,
      publicityForm: { title: '', publicityType: 'PROJECT', projectId: null, content: '' },
      handleForm: { complaintId: null, title: '', status: 2, result: '' }
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatTime,
    async load() {
      this.loading = true
      try {
        const [pub, comp] = await Promise.all([
          listPublicity({ current: 1, size: 50 }),
          listComplaints({ current: 1, size: 50 })
        ])
        this.publicityList = (pub.data && pub.data.records) || []
        this.complaints = (comp.data && comp.data.records) || []
      } finally {
        this.loading = false
      }
    },
    async publish() {
      await publishPublicity(this.publicityForm)
      this.$message.success('公示已发布')
      this.publicityVisible = false
      await this.load()
    },
    async revoke(row) {
      await revokePublicity(row.publicityId)
      this.$message.success('公示已撤回')
      await this.load()
    },
    openHandle(row) {
      this.handleForm = { complaintId: row.complaintId, title: row.title, status: 2, result: '' }
      this.handleVisible = true
    },
    async submitHandle() {
      await handleComplaint(this.handleForm.complaintId, this.handleForm.status, this.handleForm.result)
      this.$message.success('处理完成，已通知投诉人与相关社团')
      this.handleVisible = false
      await this.load()
    },
    statusType(status) {
      const map = { 0: 'warning', 1: 'warning', 2: 'success', 3: 'info' }
      return map[status] || 'info'
    },
    statusLabel(status) {
      const map = { 0: '待处理', 1: '处理中', 2: '已解决', 3: '已驳回' }
      return map[status] || status
    }
  }
}
</script>
