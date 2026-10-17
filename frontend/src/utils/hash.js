import CryptoJS from 'crypto-js'

/**
 * 前端哈希工具（哈希校验工具页使用）。
 *
 * 说明：正式存证哈希由后端按确定性规则计算后上链；本工具用于让用户
 * 本地计算文件 SHA-256，再与链上存证哈希比对，验证文件是否被篡改。
 */
export function sha256OfFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = e => {
      const wordArray = CryptoJS.lib.WordArray.create(e.target.result)
      resolve(CryptoJS.SHA256(wordArray).toString(CryptoJS.enc.Hex))
    }
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}

export function sha256OfText(text) {
  return CryptoJS.SHA256(text || '').toString(CryptoJS.enc.Hex)
}

/** 生成分享链接 */
export function buildShareLink(projectId) {
  return window.location.origin + '/#/donor/project/' + projectId
}
