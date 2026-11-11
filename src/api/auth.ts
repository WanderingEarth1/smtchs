/**
 * 认证相关接口
 * 后端：SmtAuthController（/smtchs/auth）
 *
 * 注意：后端 user.id 是 Long（JSON 数字），前端领域模型用 string，
 * 这里统一转换——否则 currentUser.id 与单价缓存里的 employeeId 比较会失败。
 */
import { http } from './request'
import type { Employee } from '@/core/types'

/** 后端返回的用户视图 */
interface UserDto {
  id: string | number
  name: string
  account: string
  role?: string
}

/** 后端登录响应 */
interface LoginResponseDto {
  token: string
  user: UserDto
}

/** 归一化用户对象 */
function normalizeUser(dto: UserDto): Employee {
  return {
    id: String(dto.id),
    name: dto.name,
    account: dto.account,
    role: (dto.role as Employee['role']) || 'EMPLOYEE',
    status: 'ACTIVE',
    failedAttempts: 0,
    lockedUntil: null,
    createdAt: '',
  } as Employee
}

export interface LoginResult {
  token: string
  user: Employee
}

export async function login(account: string, password: string): Promise<LoginResult> {
  const dto = await http.post<LoginResponseDto>('/auth/login', { account, password })
  return { token: dto.token, user: normalizeUser(dto.user) }
}

export async function logout(): Promise<void> {
  await http.post<void>('/auth/logout')
}

export async function getInfo(): Promise<Employee> {
  const dto = await http.get<UserDto>('/auth/info')
  return normalizeUser(dto)
}

export async function changePassword(
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  await http.put<void>('/auth/password', { oldPassword, newPassword })
}

export { normalizeUser }
