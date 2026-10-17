<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><div>我的收藏项目</div></template>
      <el-row :gutter="16" v-loading="loading">
        <el-col :span="8" v-for="project in list" :key="project.projectId">
          <project-card :project="project" :can-donate="true"
                        @detail="id => $router.push('/donor/project/' + id)"
                        @favorite="unfavorite(project)"
                        @share="share(project)"
                        @donate="id => $router.push('/donor/project/' + id)" />
        </el-col>
      </el-row>
      <el-empty v-if="!loading && list.length === 0" description="暂无收藏项目" />
    </el-card>
  </div>
</template>

<script>
import ProjectCard from '@/components/ProjectCard.vue'
import { myFavorites, toggleFavorite, shareProject } from '@/api/project'

export default {
  name: 'MyFavorites',
  components: { ProjectCard },
  data() {
    return { loading: false, list: [] }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await myFavorites({ current: 1, size: 20 })
        this.list = (res.data && res.data.records) || []
      } finally {
        this.loading = false
      }
    },
    async unfavorite(project) {
      await toggleFavorite(project.projectId)
      this.$message.success('已取消收藏')
      await this.load()
    },
    async share(project) {
      await shareProject(project.projectId, 'LINK')
      this.$message.success('分享链接已生成并记录')
    }
  }
}
</script>
