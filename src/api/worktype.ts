/**
 * 工作类型接口
 */

import { http } from './request'
import type { BillingMode, WorkType } from '@/core/types'

export function listWorkTypes(): Promise<WorkType[]> {
  return http.get<WorkType[]>('/work-types')
}

export function listEnabledWorkTypes(): Promise<WorkType[]> {
  return http.get<WorkType[]>('/work-types/enabled')
}

export function createWorkType(data: { name: string; billingMode: BillingMode }): Promise<WorkType> {
  return http.post<WorkType>('/work-types', data)
}

export function updateWorkType(data: Partial<WorkType> & { id: string }): Promise<WorkType> {
  const { id, ...rest } = data
  return http.put<WorkType>(`/work-types/${id}`, rest)
}

export function deleteWorkType(id: string): Promise<{ disabled: boolean }> {
  return http.del<{ disabled: boolean }>(`/work-types/${id}`)
}
