<template>
  <el-table :data="records" border stripe size="small" style="width: 100%">
    <el-table-column prop="evidenceNo" label="存证编号" width="130" />
    <el-table-column label="业务类型" width="120">
      <template #default="scope">
        <el-tag size="small">{{ scope.row.bizTypeLabel || scope.row.bizType }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="bizId" label="关联业务ID" width="90" />
    <el-table-column label="存证数据哈希（SHA-256）" min-width="220">
      <template #default="scope">
        <span class="hash-text">{{ scope.row.dataHash }}</span>
      </template>
    </el-table-column>
    <el-table-column label="交易 ID" min-width="200">
      <template #default="scope">
        <span class="chain-tag">{{ scope.row.txId }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="blockHeight" label="区块高度" width="100" />
    <el-table-column label="上链时间" width="160">
      <template #default="scope">{{ formatTime(scope.row.chainTime || scope.row.timestamp) }}</template>
    </el-table-column>
    <el-table-column prop="operatorName" label="操作人" width="110" />
    <el-table-column label="操作" width="160" fixed="right">
      <template #default="scope">
        <el-button type="text" size="small" @click="copyHash(scope.row.dataHash)">复制哈希</el-button>
        <el-button type="text" size="small" @click="$emit('verify', scope.row)">验真</el-button>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="暂无链上存证记录" />
    </template>
  </el-table>
</template>

<script>
import { formatTime } from '@/utils/format'

export default {
  name: 'EvidenceTable',
  props: {
    records: { type: Array, default: () => [] }
  },
  methods: {
    formatTime,
    copyHash(hash) {
      if (!hash) return
      const input = document.createElement('input')
      input.value = hash
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      this.$message.success('哈希已复制到剪贴板')
    }
  }
}
</script>
