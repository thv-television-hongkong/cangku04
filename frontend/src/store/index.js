import { createStore } from 'vuex'
import user from './modules/user'


export default createStore({
  modules: {
    user
  },
  getters: {
    token: state => state.user.token,
    userInfo: state => state.user.userInfo,
    role: state => state.user.role,
    permissions: state => state.user.permissions || []
  }
})
