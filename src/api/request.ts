/**
 * HTTP 请求封装（基于原生 fetch，无额外依赖）
 *
 * 约定：
 * - 后端统一响应 { code, msg, data } 或分页 { code, msg, rows, total }
 * - request() 优先返回 data，分页响应返回 { rows, total }
 * - 401 清 token 并跳登录
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/smtchs'

export { BASE_URL }

/** 后端统一响应格式 */
export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
  rows?: T
  total?: number
}

/** 分页响应 */
export interface PageResult<T> {
  rows: T[]
  total: number
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

function getToken(): string | null {
  return localStorage.getItem('smtchs.token')
}

function handleUnauthorized(): void {
  localStorage.removeItem('smtchs.token')
  if (!location.hash.startsWith('#/login')) {
    location.hash = '#/login'
  }
}

/** 判断是否为分页响应 */
function isPageResponse(json: ApiResponse): boolean {
  return Array.isArray(json.rows)
}

/**
 * 核心请求方法
 *
 * @returns data 字段；若为分页响应则返回 { rows, total }
 */
export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
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

  if (response.status === 403) {
    throw new ApiError('没有操作权限', 403)
  }

  if (!response.ok) {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status)
  }

  const json = (await response.json()) as ApiResponse

  if (json.code !== 200) {
    throw new ApiError(json.msg || '请求失败', json.code)
  }

  // 分页响应：返回 { rows, total }
  if (isPageResponse(json)) {
    return { rows: json.rows, total: json.total ?? 0 } as unknown as T
  }

  return json.data as T
}

/** 便捷方法 */
export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) => {
    const query = params
      ? '?' +
        new URLSearchParams(
          Object.entries(params)
            .filter(([, v]) => v !== undefined && v !== null && v !== '')
            .map(([k, v]) => [k, String(v)]),
        ).toString()
      : ''
    return request<T>(`${url}${query}`, { method: 'GET' })
  },
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: body !== undefined ? JSON.stringify(body) : undefined }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
}

/** 分页查询辅助 */
export async function getPage<T>(
  url: string,
  params?: Record<string, unknown>,
): Promise<PageResult<T>> {
  return http.get<PageResult<T>>(url, { pageNum: 1, pageSize: 999, ...params })
}
