import request from '@/utils/request'

/** 运营数据总览 */
export function dashboard() {
  return request({ url: '/admin/dashboard', method: 'get' })
}

/** 数据统计与报表 */
export function statistics() {
  return request({ url: '/admin/statistics', method: 'get' })
}

// ------------------------------ 用户管理 ------------------------------

export function listUsers(params) {
  return request({ url: '/admin/users', method: 'get', params })
}

export function createSystemUser(data) {
  return request({ url: '/admin/users', method: 'post', data })
}

export function updateUserStatus(userId, status, remark) {
  return request({ url: `/admin/users/${userId}/status`, method: 'put', params: { status, remark } })
}

export function updateUserRole(userId, role) {
  return request({ url: `/admin/users/${userId}/role`, method: 'put', params: { role } })
}

export function resetUserPassword(userId) {
  return request({ url: `/admin/users/${userId}/reset-password`, method: 'put' })
}

/** 社团资质审核 */
export function listQualifications(params) {
  return request({ url: '/admin/qualifications', method: 'get', params })
}

export function auditQualification(data) {
  return request({ url: '/admin/qualifications/audit', method: 'post', data })
}

// ------------------------------ 项目审核 ------------------------------

export function adminListProjects(params) {
  return request({ url: '/admin/projects', method: 'get', params })
}

export function adminProjectDetail(projectId) {
  return request({ url: `/admin/projects/${projectId}`, method: 'get' })
}

export function auditProject(data) {
  return request({ url: '/admin/projects/audit', method: 'post', data })
}

export function offlineProject(data) {
  return request({ url: '/admin/projects/offline', method: 'post', data })
}

export function onlineProject(projectId) {
  return request({ url: `/admin/projects/${projectId}/online`, method: 'post' })
}

// ------------------------------ 系统日志 ------------------------------

export function listLogs(params) {
  return request({ url: '/admin/logs', method: 'get', params })
}

export function logStatistics() {
  return request({ url: '/admin/logs/statistics', method: 'get' })
}

// ------------------------------ 公示 / 投诉 ------------------------------

export function listPublicity(params) {
  return request({ url: '/admin/publicity', method: 'get', params })
}

export function publishPublicity(data) {
  return request({ url: '/admin/publicity', method: 'post', data })
}

export function revokePublicity(publicityId) {
  return request({ url: `/admin/publicity/${publicityId}/revoke`, method: 'post' })
}

export function listComplaints(params) {
  return request({ url: '/admin/complaints', method: 'get', params })
}

export function handleComplaint(complaintId, status, result) {
  return request({ url: `/admin/complaints/${complaintId}/handle`, method: 'post', params: { status, result } })
}

// ------------------------------ 备份 / 合约 ------------------------------

export function listBackups(params) {
  return request({ url: '/admin/backups', method: 'get', params })
}

export function runBackup() {
  return request({ url: '/admin/backups/run', method: 'post' })
}

export function listContracts() {
  return request({ url: '/admin/contracts', method: 'get' })
}

export function recordContract(data) {
  return request({ url: '/admin/contracts', method: 'post', data })
}

export function chainStatus() {
  return request({ url: '/admin/chain/status', method: 'get' })
}

// ------------------------------ 用户侧投诉 ------------------------------

export function submitComplaint(data) {
  return request({ url: '/complaints', method: 'post', data })
}

export function myComplaints(params) {
  return request({ url: '/complaints/mine', method: 'get', params })
}
