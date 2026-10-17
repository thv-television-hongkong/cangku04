<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <i class="el-icon-s-promotion" />
        <span>校园公益众筹存证</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#12343b"
        text-color="#c7d8d6"
        active-text-color="#65d7c5"
        router
        unique-opened
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <el-sub-menu v-if="route.children && visibleChildren(route).length > 1"
                      :key="route.path" :index="route.path">
            <template #title>
              <i :class="route.meta && route.meta.icon" />
              <span>{{ route.meta && route.meta.title }}</span>
            </template>
            <el-menu-item v-for="child in visibleChildren(route)" :key="child.path" :index="fullPath(route, child)">
              <i :class="child.meta && child.meta.icon" />
              <span>{{ child.meta && child.meta.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item
            v-for="child in (visibleChildren(route).length <= 1 ? visibleChildren(route) : [])"
            :key="child.path"
            :index="fullPath(route, child)"
          >
            <i :class="child.meta && child.meta.icon" />
            <template #title><span>{{ route.meta && route.meta.title }} / {{ child.meta && child.meta.title }}</span></template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div class="navbar-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="navbar-right">
          <el-tag size="small" effect="plain" :type="chainTagType">{{ chainLabel }}</el-tag>
          <el-badge :value="unread" :hidden="unread === 0" class="notice-badge">
            <el-button type="text" icon="el-icon-bell" @click="$router.push('/notice')">通知</el-button>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <i class="el-icon-user-solid" />
              {{ userInfo.realName || userInfo.account }}
              <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
              <i class="el-icon-arrow-down" />
            </span>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="profile">个人信息</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="legal-tip"
          title="仅校内原型演示 · 支付和退款均为模拟，不发生实际扣款；财务确认后计入筹款。原件存服务器，存证仅提交摘要。"
        />
        <router-view />
      </el-main>
    </el-container>

    <el-dialog title="修改密码" v-model="passwordVisible" width="420px">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" show-password />
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPassword">确定</el-button>
      </div></template>
    </el-dialog>
  </el-container>
</template>

<script>
import { asyncRoutes } from '@/router'
import { getUnreadCount, changePassword } from '@/api/auth'
import settings from '@/settings'

export default {
  name: 'Layout',
  data() {
    return {
      unread: 0,
      chainSource: 'MOCK',
      passwordVisible: false,
      passwordForm: { oldPassword: '', newPassword: '', confirmPassword: '' }
    }
  },
  computed: {
    userInfo() {
      return this.$store.getters.userInfo
    },
    role() {
      return this.$store.getters.role
    },
    roleLabel() {
      return (settings.roles || {})[this.role] || this.role
    },
    menuRoutes() {
      return asyncRoutes.filter(route => {
        if (route.meta && route.meta.roles && route.meta.roles.length > 0) {
          return route.meta.roles.indexOf(this.role) >= 0
        }
        return true
      })
    },
    activeMenu() {
      const meta = this.$route.meta || {}
      return meta.activeMenu || this.$route.path
    },
    pageTitle() {
      return (this.$route.meta && this.$route.meta.title) || '校园公益众筹存证溯源系统'
    },
    chainLabel() {
      return this.chainSource === 'FABRIC' ? 'Fabric 联盟链已连接' : 'MOCK · 本地模拟存证'
    },
    chainTagType() {
      return this.chainSource === 'FABRIC' ? 'success' : 'warning'
    }
  },
  created() {
    this.loadUnread()
    this.$bus.$on('refresh-unread', this.loadUnread)
  },
  beforeUnmount() {
    this.$bus.$off('refresh-unread', this.loadUnread)
  },
  methods: {
    visibleChildren(route) {
      if (!route.children) return []
      return route.children.filter(child => {
        if (child.meta && child.meta.hidden) return false
        if (child.meta && child.meta.roles && child.meta.roles.length > 0) {
          return child.meta.roles.indexOf(this.role) >= 0
        }
        return true
      })
    },
    fullPath(route, child) {
      if (route.path === '/') return '/' + child.path
      return route.path + '/' + child.path
    },
    async loadUnread() {
      try {
        const res = await getUnreadCount()
        this.unread = res.data || 0
      } catch (e) {
        this.unread = 0
      }
    },
    handleCommand(command) {
      if (command === 'profile') {
        this.$router.push('/profile')
      } else if (command === 'password') {
        this.passwordVisible = true
      } else if (command === 'logout') {
        this.$store.dispatch('user/logout').then(() => {
          this.$router.push('/login')
        })
      }
    },
    async submitPassword() {
      await changePassword(this.passwordForm)
      this.$message.success('密码修改成功，请重新登录')
      this.passwordVisible = false
      await this.$store.dispatch('user/logout')
      this.$router.push('/login')
    }
  }
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
  background: var(--campus-bg);
}
.sidebar {
  background-color: var(--campus-ink);
  overflow-y: auto;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}
.logo {
  height: 60px;
  line-height: 60px;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  text-align: center;
  background-color: var(--campus-ink-deep);
  letter-spacing: 0.2px;
}
.logo i {
  margin-right: 6px;
  color: #65d7c5;
}
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--campus-line);
  box-shadow: 0 2px 10px rgba(18, 52, 59, 0.04);
  padding: 0 24px;
}
.page-title {
  font-size: 16px;
  font-weight: bold;
  color: var(--campus-ink);
}
.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.notice-badge {
  margin-right: 10px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  cursor: pointer;
  color: var(--campus-text);
}
.main-content {
  background-color: var(--campus-bg);
  padding: 20px;
  overflow-y: auto;
}

@media (max-width: 760px) {
  .sidebar {
    width: 68px !important;
  }

  .logo {
    font-size: 0;
  }

  .logo i {
    margin-right: 0;
    font-size: 22px;
  }

  .sidebar ::v-deep .el-menu-item span,
  .sidebar ::v-deep .el-sub-menu__title span {
    display: none;
  }

  .sidebar ::v-deep .el-menu-item,
  .sidebar ::v-deep .el-sub-menu__title {
    padding-left: 24px !important;
  }

  .navbar {
    padding: 0 12px;
  }

  .page-title {
    max-width: 150px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .navbar-right {
    gap: 6px;
  }
  .navbar-right > .el-tag, .user-info .el-tag { display: none; }

  .main-content {
    padding: 12px;
  }
}
</style>
