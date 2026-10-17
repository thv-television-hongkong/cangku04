import { login, logout as logoutApi, getProfile } from '@/api/auth'
import { getToken, setToken, removeToken, getUser, setUser, removeUser } from '@/utils/auth'

const state = {
  token: getToken(),
  userInfo: getUser() || {},
  role: (getUser() || {}).role || '',
  permissions: (getUser() || {}).permissions || [],
  routesLoaded: false
}

const mutations = {
  SET_TOKEN(state, token) {
    state.token = token
  },
  SET_USER(state, user) {
    state.userInfo = user || {}
    state.role = (user || {}).role || ''
    state.permissions = (user || {}).permissions || []
  },
  SET_ROUTES_LOADED(state, loaded) {
    state.routesLoaded = loaded
  }
}

const actions = {
  /** 登录：保存 Token 与用户信息（含角色权限） */
  async login({ commit }, form) {
    const res = await login(form)
    const data = res.data || {}
    setToken(data.token)
    setUser(data)
    commit('SET_TOKEN', data.token)
    commit('SET_USER', data)
    return data
  },

  /** 拉取最新用户信息 */
  async fetchProfile({ commit, state }) {
    const res = await getProfile()
    const merged = Object.assign({}, state.userInfo, res.data)
    setUser(merged)
    commit('SET_USER', merged)
    return merged
  },

  /** 退出登录 */
  async logout({ commit }, options = {}) {
    if (!options.localOnly) {
      try {
        await logoutApi()
      } catch (e) {
        // 后端无状态，忽略失败
      }
    }
    removeToken()
    removeUser()
    commit('SET_TOKEN', '')
    commit('SET_USER', {})
    commit('SET_ROUTES_LOADED', false)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
