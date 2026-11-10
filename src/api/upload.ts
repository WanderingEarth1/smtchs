/**
 * 文件上传接口
 */

import { BASE_URL } from './request'

export interface UploadResult {
  url: string
  size: number
}

export async function uploadPhoto(file: File): Promise<UploadResult> {
  const token = localStorage.getItem('smtchs.token')
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`${BASE_URL}/upload/photo`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  })

  if (!response.ok) throw new Error('上传失败')
  const json = await response.json()
  if (json.code !== 200) throw new Error(json.msg || '上传失败')
  return json.data
}
