/**
 * 服务记录接口
 */

import { http } from './request'
import type { WorkRecord } from '@/core/types'

export interface RecordDraft {
  workTypeId: string
  serviceDate: string
  startTime: string
  endTime: string | null
  location: string
  latitude: number | null
  longitude: number | null
  geoCaptured: boolean
  content: string
  remark: string
  photoUrls: string[]
}

export function listMyRecords(month: string, status?: string): Promise<WorkRecord[]> {
  return http.get<WorkRecord[]>('/records/mine', { month, status })
}

export function getRecord(id: string): Promise<WorkRecord> {
  return http.get<WorkRecord>(`/records/${id}`)
}

export function createRecord(draft: RecordDraft): Promise<WorkRecord> {
  return http.post<WorkRecord>('/records', draft)
}

export function updateRecord(id: string, draft: RecordDraft): Promise<WorkRecord> {
  return http.put<WorkRecord>(`/records/${id}`, draft)
}

export function deleteRecord(id: string): Promise<void> {
  return http.del<void>(`/records/${id}`)
}

export function listAllRecords(params: {
  month?: string
  status?: string
  employeeId?: string
  workTypeId?: string
  keyword?: string
}): Promise<WorkRecord[]> {
  return http.get<WorkRecord[]>('/records', params)
}
