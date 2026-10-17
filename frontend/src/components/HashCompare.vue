<template>
  <div class="hash-compare">
    <el-form :model="form" label-width="110px">
      <el-form-item label="业务类型">
        <el-select v-model="form.bizType" placeholder="请选择存证业务类型" style="width: 100%">
          <el-option v-for="item in bizTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务ID">
        <el-input v-model="form.bizId" placeholder="例如：捐赠ID / 资金凭证ID / 材料ID" />
      </el-form-item>
      <el-form-item label="待校验哈希">
        <el-input v-model="form.hash" placeholder="请输入 64 位 SHA-256 哈希，或上传文件自动计算" />
      </el-form-item>
      <el-form-item label="上传文件核验">
        <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange" action="#">
          <el-button size="small" icon="el-icon-upload2">选择文件自动计算 SHA-256</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="doVerify">校验哈希</el-button>
        <el-button @click="doVerifyByChain">仅依据链上数据校验</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="result"
      :type="result.outcome === 'MATCH' ? 'success' : result.outcome === 'MISMATCH' ? 'error' : 'warning'"
      :title="({ MATCH: '摘要一致', MISMATCH: '摘要不一致', NOT_FOUND: '未找到存证', UNAVAILABLE: '暂时无法核验' })[result.outcome] || '核验结果'"
      :description="result.message"
      show-icon
      :closable="false"
    />

    <el-descriptions v-if="result" :column="1" border size="small" style="margin-top: 12px">
      <el-descriptions-item label="业务类型">{{ result.bizType }}</el-descriptions-item>
      <el-descriptions-item label="业务ID">{{ result.bizId }}</el-descriptions-item>
      <el-descriptions-item label="链上存证哈希">
        <span class="hash-text">{{ result.storedHash }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="提交的哈希">
        <span class="hash-text">{{ result.providedHash }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="存证交易ID">{{ result.txId }}</el-descriptions-item>
      <el-descriptions-item label="区块高度">{{ result.blockHeight }}</el-descriptions-item>
      <el-descriptions-item label="上链时间">{{ formatTime(result.chainTime) }}</el-descriptions-item>
      <el-descriptions-item label="存证来源">{{ result.chainSource }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script>
import { verifyHash, verifyByChain } from '@/api/trace'
import { sha256OfFile } from '@/utils/hash'
import { formatTime } from '@/utils/format'
import settings from '@/settings'

export default {
  name: 'HashCompare',
  props: {
    defaultBizType: { type: String, default: '' },
    defaultBizId: { type: [String, Number], default: '' }
  },
  data() {
    return {
      bizTypes: settings.bizTypes,
      loading: false,
      form: {
        bizType: this.defaultBizType || 'DONATION',
        bizId: String(this.defaultBizId || ''),
        hash: ''
      },
      result: null
    }
  },
  methods: {
    formatTime,
    async onFileChange(file) {
      this.form.hash = await sha256OfFile(file.raw)
      this.$message.success('文件 SHA-256 已计算完成')
    },
    async doVerify() {
      if (!this.form.bizType || !this.form.bizId || !this.form.hash) {
        this.$message.warning('请填写完整的校验信息')
        return
      }
      this.loading = true
      try {
        const res = await verifyHash(this.form)
        this.result = res.data
        this.$emit('verified', this.result)
      } finally {
        this.loading = false
      }
    },
    async doVerifyByChain() {
      if (!this.form.bizType || !this.form.bizId || !this.form.hash) {
        this.$message.warning('请填写完整的校验信息')
        return
      }
      this.loading = true
      try {
        const res = await verifyByChain(this.form)
        this.result = res.data
        this.$emit('verified', this.result)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
