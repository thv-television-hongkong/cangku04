import request from '@/utils/request'

/** 监管：查看全部项目（项目真实性核验） */
export function auditProjects(params) {
  return request({ url: '/audit/projects', method: 'get', params })
}

export function auditProjectDetail(projectId) {
  return request({ url: `/audit/projects/${projectId}`, method: 'get' })
}

/** 存证查询与校验 */
export function auditEvidenceQuery(data) {
  return request({ url: '/audit/evidence/query', method: 'post', data })
}

export function auditEvidenceVerify(data) {
  return request({ url: '/audit/evidence/verify', method: 'post', data })
}

/** 资金链追踪 */
export function auditFundTrace(projectId) {
  return request({ url: `/audit/fund-trace/${projectId}`, method: 'get' })
}

export function auditChainOverview() {
  return request({ url: '/audit/chain/overview', method: 'get' })
}

// ------------------------------ 预警规则 ------------------------------

export function listRules(params) {
  return request({ url: '/audit/rules', method: 'get', params })
}

export function saveRule(data) {
  return request({ url: '/audit/rules', method: 'post', data })
}

export function updateRule(data) {
  return request({ url: '/audit/rules', method: 'put', data })
}

export function toggleRule(ruleId, enabled) {
  return request({ url: `/audit/rules/${ruleId}/toggle`, method: 'post', params: { enabled } })
}

export function deleteRule(ruleId) {
  return request({ url: `/audit/rules/${ruleId}`, method: 'delete' })
}

// ------------------------------ 异常预警 ------------------------------

export function listWarnings(params) {
  return request({ url: '/audit/warnings', method: 'get', params })
}

export function handleWarning(warningId, handleStatus, opinion) {
  return request({ url: `/audit/warnings/${warningId}/handle`, method: 'post', params: { handleStatus, opinion } })
}

export function warningStatistics() {
  return request({ url: '/audit/warnings/statistics', method: 'get' })
}

// ------------------------------ 审计报告 ------------------------------

export function generateReport(params) {
  return request({ url: '/audit/reports', method: 'post', params })
}

export function listReports(params) {
  return request({ url: '/audit/reports', method: 'get', params })
}

export function reportDetail(reportId) {
  return request({ url: `/audit/reports/${reportId}`, method: 'get' })
}

export function exportReport(reportId) {
  return request({ url: `/audit/reports/${reportId}/export`, method: 'get' })
}
