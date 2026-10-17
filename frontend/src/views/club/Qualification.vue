<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><div>社团资质认证（社团账号需资质审核后方可发起项目）</div></template>
          <el-alert
            v-if="qualification"
            :type="alertType"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
            :title="'当前资质状态：' + statusLabel"
            :description="qualification.auditOpinion || ''"
          />
          <el-form :model="form" label-width="140px">
            <el-form-item label="社团名称">
              <el-input v-model="form.clubName" />
            </el-form-item>
            <el-form-item label="社团编号">
              <el-input v-model="form.clubCode" />
            </el-form-item>
            <el-form-item label="挂靠学院 / 单位">
              <el-input v-model="form.college" />
            </el-form-item>
            <el-form-item label="指导老师">
              <el-input v-model="form.advisorName" />
            </el-form-item>
            <el-form-item label="指导老师电话">
              <el-input v-model="form.advisorPhone" />
            </el-form-item>
            <el-form-item label="资质材料">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange" action="#">
                <el-button size="small" icon="el-icon-upload2">上传社团注册证明 / 指导单位意见</el-button>
              </el-upload>
              <div v-if="form.licenseHash" class="hash-text">材料哈希：{{ form.licenseHash }}</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submit">提交资质审核</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><div>说明</div></template>
          <ul class="guide">
            <li>社团账号注册后需提交资质材料，由平台管理员审核；</li>
            <li>审核通过后账号才可发起众筹项目、上传资金凭证；</li>
            <li>资质材料原件保存在学校业务服务器，仅文件哈希上链存证；</li>
            <li>资质被驳回时可修改后重新提交。</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getQualification, submitQualification } from '@/api/auth'
import { uploadFile } from '@/api/file'

export default {
  name: 'ClubQualification',
  data() {
    return {
      qualification: null,
      form: {
        clubName: '', clubCode: '', college: '', advisorName: '', advisorPhone: '',
        fileId: null, licenseHash: ''
      }
    }
  },
  computed: {
    statusLabel() {
      const map = { 1: '待审核', 2: '已通过', 3: '已驳回' }
      return map[(this.qualification || {}).status] || '未提交'
    },
    alertType() {
      const map = { 1: 'warning', 2: 'success', 3: 'danger' }
      return map[(this.qualification || {}).status] || 'info'
    }
  },
  async created() {
    await this.load()
  },
  methods: {
    async load() {
      try {
        const res = await getQualification()
        this.qualification = res.data
        if (res.data) {
          this.form = Object.assign({}, this.form, {
            clubName: res.data.clubName,
            clubCode: res.data.clubCode,
            college: res.data.college,
            advisorName: res.data.advisorName,
            advisorPhone: res.data.advisorPhone,
            fileId: res.data.fileId,
            licenseHash: res.data.licenseHash
          })
        }
      } catch (e) {
        this.qualification = null
      }
    },
    async onFileChange(file) {
      const res = await uploadFile(file.raw, 'QUALIFICATION')
      this.form.fileId = res.data.fileId
      this.form.licenseHash = res.data.fileHash
      this.$message.success('资质材料已上传')
    },
    async submit() {
      await submitQualification(this.form)
      this.$message.success('资质材料已提交，等待管理员审核')
      await this.load()
    }
  }
}
</script>

<style scoped>
.guide li {
  margin-bottom: 10px;
  color: #606266;
  line-height: 22px;
}
</style>
