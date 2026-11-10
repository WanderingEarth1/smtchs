/**
 * 审核接口
 * 后端：SmtReviewController（/smtchs/review）
 */
import { http } from './request'
import type { RecordRevision } from '@/core/types'
import { normalizeRecord, type RecordDto } from './record'

/** 后端修正痕迹 DTO */
export interface RevisionDto {
  id: string | number
  recordId: string | number
  operatorName: string
  revisedAt: string
  beforeJson: string
  afterJson: string
  note: string
}

/** 把 JSON 字符串快照解析为对象 */
function parseSnapshot(json: string | null | undefined): Record<string, unknown> {
  if (!json) return {}
  try {
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

/** 确认单条 */
export async function approveRecord(id: string): Promise<void> {
  await http.put(`/review/approve/${id}`)
}

/** 批量确认（body 为裸数组） */
export async function approveRecords(
  ids: string[],
): Promise<{ success: number; failed: number }> {
  const result = await http.put<{ success: number; failed: number }>(
    '/review/approve-batch',
    ids.map((v) => Number(v) || v),
  )
  return result ?? { success: 0, failed: 0 }
}

/** 驳回 */
export async function rejectRecord(id: string, reason: string): Promise<void> {
  await http.put(`/review/reject/${id}`, { reason })
}

/** 修正已确认记录 */
export async function reviseRecord(
  id: string,
  data: {
    serviceDate?: string
    startTime?: string
    endTime?: string
    location?: string
    content?: string
    note: string
  },
): Promise<void> {
  await http.put(`/review/revise/${id}`, data)
}

/** 修正痕迹（把 beforeJson/afterJson 解析为对象） */
export async function listRevisions(recordId: string): Promise<RecordRevision[]> {
  const list = await http.get<RevisionDto[]>(`/review/revisions/${recordId}`)
  return (list || []).map((d) => ({
    id: String(d.id),
    recordId: String(d.recordId),
    operatorName: d.operatorName,
    revisedAt: d.revisedAt,
    before: parseSnapshot(d.beforeJson),
    after: parseSnapshot(d.afterJson),
    note: d.note,
  }))
}

export { normalizeRecord, type RecordDto }
