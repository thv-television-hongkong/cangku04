<template>
  <el-card class="project-card" shadow="hover">
    <div class="project-title" @click="goDetail">{{ project.title }}</div>
    <div class="project-meta">
      <el-tag size="small" type="info">{{ project.categoryLabel }}</el-tag>
      <el-tag size="small" :type="statusType">{{ project.statusLabel }}</el-tag>
      <el-tag v-if="project.beneficiaryMasked" size="small" type="warning">
        帮扶对象：{{ project.beneficiaryMasked }}
      </el-tag>
    </div>
    <div class="project-desc">{{ (project.description || '').slice(0, 60) }}...</div>

    <el-progress :percentage="project.fundingPercent || 0" :stroke-width="12" />
    <div class="project-amount">
      <span class="fund-amount">￥{{ formatAmount(project.currentAmount) }}</span>
      <span class="target">目标 ￥{{ formatAmount(project.targetAmount) }}</span>
    </div>

    <div class="project-stats">
      <span><i class="el-icon-user" /> {{ project.donorCount || 0 }} 人捐赠</span>
      <span><i class="el-icon-view" /> {{ project.viewCount || 0 }}</span>
      <span><i class="el-icon-time" /> 剩余 {{ project.remainingDays || 0 }} 天</span>
    </div>

    <!-- 浏览项目列表即展示区块链存证信息（用例：include 查看区块链存证） -->
    <div v-if="project.txId" class="chain-info">
      <i class="el-icon-link" />
      <span class="chain-tag">存证交易：{{ shortHash(project.txId, 20) }}</span>
      <span class="chain-tag">区块高度：{{ project.blockHeight || '-' }}</span>
    </div>
    <div v-else class="chain-info pending">
      <i class="el-icon-warning-outline" /> 项目尚未完成链上存证
    </div>

    <div class="project-actions">
      <el-button type="primary" size="small" @click="goDetail">查看详情</el-button>
      <el-button size="small" @click="$emit('favorite')">
        <i :class="project.favorited ? 'el-icon-star-on' : 'el-icon-star-off'" /> 收藏
      </el-button>
      <el-button size="small" @click="$emit('share')">分享</el-button>
      <el-button v-if="canDonate && project.status === 'ONLINE'" type="danger" size="small" @click="$emit('donate')">我要捐赠</el-button>
    </div>
  </el-card>
</template>

<script>
import { formatAmount, shortHash } from '@/utils/format'

export default {
  name: 'ProjectCard',
  props: {
    project: { type: Object, required: true },
    canDonate: { type: Boolean, default: true }
  },
  computed: {
    statusType() {
      const map = { ONLINE: 'success', PENDING: 'warning', REJECTED: 'danger', OFFLINE: 'danger', FINISHED: 'primary' }
      return map[this.project.status] || 'info'
    }
  },
  methods: {
    formatAmount,
    shortHash,
    goDetail() {
      this.$emit('detail', this.project.projectId)
    }
  }
}
</script>

<style scoped>
.project-card {
  margin-bottom: 16px;
  height: 100%;
}
.project-title {
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  color: #303133;
  margin-bottom: 8px;
}
.project-title:hover {
  color: #409eff;
}
.project-meta {
  margin-bottom: 8px;
}
.project-meta .el-tag {
  margin-right: 6px;
}
.project-desc {
  color: #909399;
  font-size: 13px;
  height: 38px;
  overflow: hidden;
  margin-bottom: 10px;
}
.project-amount {
  display: flex;
  justify-content: space-between;
  margin: 8px 0;
}
.project-amount .target {
  color: #909399;
  font-size: 12px;
}
.project-stats {
  display: flex;
  justify-content: space-between;
  color: #909399;
  font-size: 12px;
  margin: 10px 0;
}
.chain-info {
  font-size: 12px;
  color: #67c23a;
  background: #f0f9eb;
  border-radius: 4px;
  padding: 6px 8px;
  margin-bottom: 10px;
}
.chain-info.pending {
  color: #e6a23c;
  background: #fdf6ec;
}
.project-actions {
  text-align: right;
}
</style>
