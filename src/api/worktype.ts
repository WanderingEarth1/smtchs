/**
 * 工作类型接口
 * 后端：SmtWorkTypeController（/smtchs/worktype）
 */
import { http, getPage } from './request'
import type { BillingMode, WorkType } from '@/core/types'

/** 工作类型列表（含停用） */
export async function listWorkTypes(): Promise<WorkType[]> {
  const page = await getPage<WorkType>('/worktype/list')
  return page.rows || []
}

/** 仅启用的工作类型（员工端选类型用，返回数组） */
export async function listEnabledWorkTypes(): Promise<WorkType[]> {
  const list = await http.get<WorkType[]>('/worktype/enabled')
  return list || []
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
    ...data,
    enabled: data.enabled === undefined ? undefined : data.enabled ? 1 : 0,
  })
}

/** 删除（被引用时后端改为停用） */
export async function deleteWorkType(id: string): Promise<{ disabled: boolean }> {
  const result = await http.del<{ disabled: boolean } | null>(`/worktype/${id}`)
  return result ?? { disabled: false }
}
