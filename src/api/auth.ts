/**
 * 认证相关接口
 */

import { http } from './request'
import type { Employee } from '@/core/types'

export interface LoginResult {
  token: string
  user: Employee
}

export function login(account: string, password: string): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', { account, password })
}

export function logout(): Promise<void> {
  return http.post<void>('/auth/logout')
}

export function getInfo(): Promise<Employee> {
  return http.get<Employee>('/auth/info')
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return http.put<void>('/auth/password', { oldPassword, newPassword })
}
