import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/legacy-icons.css'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import store from './store'
import './permission'
import './styles/index.scss'
import { removeToken, removeUser } from '@/utils/auth'

// Opening the identity-neutral login entry must never inherit a session from
// another account, browser tab, or LAN address.
if (/^#\/login(?:[/?]|$)/.test(window.location.hash)) {
  removeToken()
  removeUser()
}

const events = new Map()
const bus = {
  $on(name, fn) { if (!events.has(name)) events.set(name, new Set()); events.get(name).add(fn) },
  $off(name, fn) { events.get(name)?.delete(fn) },
  $emit(name, ...args) { events.get(name)?.forEach(fn => fn(...args)) }
}
const app = createApp(App)
app.use(store).use(router).use(ElementPlus, { locale: zhCn, size: 'default' })
app.config.globalProperties.$echarts = echarts
app.config.globalProperties.$bus = bus
app.mount('#app')
