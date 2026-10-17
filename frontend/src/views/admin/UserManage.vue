<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>用户与权限管理</span>
        <el-button type="primary" style="float: right" @click="createVisible = true">新增管理/监管账号</el-button>
      </div></template>
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="账号 / 姓名 / 学号" style="width: 200px" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" clearable placeholder="全部" style="width: 150px">
            <el-option label="普通学生" value="STUDENT" />
            <el-option label="公益社团" value="CLUB" />
            <el-option label="平台管理员" value="ADMIN" />
            <el-option label="监管审计" value="AUDITOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option label="正常" :value="1" />
            <el-option label="冻结" :value="0" />
            <el-option label="待审核" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>

      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="userId" label="ID" width="70" />
        <el-table-column prop="account" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="roleLabel" label="角色" width="110" />
        <el-table-column prop="identityNoMasked" label="学号/编号(脱敏)" width="140" />
        <el-table-column prop="college" label="学院/单位" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认证" width="100">
          <template #default="scope">
            <el-tag size="small" type="info">{{ authLabel(scope.row.authStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="160" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status !== 0" type="text" size="small" style="color:#f56c6c"
                       @click="changeStatus(scope.row, 0)">冻结</el-button>
            <el-button v-else type="text" size="small" @click="changeStatus(scope.row, 1)">解冻</el-button>
            <el-dropdown @command="role => changeRole(scope.row, role)">
              <el-button type="text" size="small">权限分配<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item command="STUDENT">设为普通学生</el-dropdown-item>
                <el-dropdown-item command="CLUB">设为公益社团</el-dropdown-item>
                <el-dropdown-item command="ADMIN">设为平台管理员</el-dropdown-item>
                <el-dropdown-item command="AUDITOR">设为监管审计</el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
            <el-button type="text" size="small" @click="resetPwd(scope.row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="query.current" :page-size="query.size" @current-change="load" />
      </div>
    </el-card>

    <el-dialog title="新增管理员 / 监管审计账号" v-model="createVisible" width="520px">
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="账号"><el-input v-model="createForm.account" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="createForm.password" placeholder="默认 123456" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="createForm.realName" /></el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="createForm.role">
            <el-radio label="ADMIN">平台管理员</el-radio>
            <el-radio label="AUDITOR">监管审计</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="工号"><el-input v-model="createForm.identityNo" /></el-form-item>
        <el-form-item label="所属单位"><el-input v-model="createForm.college" /></el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createUser">创建</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listUsers, updateUserStatus, updateUserRole, resetUserPassword, createSystemUser } from '@/api/admin'

export default {
  name: 'UserManage',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      query: { current: 1, size: 10, keyword: '', role: '', status: null },
      createVisible: false,
      createForm: { account: '', password: '123456', realName: '', role: 'ADMIN', identityNo: '', college: '' }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listUsers(this.query)
        this.list = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    statusType(status) {
      const map = { 1: 'success', 0: 'danger', 2: 'warning' }
      return map[status] || 'info'
    },
    statusLabel(status) {
      const map = { 1: '正常', 0: '已冻结', 2: '待审核' }
      return map[status] || status
    },
    authLabel(status) {
      const map = { 0: '未认证', 1: '待审核', 2: '已认证', 3: '已驳回' }
      return map[status] || '未认证'
    },
    async changeStatus(row, status) {
      await updateUserStatus(row.userId, status, status === 0 ? '账号存在异常操作，已冻结' : '账号已恢复')
      this.$message.success('账号状态已更新')
      await this.load()
    },
    async changeRole(row, role) {
      await updateUserRole(row.userId, role)
      this.$message.success('角色权限已更新')
      await this.load()
    },
    async resetPwd(row) {
      await resetUserPassword(row.userId)
      this.$message.success('密码已重置为 123456')
    },
    async createUser() {
      await createSystemUser(this.createForm)
      this.$message.success('账号创建成功')
      this.createVisible = false
      await this.load()
    }
  }
}
</script>
