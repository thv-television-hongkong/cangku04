import request from '@/utils/request'

/** 上传原始文件（返回 SHA-256 哈希，原始文件存业务服务器） */
export function uploadFile(file, bizType, bizId) {
  const formData = new FormData()
  formData.append('file', file)
  if (bizType) formData.append('bizType', bizType)
  if (bizId) formData.append('bizId', bizId)
  return request({
    url: '/file/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 文件下载地址 */
export function fileDownloadUrl(fileId) {
  return (process.env.VUE_APP_BASE_API || '/api') + '/file/download/' + fileId
}

/** 经鉴权下载受控原件。 */
export async function downloadFile(fileId) {
  const response = await request({ url: `/file/download/${fileId}`, method: 'get', responseType: 'blob' })
  if ((response.headers['content-type'] || '').includes('application/json')) {
    const error = JSON.parse(await response.data.text())
    throw new Error(error.message || '文件下载失败')
  }
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  const disposition = response.headers['content-disposition'] || ''
  anchor.download = decodeURIComponent((disposition.split('filename=')[1] || `凭证-${fileId}`).replace(/^"|"$/g, ''))
  anchor.href = url
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** 上传文件并与登记哈希比对 */
export function verifyUploadedFile(fileId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/file/verify/${fileId}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 校验服务器原始文件是否被篡改 */
export function verifyStoredFile(fileId) {
  return request({ url: `/file/verify-stored/${fileId}`, method: 'get' })
}
