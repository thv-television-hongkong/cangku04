import request from '@/utils/request'

/** 发送验证码（注册 / 找回密码） */
export function sendCode(data) {
  return request({ url: '/auth/send-code', method: 'post', data })
}

/** 注册（学生 / 社团） */
export function register(data) {
  return request({ url: '/auth/register', method: 'post', data })
}

/** 登录 */
export function login(data) {
  // Login must be independent from a stale session left by another account.
  return request({ url: '/auth/login', method: 'post', data, timeout: 10000, skipAuth: true, skipErrorMessage: true })
}

/** 退出登录 */
export function logout() {
  return request({ url: '/auth/logout', method: 'post' })
}

/** 找回密码 */
export function resetPassword(data) {
  return request({ url: '/auth/reset-password', method: 'post', data })
}

/** 当前用户信息 */
export function getProfile() {
  return request({ url: '/user/profile', method: 'get' })
}

/** 个人信息维护 */
export function updateProfile(data) {
  return request({ url: '/user/profile', method: 'put', data })
}

/** 修改密码 */
export function changePassword(data) {
  return request({ url: '/user/password', method: 'put', data })
}

/** 社团提交资质 */
export function submitQualification(data) {
  return request({ url: '/user/qualification', method: 'post', data })
}

/** 当前社团资质状态 */
export function getQualification() {
  return request({ url: '/user/qualification', method: 'get' })
}

/** 站内通知 */
export function getNotifications() {
  return request({ url: '/user/notifications', method: 'get' })
}

export function getUnreadCount() {
  return request({ url: '/user/notifications/unread', method: 'get' })
}

export function markNotificationsRead(ids) {
  return request({ url: '/user/notifications/read', method: 'put', data: ids })
}
