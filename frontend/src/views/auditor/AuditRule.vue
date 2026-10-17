<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>审核与预警规则配置（extends 设置预警规则）</span>
        <el-button type="primary" style="float: right" @click="openForm()">新增规则</el-button>
      </div></template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="规则用于自动检测异常交易（单笔金额 / 单日累计 / 交易频次 / 项目目标 / 项目周期 / 资金使用进度），命中后生成预警并通知监管与管理员。"
      />
      <el-table :data="list" border stripe size="small" v-loading="loading">
        <el-table-column prop="ruleId" label="ID" width="70" />
        <el-table-column prop="ruleName" label="规则名称" width="220" />
        <el-table-column prop="ruleType" label="规则类型" width="150" />
        <el-table-column prop="bizType" label="适用业务" width="130" />
        <el-table-column label="阈值" width="150">
          <template #default="scope">{{ scope.row.comparison }} {{ scope.row.thresholdValue }} {{ unit(scope.row.thresholdUnit) }}</template>
        </el-table-column>
        <el-table-column label="预警级别" width="110">
          <template #default="scope">
            <el-tag size="small" :type="levelType(scope.row.warningLevel)">{{ levelLabel(scope.row.warningLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="100" />
        <el-table-column prop="description" label="规则说明" min-width="240" />
        <el-table-column label="启用状态" width="110">
          <template #default="scope">
            <el-switch :value="scope.row.enabled === 1" @change="val => toggle(scope.row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button type="text" size="small" @click="openForm(scope.row)">编辑</el-button>
            <el-button type="text" size="small" style="color:#f56c6c" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无预警规则" /></template>
      </el-table>
    </el-card>

    <el-dialog :title="form.ruleId ? '编辑预警规则' : '新增预警规则'" v-model="formVisible" width="560px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="规则名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="form.ruleType" style="width: 100%">
            <el-option label="单笔金额" value="SINGLE_AMOUNT" />
            <el-option label="单日累计" value="DAILY_TOTAL" />
            <el-option label="交易频次" value="FREQUENCY" />
            <el-option label="项目目标金额" value="PROJECT_TARGET" />
            <el-option label="项目周期" value="DURATION" />
            <el-option label="资金使用进度" value="PROJECT_PROGRESS" />
            <el-option label="其他规则" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用业务">
          <el-select v-model="form.bizType" style="width: 100%">
            <el-option label="捐赠业务" value="DONATION" />
            <el-option label="众筹项目" value="PROJECT" />
            <el-option label="资金凭证" value="FUND_USAGE" />
            <el-option label="资金提取" value="WITHDRAWAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="比较符">
          <el-select v-model="form.comparison" style="width: 100%">
            <el-option label="大于 >" value=">" />
            <el-option label="大于等于 >=" value=">=" />
            <el-option label="小于 <" value="<" />
            <el-option label="小于等于 <=" value="<=" />
            <el-option label="等于 =" value="=" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="form.thresholdValue" :min="0" :precision="2" style="width: 200px" /></el-form-item>
        <el-form-item label="单位">
          <el-select v-model="form.thresholdUnit" style="width: 100%">
            <el-option label="元" value="YUAN" />
            <el-option label="次" value="COUNT" />
            <el-option label="天" value="DAY" />
            <el-option label="%" value="PERCENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警级别">
          <el-radio-group v-model="form.warningLevel">
            <el-radio label="LOW">低</el-radio>
            <el-radio label="MID">中</el-radio>
            <el-radio label="HIGH">高</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规则说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { listRules, saveRule, updateRule, toggleRule, deleteRule } from '@/api/audit'

export default {
  name: 'AuditRule',
  data() {
    return {
      loading: false,
      list: [],
      formVisible: false,
      form: {
        ruleId: null, ruleName: '', ruleType: 'SINGLE_AMOUNT', bizType: 'DONATION',
        comparison: '>', thresholdValue: 5000, thresholdUnit: 'YUAN', warningLevel: 'MID',
        enabled: 1, description: ''
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await listRules({})
        this.list = res.data || []
      } finally {
        this.loading = false
      }
    },
    openForm(row) {
      if (row) {
        this.form = Object.assign({}, row)
      } else {
        this.form = {
          ruleId: null, ruleName: '', ruleType: 'SINGLE_AMOUNT', bizType: 'DONATION',
          comparison: '>', thresholdValue: 5000, thresholdUnit: 'YUAN', warningLevel: 'MID',
          enabled: 1, description: ''
        }
      }
      this.formVisible = true
    },
    async save() {
      if (this.form.ruleId) {
        await updateRule(this.form)
      } else {
        await saveRule(this.form)
      }
      this.$message.success('规则已保存')
      this.formVisible = false
      await this.load()
    },
    async toggle(row, value) {
      await toggleRule(row.ruleId, value)
      this.$message.success(value ? '规则已启用' : '规则已停用')
      await this.load()
    },
    async remove(row) {
      await this.$confirm('确认删除该预警规则？', '提示', { type: 'warning' })
      await deleteRule(row.ruleId)
      this.$message.success('规则已删除')
      await this.load()
    },
    unit(unit) {
      const map = { YUAN: '元', COUNT: '次', DAY: '天', PERCENT: '%' }
      return map[unit] || unit
    },
    levelType(level) {
      const map = { LOW: 'info', MID: 'warning', HIGH: 'danger' }
      return map[level] || 'info'
    },
    levelLabel(level) {
      const map = { LOW: '低风险', MID: '中风险', HIGH: '高风险' }
      return map[level] || level
    }
  }
}
</script>
