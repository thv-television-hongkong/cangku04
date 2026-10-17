// 返回到页面/浏览器标签时立即同步；可见且未打开操作弹窗时刷新待处理状态。
export default {
  mounted() {
    this.workspaceRefresh = () => {
      if (!document.hidden && !this.loading && !this.busy && !this.visible && !this.refundVisible && !this.cancelVisible) this.load().catch(() => {})
    }
    window.addEventListener('focus', this.workspaceRefresh)
    document.addEventListener('visibilitychange', this.workspaceRefresh)
    this.workspaceTimer = window.setInterval(this.workspaceRefresh, 15000)
  },
  beforeUnmount() {
    window.clearInterval(this.workspaceTimer)
    window.removeEventListener('focus', this.workspaceRefresh)
    document.removeEventListener('visibilitychange', this.workspaceRefresh)
  }
}
