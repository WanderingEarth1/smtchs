/**
 * 员工管理接口
 * 后端：SmtEmployeeController（/smtchs/employee）
 */
import { http, getPage, type PageResult } from './request'
import type { Employee } from '@/core/types'

/** 后端实体（字段与 smt_employee 一致，status 为 '0'/'1'，role 由后端推导） */
export interface EmployeeDto extends Omit<Employee, 'status' | 'role'> {
  status: string
  role?: string
}

/** 状态映射：后端 '0'/'1' ↔ 前端 'ACTIVE'/'DISABLED' */
export function normalizeEmployee(dto: EmployeeDto): Employee {
  return {
    ...dto,
    status: dto.status === '0' ? 'ACTIVE' : 'DISABLED',
    role: (dto.role as Employee['role']) || 'EMPLOYEE',
  }
}

/** 员工列表（分页） */
export async function listEmployees(params?: {
  pageNum?: number
  pageSize?: number
  name?: string
  account?: string
  status?: string
}): Promise<Employee[]> {
  const page = await getPage<EmployeeDto>('/employee/list', params)
  return (page.rows || []).map(normalizeEmployee)
}

/** 员工详情 */
export async function getEmployee(id: string): Promise<Employee | undefined> {
  const dto = await http.get<EmployeeDto | null>(`/employee/${id}`)
  return dto ? normalizeEmployee(dto) : undefined
}

/** 新增员工 */
export async function createEmployee(data: {
  name: string
  account: string
  password: string
}): Promise<Employee> {
  await http.post('/employee', data)
  return { id: '', name: data.name, account: data.account } as Employee
}

/** 修改员工 */
export async function updateEmployee(data: {
  id: string
  name?: string
  account?: string
  status?: string
}): Promise<void> {
  await http.put('/employee', data)
}

/** 删除员工（有历史记录时后端改为停用） */
export async function deleteEmployee(id: string): Promise<{ disabled: boolean }> {
  const result = await http.del<{ disabled: boolean } | null>(`/employee/${id}`)
  return result ?? { disabled: false }
}

/** 重置密码 */
export async function resetPassword(id: string, password: string): Promise<void> {
  await http.put(`/employee/${id}/password`, { password })
}
