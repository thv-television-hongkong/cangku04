<template>
  <el-timeline>
    <el-timeline-item
      v-for="(node, index) in nodes"
      :key="index"
      :timestamp="formatTime(node.chainTime || node.timestamp || node.createTime)"
      :type="nodeType(node)"
      placement="top"
    >
      <el-card shadow="never">
        <div class="node-title">
          <el-tag size="small" :type="nodeType(node)">
            {{ node.bizTypeLabel || node.bizType || '链下业务' }}
          </el-tag>
          <span class="node-id">业务ID：{{ node.bizId || node.projectId || '-' }}</span>
          <el-tag v-if="node.nodeType === 'OFF_CHAIN'" size="small" type="info">链下记录</el-tag>
        </div>
        <div v-if="node.amount" class="node-amount">金额：￥{{ formatAmount(node.amount) }}</div>
        <div v-if="node.status" class="node-status">状态：{{ node.status }}</div>
        <div v-if="node.dataHash" class="node-hash">
          <span class="detail-label">存证哈希</span>
          <span class="hash-text">{{ node.dataHash }}</span>
        </div>
        <div v-if="node.txId" class="node-hash">
          <span class="detail-label">交易 ID</span>
          <span class="chain-tag">{{ node.txId }}</span>
        </div>
        <div v-if="node.blockHeight">
          <span class="detail-label">区块高度</span>
          <span>{{ node.blockHeight }}</span>
        </div>
        <div v-if="node.chainHash">
          <span class="detail-label">链式哈希</span>
          <span class="hash-text">{{ node.chainHash }}</span>
        </div>
      </el-card>
    </el-timeline-item>
    <el-timeline-item v-if="!nodes || nodes.length === 0" type="info">
      暂无溯源节点
    </el-timeline-item>
  </el-timeline>
</template>

<script>
import { formatTime, formatAmount } from '@/utils/format'

export default {
  name: 'TraceTimeline',
  props: {
    nodes: { type: Array, default: () => [] }
  },
  methods: {
    formatTime,
    formatAmount,
    nodeType(node) {
      if (node.nodeType === 'OFF_CHAIN') return 'info'
      const map = {
        PROJECT: 'primary',
        DONATION: 'success',
        FUND_USAGE: 'warning',
        MATERIAL: 'info',
        WITHDRAWAL: 'danger',
        PROJECT_PROGRESS: 'primary',
        PROJECT_STATUS: 'danger',
        AUDIT_REPORT: 'info'
      }
      return map[node.bizType] || 'primary'
    }
  }
}
</script>

<style scoped>
.node-title {
  margin-bottom: 6px;
}
.node-title span.node-id {
  margin: 0 10px;
  color: #606266;
  font-size: 13px;
}
.node-amount {
  color: #f56c6c;
  font-weight: bold;
}
.node-status {
  color: #909399;
  font-size: 13px;
}
.node-hash {
  margin-top: 4px;
}
</style>
