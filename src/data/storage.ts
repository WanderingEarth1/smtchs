/**
 * 本地持久化存储层
 *
 * 原型以 localStorage 模拟后端数据库；照片以压缩后的 dataURL 存储。
 * 生产环境应替换为 REST API + 对象存储，接口签名保持一致即可平滑迁移。
 */
import type {
  Employee,
  WorkType,
  PriceRule,
  SysConfig,
  WorkRecord,
  RecordRevision,
  LoginLog,
} from '@/core/types'

const NS = 'smtchs.v1'

export const STORAGE_KEYS = {
  employees: `${NS}.employees`,
  workTypes: `${NS}.workTypes`,
  priceRules: `${NS}.priceRules`,
  sysConfig: `${NS}.sysConfig`,
  records: `${NS}.records`,
  revisions: `${NS}.revisions`,
  loginLogs: `${NS}.loginLogs`,
  session: `${NS}.session`,
  seeded: `${NS}.seeded`,
  theme: `${NS}.theme`,
} as const

/** 安全读取 JSON */
function readJSON<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

/** 安全写入 JSON */
function writeJSON(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (err) {
    // 配额超限等异常：抛出以便上层提示用户
    console.error('[storage] 写入失败', key, err)
    throw new Error('本地存储写入失败，可能是存储空间不足')
  }
}

/* ---------------- 员工 ---------------- */
export function loadEmployees(): Employee[] {
  return readJSON<Employee[]>(STORAGE_KEYS.employees, [])
}
export function saveEmployees(list: Employee[]): void {
  writeJSON(STORAGE_KEYS.employees, list)
}

/* ---------------- 工作类型 ---------------- */
export function loadWorkTypes(): WorkType[] {
  return readJSON<WorkType[]>(STORAGE_KEYS.workTypes, [])
}
export function saveWorkTypes(list: WorkType[]): void {
  writeJSON(STORAGE_KEYS.workTypes, list)
}

/* ---------------- 单价 ---------------- */
export function loadPriceRules(): PriceRule[] {
  return readJSON<PriceRule[]>(STORAGE_KEYS.priceRules, [])
}
export function savePriceRules(list: PriceRule[]): void {
  writeJSON(STORAGE_KEYS.priceRules, list)
}

/* ---------------- 系统配置 ---------------- */
export function loadSysConfig(): SysConfig | null {
  return readJSON<SysConfig | null>(STORAGE_KEYS.sysConfig, null)
}
export function saveSysConfig(cfg: SysConfig): void {
  writeJSON(STORAGE_KEYS.sysConfig, cfg)
}

/* ---------------- 服务记录 ---------------- */
export function loadRecords(): WorkRecord[] {
  return readJSON<WorkRecord[]>(STORAGE_KEYS.records, [])
}
export function saveRecords(list: WorkRecord[]): void {
  writeJSON(STORAGE_KEYS.records, list)
}

/* ---------------- 修正痕迹 ---------------- */
export function loadRevisions(): RecordRevision[] {
  return readJSON<RecordRevision[]>(STORAGE_KEYS.revisions, [])
}
export function saveRevisions(list: RecordRevision[]): void {
  writeJSON(STORAGE_KEYS.revisions, list)
}

/* ---------------- 登录日志 ---------------- */
export function loadLoginLogs(): LoginLog[] {
  return readJSON<LoginLog[]>(STORAGE_KEYS.loginLogs, [])
}
export function saveLoginLogs(list: LoginLog[]): void {
  writeJSON(STORAGE_KEYS.loginLogs, list)
}

/* ---------------- 会话 ---------------- */
export interface Session {
  employeeId: string
  /** 过期时间戳（毫秒），默认 30 天 */
  expiresAt: number
  /** 最近活跃时间戳，用于静默续期 */
  lastActiveAt: number
}

export function loadSession(): Session | null {
  return readJSON<Session | null>(STORAGE_KEYS.session, null)
}
export function saveSession(session: Session | null): void {
  if (session === null) {
    localStorage.removeItem(STORAGE_KEYS.session)
    return
  }
  writeJSON(STORAGE_KEYS.session, session)
}

/* ---------------- 主题 ---------------- */
export function loadTheme(): 'light' | 'dark' | null {
  const v = localStorage.getItem(STORAGE_KEYS.theme)
  return v === 'light' || v === 'dark' ? v : null
}
export function saveTheme(theme: 'light' | 'dark'): void {
  localStorage.setItem(STORAGE_KEYS.theme, theme)
}

/* ---------------- 初始化标记 ---------------- */
export function isSeeded(): boolean {
  return localStorage.getItem(STORAGE_KEYS.seeded) === '1'
}
export function markSeeded(): void {
  localStorage.setItem(STORAGE_KEYS.seeded, '1')
}

/** 清空全部业务数据（用于「重置演示数据」） */
export function clearAll(): void {
  Object.values(STORAGE_KEYS).forEach((k) => localStorage.removeItem(k))
}
