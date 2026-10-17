import request from '@/utils/request'

/** 捐赠者：浏览已上架项目 */
export function listProjects(params) {
  return request({ url: '/projects', method: 'get', params })
}

/** 项目详情（include 收藏） */
export function getProject(projectId) {
  return request({ url: `/projects/${projectId}`, method: 'get' })
}

/** 收藏 / 取消收藏 */
export function toggleFavorite(projectId) {
  return request({ url: `/projects/${projectId}/favorite`, method: 'post' })
}

/** 我的收藏 */
export function myFavorites(params) {
  return request({ url: '/projects/favorites/mine', method: 'get', params })
}

/** 分享项目（extends） */
export function shareProject(projectId, channel) {
  return request({ url: `/projects/${projectId}/share`, method: 'post', params: { channel } })
}

/** 评价项目 */
export function reviewProject(data) {
  return request({ url: '/projects/reviews', method: 'post', data })
}

/** 项目评价列表 */
export function listReviews(projectId, params) {
  return request({ url: `/projects/${projectId}/reviews`, method: 'get', params })
}

/** 查看资金流向 */
export function projectFundFlow(projectId) {
  return request({ url: `/projects/${projectId}/fund-flow`, method: 'get' })
}

/** 项目进度 */
export function projectProgress(projectId) {
  return request({ url: `/projects/${projectId}/progress`, method: 'get' })
}

/** 项目材料 */
export function projectMaterials(projectId) {
  return request({ url: `/projects/${projectId}/materials`, method: 'get' })
}

/** 项目审核记录 */
export function projectAudits(projectId) {
  return request({ url: `/projects/${projectId}/audits`, method: 'get' })
}

/** 项目捐赠人排行（脱敏） */
export function projectDonors(projectId, limit = 10) {
  return request({ url: `/projects/${projectId}/donors`, method: 'get', params: { limit } })
}

// ---------------------------- 公益社团（受助者） ----------------------------

/** 发布项目 */
export function createProject(data) {
  return request({ url: '/club/projects', method: 'post', data })
}

/** 编辑项目 */
export function updateProject(data) {
  return request({ url: '/club/projects', method: 'put', data })
}

/** 提交审核 */
export function submitProject(projectId) {
  return request({ url: `/club/projects/${projectId}/submit`, method: 'post' })
}

/** 本社团项目 */
export function myProjects(params) {
  return request({ url: '/club/projects/mine', method: 'get', params })
}

/** 项目审核状态 */
export function clubProjectAudits(projectId) {
  return request({ url: `/club/projects/${projectId}/audits`, method: 'get' })
}

/** 项目捐赠明细 */
export function clubProjectDonations(projectId, limit = 50) {
  return request({ url: `/club/projects/${projectId}/donations`, method: 'get', params: { limit } })
}

/** 提交项目 / 帮扶证明材料 */
export function uploadMaterial(data) {
  return request({ url: '/club/projects/materials', method: 'post', data })
}

export function clubMaterials(projectId) {
  return request({ url: `/club/projects/${projectId}/materials`, method: 'get' })
}

/** 新增项目进度 */
export function addProgress(data) {
  return request({ url: '/club/projects/progress', method: 'post', data })
}

export function clubProgress(projectId) {
  return request({ url: `/club/projects/${projectId}/progress`, method: 'get' })
}

/** 项目结项 */
export function finishProject(projectId) {
  return request({ url: `/club/projects/${projectId}/finish`, method: 'post' })
}

/** 回复评价 */
export function replyReview(data) {
  return request({ url: '/club/projects/reviews/reply', method: 'post', data })
}
