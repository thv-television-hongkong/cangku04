<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>我发起的众筹项目</span>
        <el-button type="primary" style="float: right" icon="el-icon-plus" @click="$router.push('/club/project/form')">
          发起众筹项目
        </el-button>
      </div></template>

      <el-alert
        v-if="qualification && qualification.status !== 2"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="您的社团资质尚未通过审核，暂时无法发起众筹项目，请先完成资质认证。"
      />

      <el-form :inline="true" class="filter-bar">
        <el-form-item label="项目状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 160px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="审核驳回" value="REJECTED" />
            <el-option label="众筹中" value="ONLINE" />
            <el-option label="已下架" value="OFFLINE" />
            <el-option label="已完成" value="FINISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="projectNo" label="项目编号" width="180" />
        <el-table-column prop="title" label="项目标题" min-width="180" />
        <el-table-column label="目标金额" width="120">
          <template #default="scope">￥{{ formatAmount(scope.row.targetAmount) }}</template>
        </el-table-column>
        <el-table-column label="已筹/进度" width="160">
          <template #default="scope">
            <div>￥{{ formatAmount(scope.row.currentAmount) }}</div>
            <el-progress :percentage="scope.row.fundingPercent || 0" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.status)">{{ scope.row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="180">
          <template #default="scope">{{ scope.row.auditOpinion || '-' }}</template>
        </el-table-column>
        <el-table-column label="链上存证" min-width="180">
          <template #default="scope">
            <div v-if="scope.row.txId" class="chain-tag">{{ scope.row.txId }}</div>
            <div v-if="scope.row.blockHeight">区块高度：{{ scope.row.blockHeight }}</div>
            <span v-if="!scope.row.txId" class="legal-tip">尚未上链存证</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="scope">
            <el-button type="text" size="small" @click="$router.push('/club/project/workspace/' + scope.row.projectId)">执行管理</el-button>
            <el-button v-if="editable(scope.row.status)" type="text" size="small"
                       @click="$router.push('/club/project/edit/' + scope.row.projectId)">编辑</el-button>
            <el-button v-if="scope.row.status === 'DRAFT' || scope.row.status === 'REJECTED'"
                       type="text" size="small" @click="submit(scope.row)">提交审核</el-button>
            <el-button type="text" size="small" @click="viewDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无项目，点击右上角发起众筹项目" /></template>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>
  </div>
</template>

<script>
import { myProjects, submitProject } from '@/api/project'
import { getQualification } from '@/api/auth'
import { formatAmount } from '@/utils/format'

export default {
  name: 'MyProjects',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, status: '' },
      qualification: null
    }
  },
  async created() {
    await this.load()
    await this.loadQualification()
  },
  methods: {
    formatAmount,
    async load() {
      this.loading = true
      try {
        const res = await myProjects(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async loadQualification() {
      try {
        const res = await getQualification()
        this.qualification = res.data
      } catch (e) {
        this.qualification = null
      }
    },
    editable(status) {
      return status === 'DRAFT' || status === 'REJECTED'
    },
    statusType(status) {
      const map = { DRAFT: 'info', PENDING: 'warning', REJECTED: 'danger', ONLINE: 'success', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[status] || 'info'
    },
    async submit(row) {
      await submitProject(row.projectId)
      this.$message.success('已提交管理员审核')
      await this.load()
    },
    viewDetail(row) {
      this.$router.push('/donor/project/' + row.projectId)
    }
  }
}
</script>
