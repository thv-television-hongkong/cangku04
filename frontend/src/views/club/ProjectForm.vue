<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>{{ isEdit ? '编辑众筹项目' : '发起众筹项目' }}</div></template>
      <el-form ref="form" :model="form" :rules="rules" label-width="140px" style="max-width: 900px">
        <el-form-item label="项目标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：山区儿童冬季助学计划" />
        </el-form-item>
        <el-form-item label="帮扶类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="教育帮扶" value="EDUCATION" />
            <el-option label="医疗救助" value="MEDICAL" />
            <el-option label="困难帮扶" value="POVERTY" />
            <el-option label="突发救助" value="DISASTER" />
            <el-option label="其他公益" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="帮扶对象" prop="beneficiary">
          <el-input v-model="form.beneficiary" placeholder="帮扶对象姓名 / 机构名称（对外展示时系统自动脱敏）" />
          <div class="legal-tip">帮扶对象个人信息将脱敏展示（如“张**”），链上不存储任何个人信息明文。</div>
        </el-form-item>
        <el-form-item label="帮扶对象联系方式">
          <el-input v-model="form.beneficiaryContact" placeholder="选填，展示时会自动脱敏" />
        </el-form-item>
        <el-form-item label="帮扶对象情况">
          <el-input v-model="form.beneficiaryDesc" type="textarea" :rows="3"
                    placeholder="请描述帮扶对象情况（注意不要填写身份证号、银行卡号等敏感信息）" />
        </el-form-item>
        <el-form-item label="筹款目标金额" prop="targetAmount">
          <el-input-number v-model="form.targetAmount" :min="1" :precision="2" :step="1000" style="width: 220px" /> 元
        </el-form-item>
        <el-form-item label="筹款起止时间" prop="startTime">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始时间" end-placeholder="截止时间"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="4"
                    placeholder="项目背景、帮扶意义、执行安排等" />
        </el-form-item>
        <el-form-item label="资金使用计划">
          <el-input v-model="form.fundsPlan" type="textarea" :rows="4"
                    placeholder="请分项说明资金用途与预算，用于管理员审核与后续公示" />
        </el-form-item>
        <el-form-item label="项目封面">
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="onCoverChange" action="#">
            <el-button size="small" icon="el-icon-upload2">上传封面图片</el-button>
          </el-upload>
          <span v-if="form.coverImage" style="margin-left: 10px">已上传</span>
        </el-form-item>
        <el-form-item label="提交方式">
          <el-radio-group v-model="form.submit">
            <el-radio :label="true">直接提交管理员审核</el-radio>
            <el-radio :label="false">保存为草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ isEdit ? '保存修改' : '提交项目申请' }}
          </el-button>
          <el-button @click="$router.push('/club/projects')">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { createProject, updateProject, getProject } from '@/api/project'
import { uploadFile } from '@/api/file'

export default {
  name: 'ProjectForm',
  data() {
    return {
      submitting: false,
      timeRange: [],
      form: {
        projectId: null,
        title: '',
        category: 'EDUCATION',
        beneficiary: '',
        beneficiaryContact: '',
        beneficiaryDesc: '',
        targetAmount: 10000,
        startTime: '',
        endTime: '',
        description: '',
        fundsPlan: '',
        coverImage: '',
        submit: true
      },
      rules: {
        title: [{ required: true, message: '请输入项目标题', trigger: 'blur' }],
        beneficiary: [{ required: true, message: '请填写帮扶对象', trigger: 'blur' }],
        targetAmount: [{ required: true, message: '请设置筹款目标金额', trigger: 'blur' }],
        startTime: [{ required: true, message: '请选择筹款起止时间', trigger: 'change' }]
      }
    }
  },
  computed: {
    isEdit() {
      return !!this.$route.params.id
    }
  },
  async created() {
    if (this.isEdit) {
      const res = await getProject(this.$route.params.id)
      const data = res.data || {}
      this.form = {
        projectId: data.projectId,
        title: data.title,
        category: data.category,
        beneficiary: data.beneficiaryMasked || '',
        beneficiaryContact: '',
        beneficiaryDesc: data.beneficiaryDesc,
        targetAmount: Number(data.targetAmount),
        startTime: data.startTime,
        endTime: data.endTime,
        description: data.description,
        fundsPlan: data.fundsPlan,
        coverImage: data.coverImage,
        submit: true
      }
      this.timeRange = [data.startTime, data.endTime]
    }
  },
  methods: {
    async onCoverChange(file) {
      const res = await uploadFile(file.raw, 'PROJECT_COVER')
      this.form.coverImage = (res.data && res.data.fileUrl) || ''
      this.$message.success('封面已上传（仅哈希上链存证，原图存业务服务器）')
    },
    async submit() {
      if (this.timeRange && this.timeRange.length === 2) {
        this.form.startTime = this.timeRange[0]
        this.form.endTime = this.timeRange[1]
      }
      this.$refs.form.validate(async valid => {
        if (!valid) return
        if (this.timeRange && this.timeRange.length === 2) {
          this.form.startTime = this.timeRange[0]
          this.form.endTime = this.timeRange[1]
        }
        if (!this.form.startTime || !this.form.endTime) {
          this.$message.warning('请选择筹款起止时间')
          return
        }
        this.submitting = true
        try {
          if (this.isEdit) {
            await updateProject(this.form)
            this.$message.success('项目已更新')
          } else {
            await createProject(this.form)
            this.$message.success('项目已提交，等待管理员审核')
          }
          this.$router.push('/club/projects')
        } finally {
          this.submitting = false
        }
      })
    }
  }
}
</script>
