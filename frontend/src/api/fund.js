import request from '@/utils/request'

/** 上传资金使用凭证（extends 查看存证溯源） */
export function uploadFundUsage(data) {
  return request({ url: '/fund/usages', method: 'post', data })
}

/** 资金使用凭证列表 */
export function listFundUsages(params) {
  return request({ url: '/fund/usages', method: 'get', params })
}

/** 审核资金使用凭证 */
export function auditFundUsage(data) {
  return request({ url: '/fund/usages/audit', method: 'post', data })
}

/** 申请资金提取（extends 提交区块链存证） */
export function applyWithdrawal(data) {
  return request({ url: '/fund/withdrawals', method: 'post', data })
}

/** 资金提取列表 */
export function listWithdrawals(params) {
  return request({ url: '/fund/withdrawals', method: 'get', params })
}

/** 审核 / 发放资金提取 */
export function auditWithdrawal(data) {
  return request({ url: '/fund/withdrawals/audit', method: 'post', data })
}

/** 项目资金全景 */
export function fundOverview(projectId) {
  return request({ url: `/fund/overview/${projectId}`, method: 'get' })
}
