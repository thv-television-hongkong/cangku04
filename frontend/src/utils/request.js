import axios from 'axios'
import { ElMessage as Message, ElMessageBox as MessageBox } from 'element-plus'
import store from '@/store'
import router from '@/router'
import { getToken } from '@/utils/auth'

/**
 * 统一请求封装：自动携带 JWT，统一拦截业务错误码。
 * 后端返回结构：{ code, message, data, timestamp }
 */
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || '/api',
  timeout: 30000
})

function isLoginPage() {
  const hash = window.location.hash || ''
  return router.currentRoute.value.path === '/login' || /^#\/login(?:[/?]|$)/.test(hash)
}

service.interceptors.request.use(
  config => {
    const token = getToken()
    if (token && config.skipAuth !== true) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    // 文件下载等 blob 响应直接返回
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    const silentError = response.config.skipErrorMessage === true
    if (res.code === undefined) {
      return res
    }
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      if (isLoginPage()) {
        return Promise.reject(Object.assign(new Error(res.message || '未登录'), { code: res.code }))
      }
      MessageBox.confirm('登录状态已过期，请重新登录', '提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        store.dispatch('user/logout').then(() => {
          router.push('/login')
        })
      })
      return Promise.reject(Object.assign(new Error(res.message || '未登录'), { code: res.code }))
    }
    // A stale protected request can finish while the login page is being
    // restored. Do not show a misleading permission toast over that page.
    if (!silentError && (!isLoginPage() || res.code !== 403)) {
      Message({ message: res.message || '请求失败', type: 'error', duration: 4000 })
    }
    return Promise.reject(Object.assign(new Error(res.message || '请求失败'), { code: res.code }))
  },
  error => {
    let message = error.message
    if (message && message.includes('timeout')) {
      message = '请求超时，请稍后重试'
    } else if (error.response && error.response.status === 403) {
      message = '无权限访问该功能'
    } else if (error.response && error.response.status === 401) {
      message = '未登录或登录已过期'
    }
    if (!error.config || error.config.skipErrorMessage !== true) {
      if (!isLoginPage() || (error.response && error.response.status !== 403)) {
      Message({ message: message || '网络异常', type: 'error', duration: 4000 })
      }
    }
    return Promise.reject(error)
  }
)

export default service

/** 下载文件（导出 Excel / 存证数据 / 审计报告） */
export function downloadBlob(blob, fileName) {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', fileName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
