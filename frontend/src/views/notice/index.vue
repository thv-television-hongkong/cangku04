<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>
        <span>我的通知</span>
        <el-button type="text" style="float: right" :disabled="unreadIds.length === 0" @click="markAllRead">
          全部标记已读
        </el-button>
      </div></template>
      <el-table :data="list" border stripe size="small">
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.readStatus === 0 ? 'danger' : 'info'">
              {{ scope.row.readStatus === 0 ? '未读' : '已读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="noticeType" label="类型" width="140" />
        <el-table-column prop="title" label="标题" width="220" />
        <el-table-column prop="content" label="内容" />
        <el-table-column label="时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button v-if="scope.row.readStatus === 0" type="text" size="small"
                       @click="markRead(scope.row.noticeId)">标记已读</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无通知" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getNotifications, markNotificationsRead } from '@/api/auth'
import { formatTime } from '@/utils/format'

export default {
  name: 'Notice',
  data() {
    return { list: [] }
  },
  computed: {
    unreadIds() {
      return this.list.filter(i => i.readStatus === 0).map(i => i.noticeId)
    }
  },
  async created() {
    await this.load()
  },
  methods: {
    formatTime,
    async load() {
      const res = await getNotifications()
      this.list = res.data || []
    },
    async markRead(id) {
      await markNotificationsRead([id])
      await this.load()
      this.$bus.$emit('refresh-unread')
    },
    async markAllRead() {
      await markNotificationsRead(this.unreadIds)
      await this.load()
      this.$bus.$emit('refresh-unread')
    }
  }
}
</script>
