import request from '@/utils/request'

/** 捐款 / 捐赠（上链存证） */
export function donate(data) {
  return request({ url: '/donations', method: 'post', data })
}

/** 取消捐赠（extends） */
export function cancelDonation(data) {
  return request({ url: '/donations/cancel', method: 'post', data })
}

/** 我的捐赠记录 */
export function myDonations(params) {
  return request({ url: '/donations/mine', method: 'get', params })
}

/** 项目捐赠明细 */
export function projectDonations(params) {
  return request({ url: '/donations/project', method: 'get', params })
}

/** 捐赠详情（含存证凭证） */
export function donationDetail(donationId) {
  return request({ url: `/donations/${donationId}`, method: 'get' })
}
