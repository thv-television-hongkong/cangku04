import request from '@/utils/request'

/** 多维溯源查询（项目ID / 交易ID / 区块高度 / 业务ID） */
export function traceQuery(data) {
  return request({ url: '/trace/query', method: 'post', data })
}

/** 哈希验真 */
export function verifyHash(data) {
  return request({ url: '/trace/verify', method: 'post', data })
}

/** 仅依据链上数据验真（业务库损坏时仍可核验） */
export function verifyByChain(data) {
  return request({ url: '/trace/verify-by-chain', method: 'post', data })
}

/** 项目全流程溯源 */
export function projectTrace(projectId) {
  return request({ url: `/trace/project/${projectId}`, method: 'get' })
}

/** 文件哈希核验 */
export function verifyFile(fileId) {
  return request({ url: `/trace/file/${fileId}`, method: 'get' })
}

/** 区块链存证总览 */
export function chainOverview() {
  return request({ url: '/trace/overview', method: 'get' })
}

/** 导出存证数据 */
export function exportEvidence(data) {
  return request({ url: '/trace/export', method: 'post', data })
}
