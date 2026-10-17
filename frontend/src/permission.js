import router from '@/router'
import store from '@/store'
import { asyncRoutes, constantRoutes, resetRouter, addRoleRoute } from '@/router'
import { getToken } from '@/utils/auth'

const WHITE_LIST = ['/login', '/404']

const ROLE_HOME = {
  STUDENT: '/donor/projects',
  CLUB: '/club/projects',
  ADMIN: '/admin/audit',
  AUDITOR: '/auditor/projects'
}

// Keep the catch-all route after role routes. Vue Router appends dynamic
// routes, so registering '*' in constantRoutes would redirect valid role
// paths such as /admin/audit to /404 before they can match.
const FALLBACK_ROUTE = { path: '/:pathMatch(.*)*', redirect: '/404', hidden: true }

export function roleHome(role) {
  return ROLE_HOME[role] || '/dashboard'
}

/** 根据角色过滤可访问路由（前端菜单控制，后端另有 @PreAuthorize 强制校验） */
function filterRoutes(routes, role) {
  const result = []
  routes.forEach(route => {
    const item = Object.assign({}, route)
    if (item.meta && item.meta.roles && item.meta.roles.length > 0 && item.meta.roles.indexOf(role) < 0) {
      return
    }
    if (item.children && item.children.length > 0) {
      item.children = item.children.filter(child => {
        if (!child.meta || !child.meta.roles || child.meta.roles.length === 0) {
          return true
        }
        return child.meta.roles.indexOf(role) >= 0
      })
      if (item.children.length === 0) {
        return
      }
    }
    result.push(item)
  })
  return result
}

function buildRouter() {
  const role = store.getters.role
  if (!role) {
    return
  }
  store.state.user.userInfo.role = role
  const accessible = filterRoutes(asyncRoutes, role)
  accessible.forEach(route => addRoleRoute(route))
  addRoleRoute(FALLBACK_ROUTE)
}

router.beforeEach(async (to, from, next) => {
  // Vuex is updated before localStorage becomes observable in a few browser
  // event-loop orderings. Accept either source during the transition after
  // login so a freshly authenticated user is not sent back to /login.
  const token = getToken() || store.getters.token

  // /login is always a clean, identity-neutral entry point.  A browser can
  // retain a token from a previous account or from another LAN address; do
  // not restore that stale session while the user is explicitly opening the
  // login page.
  if (to.path === '/login') {
    // A duplicate /login transition can still be finishing while the user
    // submits the form. Only clear an existing session when entering the
    // login page from another route; never erase the token just issued by a
    // successful login while the current route is already /login.
    if (token && from.path !== '/login') {
      await store.dispatch('user/logout', { localOnly: true })
    }
    resetRouter()
    store.commit('user/SET_ROUTES_LOADED', false)
    next()
    return
  }

  if (token) {
    const role = store.getters.role
    if (!role) {
      try {
        await store.dispatch('user/fetchProfile')
      } catch (e) {
        await store.dispatch('user/logout')
        next('/login')
        return
      }
    }
    if (!store.state.user.routesLoaded) {
      buildRouter()
      store.state.user.routesLoaded = true
      next(to.path === '/' ? { path: roleHome(store.getters.role), replace: true } : { ...to, replace: true })
      return
    }
    if (to.path === '/') {
      next({ path: roleHome(store.getters.role), replace: true })
      return
    }
    next()
  } else if (WHITE_LIST.indexOf(to.path) >= 0) {
    next()
  } else {
    next('/login')
  }
})

router.afterEach(() => {
  document.title = (router.currentRoute.value.meta && router.currentRoute.value.meta.title
    ? router.currentRoute.value.meta.title + ' - ' : '') + '校园公益众筹存证溯源系统'
})

export { buildRouter, constantRoutes }
