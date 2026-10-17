<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><div>哈希校验工具（校验文件是否被篡改）</div></template>
          <hash-compare :default-biz-type="defaultBizType" :default-biz-id="defaultBizId" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><div>使用说明</div></template>
          <ol class="guide">
            <li>选择存证业务类型（捐赠 / 资金凭证 / 帮扶材料 / 项目等）；</li>
            <li>填写对应业务 ID（如捐赠ID、资金凭证ID、材料ID）；</li>
            <li>上传原始文件，页面本地计算 SHA-256 摘要；</li>
            <li>点击“校验哈希”，系统将本地摘要与链上存证摘要比对；</li>
            <li>也可点击“仅依据链上数据校验”，不依赖业务数据库直接核验链上凭证。</li>
          </ol>
          <el-divider />
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="法律约束：系统仅把文件哈希摘要写入联盟链，原始文件保存在学校业务服务器，链上不存储任何原始敏感数据。"
          />
          <el-divider />
          <div class="section-title">文件哈希与存证哈希的对应关系</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="项目存证">SHA256(PROJECT|项目ID|标题|目标金额|脱敏帮扶对象|起止时间)</el-descriptions-item>
            <el-descriptions-item label="捐赠存证">SHA256(DONATION|捐赠ID|项目ID|捐赠人ID|金额|捐赠时间)</el-descriptions-item>
            <el-descriptions-item label="资金凭证">SHA256(FUND_USAGE|凭证ID|项目ID|金额|使用说明|文件哈希)</el-descriptions-item>
            <el-descriptions-item label="帮扶材料">SHA256(MATERIAL|材料ID|项目ID|材料类型|文件哈希)</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import HashCompare from '@/components/HashCompare.vue'

export default {
  name: 'HashVerify',
  components: { HashCompare },
  data() {
    return {
      defaultBizType: this.$route.query.bizType || 'DONATION',
      defaultBizId: this.$route.query.bizId || ''
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
