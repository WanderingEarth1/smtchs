/**
 * 照片处理：前端压缩后上传（非功能需求「照片」）
 * 目标：≤300KB/张，最长边 1280px
 */
import type { PhotoRef } from '@/core/types'
import { PHOTO_MAX_BYTES } from '@/data/service'

const MAX_EDGE = 1280
const QUALITY_START = 0.82
const QUALITY_MIN = 0.4

/** 读取文件为 dataURL */
function readAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('照片读取失败'))
    reader.readAsDataURL(file)
  })
}

/** 加载图片 */
function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('照片解析失败'))
    img.src = src
  })
}

/** dataURL 的近似字节数 */
function dataUrlBytes(dataUrl: string): number {
  const idx = dataUrl.indexOf(',')
  const b64 = idx >= 0 ? dataUrl.slice(idx + 1) : dataUrl
  return Math.round((b64.length * 3) / 4)
}

/**
 * 压缩图片：等比缩放到最长边 1280，逐步降低质量直到 ≤300KB
 */
export async function compressPhoto(file: File): Promise<PhotoRef> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  if (file.size > 20 * 1024 * 1024) {
    throw new Error('图片过大（超过 20MB），请重新拍摄')
  }

  const raw = await readAsDataURL(file)
  const img = await loadImage(raw)

  const scale = Math.min(1, MAX_EDGE / Math.max(img.width, img.height))
  const w = Math.max(1, Math.round(img.width * scale))
  const h = Math.max(1, Math.round(img.height * scale))

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('当前浏览器不支持图片压缩')

  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(img, 0, 0, w, h)

  let quality = QUALITY_START
  let dataUrl = canvas.toDataURL('image/jpeg', quality)

  while (dataUrlBytes(dataUrl) > PHOTO_MAX_BYTES && quality > QUALITY_MIN) {
    quality = Math.max(QUALITY_MIN, quality - 0.1)
    dataUrl = canvas.toDataURL('image/jpeg', quality)
  }

  return {
    id: `ph_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`,
    dataUrl,
    name: file.name || '现场照片.jpg',
    size: dataUrlBytes(dataUrl),
  }
}

/** 采集 GPS 定位（失败不阻断提交） */
export interface GeoResult {
  ok: boolean
  latitude: number | null
  longitude: number | null
  message: string
}

export function captureGeo(timeoutMs = 8000): Promise<GeoResult> {
  return new Promise((resolve) => {
    if (!('geolocation' in navigator)) {
      resolve({ ok: false, latitude: null, longitude: null, message: '当前浏览器不支持定位' })
      return
    }

    let settled = false
    const timer = window.setTimeout(() => {
      if (settled) return
      settled = true
      resolve({ ok: false, latitude: null, longitude: null, message: '定位超时，已标记未获取定位' })
    }, timeoutMs)

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (settled) return
        settled = true
        window.clearTimeout(timer)
        resolve({
          ok: true,
          latitude: Math.round(pos.coords.latitude * 1e6) / 1e6,
          longitude: Math.round(pos.coords.longitude * 1e6) / 1e6,
          message: '定位成功',
        })
      },
      (err) => {
        if (settled) return
        settled = true
        window.clearTimeout(timer)
        const message =
          err.code === err.PERMISSION_DENIED
            ? '定位权限被拒绝，已标记未获取定位'
            : '定位获取失败，已标记未获取定位'
        resolve({ ok: false, latitude: null, longitude: null, message })
      },
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 60000 },
    )
  })
}
