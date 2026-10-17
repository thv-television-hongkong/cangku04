import moment from 'moment'

/** 时间格式化 */
export function formatTime(value, pattern = 'YYYY-MM-DD HH:mm:ss') {
  if (!value) return '-'
  return moment(value).format(pattern)
}

/** 金额格式化（保留两位小数，添加千分位） */
export function formatAmount(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (isNaN(num)) return value
  return num.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 哈希缩写展示 */
export function shortHash(hash, length = 16) {
  if (!hash) return '-'
  if (hash.length <= length) return hash
  return hash.substring(0, length) + '...' + hash.substring(hash.length - 8)
}

/** 百分比 */
export function percent(current, target) {
  if (!target || Number(target) === 0) return 0
  const value = (Number(current || 0) * 100) / Number(target)
  return Math.min(Math.round(value), 100)
}

/** 捐赠状态标签 */
export const donationStatusMap = {
  PAID: { label: '已支付（可撤回）', type: 'warning' },
  CONFIRMED: { label: '已入账', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 项目状态标签 */
export const projectStatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审核', type: 'warning' },
  REJECTED: { label: '审核驳回', type: 'danger' },
  ONLINE: { label: '众筹中', type: 'success' },
  OFFLINE: { label: '已下架', type: 'danger' },
  FINISHED: { label: '已完成', type: 'primary' },
  SETTLED: { label: '已结算', type: 'info' }
}

/** 审核状态标签 */
export const auditStatusMap = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}

/** 预警级别标签 */
export const warningLevelMap = {
  LOW: { label: '低', type: 'info' },
  MID: { label: '中', type: 'warning' },
  HIGH: { label: '高', type: 'danger' }
}
