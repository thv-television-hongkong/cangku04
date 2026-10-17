<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>社团资质审核</div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="审核状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option label="待审核" :value="1" />
            <el-option label="已通过" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="qualificationId" label="ID" width="70" />
        <el-table-column prop="clubName" label="社团名称" width="180" />
        <el-table-column prop="clubCode" label="社团编号" width="130" />
        <el-table-column prop="college" label="挂靠单位" width="150" />
        <el-table-column prop="advisorName" label="指导老师" width="120" />
        <el-table-column label="资质材料哈希" min-width="200">
          <template #default="scope"><span class="hash-text">{{ scope.row.licenseHash }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditOpinion" label="审核意见" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 1" type="text" size="small" @click="openAudit(scope.row, 2)">通过</el-button>
            <el-button v-if="scope.row.status === 1" type="text" size="small" style="color:#f56c6c"
                       @click="openAudit(scope.row, 3)">驳回</el-button>
            <el-button v-if="scope.row.fileId" type="text" size="small" @click="download(scope.row.fileId)">下载材料</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无资质记录" /></template>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog :title="auditForm.status === 2 ? '通过社团资质' : '驳回社团资质'" v-model="auditVisible" width="520px">
      <el-form :model="auditForm" label-width="100px">
        <el-form-item label="社团名称">{{ auditForm.clubName }}</el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3"
                    :placeholder="auditForm.status === 2 ? '资质材料齐全，通过审核' : '请说明驳回原因'" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit">提交</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listQualifications, auditQualification } from '@/api/admin'
import { downloadFile } from '@/api/file'

export default {
  name: 'QualificationAudit',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, status: 1 },
      auditVisible: false,
      auditForm: { qualificationId: null, clubName: '', status: 2, opinion: '' }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listQualifications(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    statusType(status) {
      const map = { 1: 'warning', 2: 'success', 3: 'danger' }
      return map[status] || 'info'
    },
    statusLabel(status) {
      const map = { 1: '待审核', 2: '已通过', 3: '已驳回' }
      return map[status] || status
    },
    openAudit(row, status) {
      this.auditForm = { qualificationId: row.qualificationId, clubName: row.clubName, status, opinion: '' }
      this.auditVisible = true
    },
    async submitAudit() {
      await auditQualification(this.auditForm)
      this.$message.success('资质审核完成，已通知社团')
      this.auditVisible = false
      await this.load()
    },
    download(fileId) {
      downloadFile(fileId)
    }
  }
}
</script>
