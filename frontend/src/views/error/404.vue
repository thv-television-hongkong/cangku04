<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在或您没有访问权限</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script>
export default {
  name: 'NotFound'
}
</script>

<style scoped>
.not-found {
  text-align: center;
  padding-top: 120px;
}
.not-found h1 {
  font-size: 72px;
  color: #409eff;
  margin-bottom: 8px;
}
</style>
