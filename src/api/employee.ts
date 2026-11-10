/**
 * 员工管理接口
 */

import { http } from './request'
import type { Employee } from '@/core/types'

export function listEmployees(): Promise<Employee[]> {
  return http.get<Employee[]>('/employees')
}

export function getEmployee(id: string): Promise<Employee> {
  return http.get<Employee>(`/employees/${id}`)
}

export function createEmployee(data: {
  name: string
  account: string
  password: string
}): Promise<Employee> {
  return http.post<Employee>('/employees', data)
}

export function updateEmployee(data: Partial<Employee> & { id: string }): Promise<Employee> {
  const { id, ...rest } = data
  return http.put<Employee>(`/employees/${id}`, rest)
}

export function deleteEmployee(id: string): Promise<{ disabled: boolean }> {
  return http.del<{ disabled: boolean }>(`/employees/${id}`)
}

export function resetPassword(id: string, password: string): Promise<void> {
  return http.put<void>(`/employees/${id}/password`, { password })
}
