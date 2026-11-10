/**
 * 系统配置接口
 */

import { http } from './request'
import type { SysConfig } from '@/core/types'

export function getConfig(): Promise<SysConfig> {
  return http.get<SysConfig>('/config')
}

export function updateConfig(data: Partial<SysConfig>): Promise<SysConfig> {
  return http.put<SysConfig>('/config', data)
}
