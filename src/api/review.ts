/**
 * 记录审核接口
 */

import { http } from './request'
import type { RecordRevision, WorkRecord } from '@/core/types'

export function approveRecord(id: string): Promise<void> {
  return http.post<void>(`/records/${id}/approve`)
}

export function approveRecords(ids: string[]): Promise<{ success: number; failed: number }> {
  return http.post<{ success: number; failed: number }>('/records/approve-batch', { ids })
}

export function rejectRecord(id: string, reason: string): Promise<void> {
  return http.post<void>(`/records/${id}/reject`, { reason })
}

export function reviseRecord(
  id: string,
  data: {
    serviceDate?: string
    startTime?: string
    endTime?: string
    location?: string
    content?: string
    note: string
  },
): Promise<WorkRecord> {
  return http.put<WorkRecord>(`/records/${id}/revise`, data)
}

export function listRevisions(id: string): Promise<RecordRevision[]> {
  return http.get<RecordRevision[]>(`/records/${id}/revisions`)
}
