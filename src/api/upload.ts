/**
 * 照片上传接口
 * 后端：SmtUploadController（/smtchs/upload）
 */
import { BASE_URL } from './request'

export interface UploadResult {
  url: string
  size: number
}

/**
 * 上传照片到后端，返回可持久化的 URL
 *
 * 注意：提交记录时传的是这个 URL，不是 base64 dataURL。
 */
export async function uploadPhoto(file: File): Promise<UploadResult> {
  const token = localStorage.getItem('smtchs.token')
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`${BASE_URL}/upload/photo`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  })

  if (response.status === 401) {
    localStorage.removeItem('smtchs.token')
    location.hash = '#/login'
    throw new Error('登录已过期')
  }

  if (!response.ok) {
    throw new Error(`上传失败（HTTP ${response.status}）`)
  }

  const json = await response.json()
  if (json.code !== 200) {
    throw new Error(json.msg || '上传失败')
  }
  return json.data as UploadResult
}
