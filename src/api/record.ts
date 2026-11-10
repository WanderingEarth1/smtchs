/**
 * 服务记录接口
 * 后端：SmtRecordController（/smtchs/record）
 *
 * 注意：后端返回扁平 snap* 字段，前端领域模型是嵌套 billingSnapshot，
 * 因此在 normalizeRecord 里做适配。
 */
import { http, getPage } from './request'
import type { BillingSnapshot, PhotoRef, RecordStatus, WorkRecord } from '@/core/types'

/** 后端记录 DTO（扁平 snap 字段） */
export interface RecordDto {
  id: string | number
  serialNo: string
  employeeId: string | number
  employeeName?: string
  workTypeId: string | number
  workTypeName?: string
  serviceDate: string
  startTime: string
  endTime?: string | null
  location: string
  latitude?: number | null
  longitude?: number | null
  geoCaptured?: number | boolean
  content: string
  remark?: string
  status: string
  isBackfill?: number | boolean
  rejectReason?: string | null
  submittedAt?: string
  confirmedAt?: string | null
  rejectedAt?: string | null
  snapBillingMode: string
  snapUnitPrice: number
  snapOvertimeStart: string
  snapOvertimeMultiplier: number
  snapRoundingRule: string
  photos?: Array<{ id: string | number; url: string; size?: number }> | null
}

/** 布尔字段兼容：后端 TINYINT 可能是 0/1 或 true/false */
function flag(v: unknown): boolean {
  return v === true || v === 1 || v === '1'
}

/** 把后端 DTO 转为前端领域模型 */
export function normalizeRecord(dto: RecordDto): WorkRecord {
  const snapshot: BillingSnapshot = {
    billingMode: dto.snapBillingMode as BillingSnapshot['billingMode'],
    unitPrice: Number(dto.snapUnitPrice) || 0,
    overtimeStart: dto.snapOvertimeStart || '18:00',
    overtimeMultiplier: Number(dto.snapOvertimeMultiplier) || 2,
    roundingRule: dto.snapRoundingRule as BillingSnapshot['roundingRule'],
  }

  const photos: PhotoRef[] = (dto.photos ?? []).map((p) => ({
    id: String(p.id),
    dataUrl: p.url,
    name: p.url.split('/').pop() ?? '照片',
    size: p.size ?? 0,
  }))

  return {
    id: String(dto.id),
    serialNo: dto.serialNo,
    employeeId: String(dto.employeeId),
    workTypeId: String(dto.workTypeId),
    serviceDate: dto.serviceDate,
    startTime: dto.startTime,
    endTime: dto.endTime ?? null,
    location: dto.location,
    latitude: dto.latitude ?? null,
    longitude: dto.longitude ?? null,
    geoCaptured: flag(dto.geoCaptured),
    content: dto.content,
    remark: dto.remark ?? '',
    photos,
    status: dto.status as RecordStatus,
    isBackfill: flag(dto.isBackfill),
    rejectReason: dto.rejectReason ?? null,
    submittedAt: dto.submittedAt ?? '',
    confirmedAt: dto.confirmedAt ?? null,
    rejectedAt: dto.rejectedAt ?? null,
    billingSnapshot: snapshot,
  }
}

/** 提交草稿 */
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

/** 我的记录（按月 + 状态） */
export async function listMyRecords(month: string, status?: string): Promise<WorkRecord[]> {
  const list = await http.get<RecordDto[]>('/record/my', { month, status })
  return (list || []).map(normalizeRecord)
}

/** 全部记录（管理端筛选） */
export async function listAllRecords(params: {
  month?: string
  status?: string
  employeeId?: string
  workTypeId?: string
  keyword?: string
}): Promise<WorkRecord[]> {
  const page = await getPage<RecordDto>('/record/list', params)
  return (page.rows || []).map(normalizeRecord)
}

/** 记录详情 */
export async function getRecord(id: string): Promise<WorkRecord | undefined> {
  const dto = await http.get<RecordDto | null>(`/record/${id}`)
  return dto ? normalizeRecord(dto) : undefined
}

/** 新增记录 */
export async function createRecord(draft: RecordDraft): Promise<WorkRecord> {
  const dto = await http.post<RecordDto>('/record', draft)
  return normalizeRecord(dto)
}

/** 修改记录（后端用 @RequestParam 接收 id） */
export async function updateRecord(id: string, draft: RecordDraft): Promise<WorkRecord> {
  const dto = await http.put<RecordDto>(`/record?id=${encodeURIComponent(id)}`, draft)
  return normalizeRecord(dto)
}

/** 删除记录 */
export async function deleteRecord(id: string): Promise<void> {
  await http.del(`/record/${id}`)
}
