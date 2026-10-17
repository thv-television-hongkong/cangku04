import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layout/index.vue'


/**
 * 路由表。
 * meta.roles 为空表示已登录即可访问；否则只允许指定角色访问（与后端 @PreAuthorize 双重控制）。
 */
export const constantRoutes = [
  // The root entry is intentionally identity-neutral. The guard sends authenticated users to roleHome().
  { path: '/', redirect: '/login', hidden: true },
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    hidden: true
  },
  { path: '/404', component: () => import('@/views/error/404.vue'), hidden: true }
]

export const asyncRoutes = [
  {
    path: '/',
    name: 'AppShell',
    meta: { title: '个人中心', icon: 'el-icon-s-home' },
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页总览', icon: 'el-icon-s-home' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人信息', icon: 'el-icon-user' }
      },
      {
        path: 'notice',
        name: 'Notice',
        component: () => import('@/views/notice/index.vue'),
        meta: { title: '我的通知', icon: 'el-icon-bell' }
      }
    ]
  },

  // ------------------------------ 捐赠者（普通学生） ------------------------------
  {
    path: '/donor',
    component: Layout,
    meta: { title: '公益众筹', icon: 'el-icon-present' },
    children: [
      { path: 'orders', name: 'MyOrders', component: () => import('@/views/finance/OrderWorkspace.vue'), meta: { title: '我的模拟订单', icon: 'el-icon-tickets', roles: ['STUDENT'] } },
      { path: 'identity', name: 'CampusIdentity', component: () => import('@/views/finance/CampusIdentity.vue'), meta: { title: '校园身份认证', icon: 'el-icon-postcard', roles: ['STUDENT'] } },
      { path: 'refunds', name: 'MyRefunds', component: () => import('@/views/finance/RefundWorkspace.vue'), meta: { title: '退款申请记录', icon: 'el-icon-refresh-left', roles: ['STUDENT'] } },
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/donor/ProjectList.vue'),
        meta: { title: '浏览公益项目', icon: 'el-icon-search' }
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/donor/ProjectDetail.vue'),
        meta: { title: '项目详情', hidden: true, activeMenu: '/donor/projects' }
      },
      {
        path: 'donations',
        name: 'MyDonations',
        component: () => import('@/views/donor/MyDonations.vue'),
        meta: { title: '我的捐赠记录', icon: 'el-icon-tickets', roles: ['STUDENT', 'ADMIN', 'AUDITOR'] }
      },
      {
        path: 'favorites',
        name: 'MyFavorites',
        component: () => import('@/views/donor/MyFavorites.vue'),
        meta: { title: '我的收藏', icon: 'el-icon-star-off', roles: ['STUDENT'] }
      },
      {
        path: 'fund-flow/:id',
        name: 'FundFlow',
        component: () => import('@/views/donor/FundFlow.vue'),
        meta: { title: '资金流向', hidden: true, activeMenu: '/donor/projects' }
      }
    ]
  },

  // ------------------------------ 溯源查询 ------------------------------
  {
    path: '/trace',
    component: Layout,
    meta: { title: '存证溯源', icon: 'el-icon-link' },
    children: [
      {
        path: 'query',
        name: 'TraceQuery',
        component: () => import('@/views/trace/TraceQuery.vue'),
        meta: { title: '多维度溯源查询', icon: 'el-icon-search' }
      },
      {
        path: 'verify',
        name: 'HashVerify',
        component: () => import('@/views/trace/HashVerify.vue'),
        meta: { title: '哈希校验工具', icon: 'el-icon-key' }
      }
    ]
  },

  // ------------------------------ 公益社团（受助者 / 项目发起方） ------------------------------
  {
    path: '/club',
    component: Layout,
    meta: { title: '社团工作台', icon: 'el-icon-office-building', roles: ['CLUB'] },
    children: [
      {
        path: 'projects',
        name: 'ClubProjects',
        component: () => import('@/views/club/MyProjects.vue'),
        meta: { title: '我的众筹项目', icon: 'el-icon-s-management' }
      },
      {
        path: 'project/form',
        name: 'ProjectForm',
        component: () => import('@/views/club/ProjectForm.vue'),
        meta: { title: '发起众筹项目', icon: 'el-icon-plus' }
      },
      {
        path: 'project/edit/:id',
        name: 'ProjectEdit',
        component: () => import('@/views/club/ProjectForm.vue'),
        meta: { title: '编辑众筹项目', hidden: true, activeMenu: '/club/projects' }
      },
      {
        path: 'project/workspace/:id',
        name: 'ProjectWorkspace',
        component: () => import('@/views/club/ProjectWorkspace.vue'),
        meta: { title: '项目执行管理', hidden: true, activeMenu: '/club/projects' }
      },
      {
        path: 'qualification',
        name: 'ClubQualification',
        component: () => import('@/views/club/Qualification.vue'),
        meta: { title: '社团资质认证', icon: 'el-icon-postcard' }
      }
    ]
  },

  // ------------------------------ 平台管理员 ------------------------------
  {
    path: '/admin',
    component: Layout,
    meta: { title: '管理后台', icon: 'el-icon-setting', roles: ['ADMIN'] },
    children: [
      { path: 'finance', name: 'FinanceOrders', component: () => import('@/views/finance/OrderWorkspace.vue'), meta: { title: '捐赠财务确认', icon: 'el-icon-coin' } },
      { path: 'identities', name: 'IdentityReview', component: () => import('@/views/finance/CampusIdentity.vue'), meta: { title: '校园身份审核', icon: 'el-icon-postcard' } },
      { path: 'refunds', name: 'FinanceRefunds', component: () => import('@/views/finance/RefundWorkspace.vue'), meta: { title: '退款审核与完成', icon: 'el-icon-refresh-left' } },
      { path: 'evidence-tasks', name: 'EvidenceTasks', component: () => import('@/views/admin/EvidenceTasks.vue'), meta: { title: '存证任务与重试', icon: 'el-icon-link' } },
      {
        path: 'audit',
        name: 'ProjectAudit',
        component: () => import('@/views/admin/ProjectAudit.vue'),
        meta: { title: '项目审核', icon: 'el-icon-document-checked' }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户与权限', icon: 'el-icon-user-solid' }
      },
      {
        path: 'qualifications',
        name: 'QualificationAudit',
        component: () => import('@/views/admin/QualificationAudit.vue'),
        meta: { title: '社团资质审核', icon: 'el-icon-s-check' }
      },
      {
        path: 'statistics',
        name: 'AdminStatistics',
        component: () => import('@/views/admin/Statistics.vue'),
        meta: { title: '数据统计报表', icon: 'el-icon-data-line' }
      },
      {
        path: 'publicity',
        name: 'PublicityComplaint',
        component: () => import('@/views/admin/PublicityComplaint.vue'),
        meta: { title: '公示与纠纷', icon: 'el-icon-chat-line-square' }
      },
      {
        path: 'system',
        name: 'SystemManage',
        component: () => import('@/views/admin/SystemManage.vue'),
        meta: { title: '日志与合约', icon: 'el-icon-monitor' }
      },
      {
        path: 'project/:id',
        name: 'AdminProjectDetail',
        component: () => import('@/views/donor/ProjectDetail.vue'),
        meta: { title: '项目详情', hidden: true, activeMenu: '/admin/audit' }
      }
    ]
  },

  // ------------------------------ 监管审计方 ------------------------------
  {
    path: '/auditor',
    component: Layout,
    meta: { title: '监管审计', icon: 'el-icon-s-claim', roles: ['AUDITOR'] },
    children: [
      { path: 'orders', name: 'AuditOrders', component: () => import('@/views/finance/OrderWorkspace.vue'), meta: { title: '订单财务记录', icon: 'el-icon-tickets' } },
      { path: 'refunds', name: 'AuditRefunds', component: () => import('@/views/finance/RefundWorkspace.vue'), meta: { title: '退款追踪', icon: 'el-icon-refresh-left' } },
      {
        path: 'projects',
        name: 'AuditProjects',
        component: () => import('@/views/auditor/ProjectVerify.vue'),
        meta: { title: '项目真实性核验', icon: 'el-icon-document-checked' }
      },
      {
        path: 'evidence',
        name: 'AuditEvidence',
        component: () => import('@/views/auditor/EvidenceVerify.vue'),
        meta: { title: '存证哈希验证', icon: 'el-icon-key' }
      },
      {
        path: 'fund-trace',
        name: 'AuditFundTrace',
        component: () => import('@/views/auditor/FundTrace.vue'),
        meta: { title: '资金链追踪', icon: 'el-icon-coin' }
      },
      {
        path: 'reports',
        name: 'AuditReport',
        component: () => import('@/views/auditor/AuditReport.vue'),
        meta: { title: '审计报告', icon: 'el-icon-notebook-2' }
      },
      {
        path: 'rules',
        name: 'AuditRule',
        component: () => import('@/views/auditor/AuditRule.vue'),
        meta: { title: '审核规则配置', icon: 'el-icon-set-up' }
      },
      {
        path: 'warnings',
        name: 'WarningList',
        component: () => import('@/views/auditor/WarningList.vue'),
        meta: { title: '异常交易预警', icon: 'el-icon-warning-outline' }
      }
    ]
  }
]

const makeRouter = () => createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: constantRoutes
})

const router = makeRouter()

const removers = []
export function addRoleRoute(route) { removers.push(router.addRoute(route)) }
export function resetRouter() { while (removers.length) removers.pop()() }


export default router
