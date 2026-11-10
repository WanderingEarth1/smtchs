/**
 * HTTP 请求封装（基于原生 fetch，无额外依赖）
 */

export const BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/smtchs'

/** 后端统一响应格式 */
export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 业务异常 */
export class ApiError extends Error {
  code: number
  constructor(message: string, code: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/** 取 token */
function getToken(): string | null {
  return localStorage.getItem('smtchs.token')
}

/** 401 时跳登录 */
function handleUnauthorized(): void {
  localStorage.removeItem('smtchs.token')
  if (!location.hash.startsWith('#/login')) {
    location.hash = '#/login'
  }
}

/** 核心请求方法 */
export async function request<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${url}`, { ...options, headers })

  if (response.status === 401) {
    handleUnauthorized()
    throw new ApiError('登录已过期，请重新登录', 401)
  }

  if (!response.ok) {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status)
  }

  const json = (await response.json()) as ApiResponse<T>

  if (json.code !== 200) {
    throw new ApiError(json.msg || '请求失败', json.code)
  }

  return json.data
}

/** 便捷方法 */
export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) => {
    const query = params
      ? '?' + new URLSearchParams(
          Object.entries(params)
            .filter(([, v]) => v !== undefined && v !== null && v !== '')
            .map(([k, v]) => [k, String(v)]),
        ).toString()
      : ''
    return request<T>(`${url}${query}`, { method: 'GET' })
  },
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
}
