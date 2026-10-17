<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><div>个人信息</div></template>
          <el-form :model="form" label-width="110px">
            <el-form-item label="账号">
              <el-input v-model="form.account" disabled />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag>{{ form.roleLabel }}</el-tag>
            </el-form-item>
            <el-form-item label="认证状态">
              <el-tag :type="authType">{{ authLabel }}</el-tag>
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="form.realName" />
            </el-form-item>
            <el-form-item label="学号 / 编号">
              <el-input v-model="form.identityNo" disabled :placeholder="form.identityNoMasked" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="展示时自动脱敏" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
            <el-form-item label="学院 / 单位">
              <el-input v-model="form.college" />
            </el-form-item>
            <el-form-item label="专业">
              <el-input v-model="form.major" />
            </el-form-item>
            <el-form-item v-if="$store.getters.role === 'STUDENT'" label="校园身份"><el-button link type="primary" @click="$router.push('/donor/identity')">提交校园身份证明，查看审核结果</el-button></el-form-item>
            <el-form-item>
              <el-button type="primary" @click="save">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header><div>账号安全</div></template>
          <el-form :model="passwordForm" label-width="110px" style="max-width: 460px">
            <el-form-item label="原密码">
              <el-input v-model="passwordForm.oldPassword" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
          <el-divider />
          <div class="section-title">我的投诉与反馈</div>
          <el-button size="small" type="primary" @click="complaintVisible = true">提交投诉 / 建议</el-button>
          <el-table :data="complaints" size="small" border style="margin-top: 12px">
            <el-table-column prop="complaintNo" label="编号" width="150" />
            <el-table-column prop="title" label="标题" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="complaintType(scope.row.status)">
                  {{ complaintLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="handleResult" label="处理结果" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog title="提交投诉 / 建议" v-model="complaintVisible" width="520px">
      <el-form :model="complaintForm" label-width="100px">
        <el-form-item label="投诉类型">
          <el-select v-model="complaintForm.complaintType" style="width: 100%">
            <el-option label="资金使用问题" value="FUND_USE" />
            <el-option label="项目真实性质疑" value="FAKE_PROJECT" />
            <el-option label="隐私泄露" value="PRIVACY" />
            <el-option label="进度滞后" value="DELAY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目ID">
          <el-input v-model="complaintForm.projectId" placeholder="选填" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="complaintForm.title" />
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input v-model="complaintForm.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="complaintVisible = false">取消</el-button>
        <el-button type="primary" @click="submitComplaintForm">提交</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getProfile, updateProfile, changePassword } from '@/api/auth'
import { submitComplaint, myComplaints } from '@/api/admin'
import { formatTime } from '@/utils/format'
import settings from '@/settings'

export default {
  name: 'Profile',
  data() {
    return {
      form: {},
      idCardNo: '',
      passwordForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
      complaintVisible: false,
      complaints: [],
      complaintForm: { complaintType: 'OTHER', projectId: null, title: '', content: '' }
    }
  },
  computed: {
    authLabel() {
      if (this.form.identityTestOnly) return this.form.authStatus === 2 ? '内部测试认证已完成' : '测试认证已停用，需学校审核'
      const map = { 0: '未认证', 1: '待审核', 2: '已认证', 3: '认证驳回' }
      return map[this.form.authStatus] || '未认证'
    },
    authType() {
      const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
      return map[this.form.authStatus] || 'info'
    }
  },
  async created() {
    await this.load()
    await this.loadComplaints()
  },
  methods: {
    formatTime,
    async load() {
      const res = await getProfile()
      this.form = res.data || {}
    },
    async save() {
      const payload = Object.assign({}, this.form, { idCardNo: this.idCardNo || undefined })
      await updateProfile(payload)
      this.$message.success('个人信息已更新')
      await this.$store.dispatch('user/fetchProfile')
      this.idCardNo = ''
      await this.load()
    },
    async changePwd() {
      await changePassword(this.passwordForm)
      this.$message.success('密码修改成功，请重新登录')
      await this.$store.dispatch('user/logout')
      this.$router.push('/login')
    },
    async loadComplaints() {
      const res = await myComplaints({ current: 1, size: 10 })
      this.complaints = (res.data && res.data.records) || []
    },
    async submitComplaintForm() {
      await submitComplaint(this.complaintForm)
      this.$message.success('投诉已提交，管理员将尽快处理')
      this.complaintVisible = false
      await this.loadComplaints()
    },
    complaintLabel(status) {
      const map = { 0: '待处理', 1: '处理中', 2: '已解决', 3: '已驳回' }
      return map[status] || status
    },
    complaintType(status) {
      const map = { 0: 'warning', 1: 'warning', 2: 'success', 3: 'info' }
      return map[status] || 'info'
    },
    roleLabel(role) {
      return (settings.roles || {})[role] || role
    }
  }
}
</script>
