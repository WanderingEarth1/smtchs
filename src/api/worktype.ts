/**
 * 工作类型接口
 * 后端：SmtWorkTypeController（/smtchs/worktype）
 *
 * 注意：后端 id 是 Long（JSON 里为数字），前端领域模型用 string，
 * 这里统一转换，否则 getWorkTypeName 等同步查询会因 === 比较失败。
 */
import { http, getPage } from './request'
import type { BillingMode, WorkType } from '@/core/types'

/** 后端工作类型 DTO */
export interface WorkTypeDto {
  id: string | number
  name: string
  billingMode: string
  enabled: number | boolean
  sort?: number
}

/** 归一化：id → string，enabled → boolean */
export function normalizeWorkType(dto: WorkTypeDto): WorkType {
  return {
    id: String(dto.id),
    name: dto.name,
    billingMode: dto.billingMode as BillingMode,
    enabled: dto.enabled === true || dto.enabled === 1,
    sort: dto.sort ?? 0,
  } as WorkType
}

/** 工作类型列表（含停用） */
export async function listWorkTypes(): Promise<WorkType[]> {
  const page = await getPage<WorkTypeDto>('/worktype/list')
  return (page.rows || []).map(normalizeWorkType)
}

/** 仅启用的工作类型（员工端选类型用，返回数组） */
export async function listEnabledWorkTypes(): Promise<WorkType[]> {
  const list = await http.get<WorkTypeDto[]>('/worktype/enabled')
  return (list || []).map(normalizeWorkType)
}

/** 新增 */
export async function createWorkType(data: {
  name: string
  billingMode: BillingMode
}): Promise<void> {
  await http.post('/worktype', data)
}

/** 修改 */
export async function updateWorkType(data: {
  id: string
  name?: string
  billingMode?: BillingMode
  enabled?: boolean
}): Promise<void> {
  await http.put('/worktype', {
    id: Number(data.id) || data.id,
    name: data.name,
    billingMode: data.billingMode,
    enabled: data.enabled === undefined ? undefined : data.enabled ? 1 : 0,
  })
}

/** 删除（被引用时后端改为停用） */
export async function deleteWorkType(id: string): Promise<{ disabled: boolean }> {
  const result = await http.del<{ disabled: boolean } | null>(`/worktype/${id}`)
  return result ?? { disabled: false }
}
