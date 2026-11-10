/**
 * 业务服务层 —— 集中实现需求文档第 5 章全部功能规则
 *
 * 所有写操作都经过此层，保证规则单点实现、可测试。
 */
import type {
  BillingResult,
  Employee,
  LoginLog,
  PriceRule,
  RecordRevision,
  RecordStatus,
  SysConfig,
  WorkRecord,
  WorkType,
} from '@/core/types'
import { calculateBilling, accumulate, emptySummary, type MonthlySummary } from '@/core/billing'
import { hashPassword, verifyPassword } from '@/core/hash'
import { currentMonthKey, monthOf, todayKey } from '@/core/date'
import {
  loadEmployees,
  loadLoginLogs,
  loadPriceRules,
  loadRecords,
  loadRevisions,
  loadSession,
  loadSysConfig,
  loadWorkTypes,
  saveEmployees,
  saveLoginLogs,
  savePriceRules,
  saveRecords,
  saveRevisions,
  saveSession,
  saveSysConfig,
  saveWorkTypes,
  type Session,
} from './storage'

/* ============================================================
   常量
   ============================================================ */

/** 登录态有效期：30 天（LOG-03） */
export const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000
/** 连续输错锁定阈值（LOG-05） */
export const MAX_FAILED_ATTEMPTS = 5
/** 锁定持续时间：15 分钟（LOG-05） */
export const LOCK_DURATION_MS = 15 * 60 * 1000
/** 照片数量上限（REC 字段规则） */
export const MAX_PHOTOS = 3
/** 照片压缩目标体积 */
export const PHOTO_MAX_BYTES = 300 * 1024

/* ============================================================
   工具
   ============================================================ */

function uid(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

/** 业务异常：携带可直接展示给用户的中文消息 */
export class BizError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'BizError'
  }
}

/* ============================================================
   配置读取
   ============================================================ */

const DEFAULT_CONFIG: SysConfig = {
  overtimeStart: '18:00',
  overtimeMultiplier: 2,
  roundingRule: 'EXACT',
}

export function getConfig(): SysConfig {
  return loadSysConfig() ?? { ...DEFAULT_CONFIG }
}

export function updateConfig(patch: Partial<SysConfig>): SysConfig {
  const current = getConfig()
  const next: SysConfig = { ...current, ...patch }

  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(next.overtimeStart)) {
    throw new BizError('加班起算时间格式应为 HH:mm')
  }
  if (!Number.isFinite(next.overtimeMultiplier) || next.overtimeMultiplier < 1) {
    throw new BizError('加班倍率必须不小于 1')
  }
  if (next.overtimeMultiplier > 10) {
    throw new BizError('加班倍率不能超过 10')
  }
  if (!['EXACT', 'HALF_HOUR_UP', 'HOUR_UP'].includes(next.roundingRule)) {
    throw new BizError('取整规则取值非法')
  }

  saveSysConfig(next)
  return next
}

/* ============================================================
   员工管理（CFG-01）
   ============================================================ */

export function listEmployees(): Employee[] {
  return loadEmployees()
}

export function listActiveEmployees(): Employee[] {
  return loadEmployees().filter((e) => e.status === 'ACTIVE')
}

export function listEngineers(): Employee[] {
  return loadEmployees().filter((e) => e.role === 'EMPLOYEE')
}

export function getEmployee(id: string): Employee | undefined {
  return loadEmployees().find((e) => e.id === id)
}

export function getEmployeeName(id: string): string {
  return getEmployee(id)?.name ?? '未知员工'
}

export interface CreateEmployeeInput {
  name: string
  account: string
  password: string
}

export async function createEmployee(input: CreateEmployeeInput): Promise<Employee> {
  const name = input.name.trim()
  const account = input.account.trim()

  if (!name) throw new BizError('请填写员工姓名')
  if (name.length > 20) throw new BizError('姓名不能超过 20 个字符')
  if (!account) throw new BizError('请填写登录账号')
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(account)) {
    throw new BizError('登录账号需为 3~20 位字母、数字或下划线')
  }
  if (!input.password || input.password.length < 6) {
    throw new BizError('初始密码至少 6 位')
  }

  const employees = loadEmployees()
  if (employees.some((e) => e.account.toLowerCase() === account.toLowerCase())) {
    throw new BizError(`登录账号「${account}」已存在`)
  }

  const employee: Employee = {
    id: uid('emp'),
    name,
    account,
    passwordHash: await hashPassword(input.password),
    status: 'ACTIVE',
    role: 'EMPLOYEE',
    createdAt: new Date().toISOString(),
    failedAttempts: 0,
    lockedUntil: null,
  }
  employees.push(employee)
  saveEmployees(employees)
  return employee
}

export function updateEmployee(
  id: string,
  patch: Partial<Pick<Employee, 'name' | 'account' | 'status'>>,
): Employee {
  const employees = loadEmployees()
  const idx = employees.findIndex((e) => e.id === id)
  if (idx < 0) throw new BizError('员工不存在')

  const target = employees[idx]
  if (target.role === 'ADMIN' && patch.status === 'DISABLED') {
    throw new BizError('不能停用管理员账号')
  }

  if (patch.name !== undefined) {
    const name = patch.name.trim()
    if (!name) throw new BizError('姓名不能为空')
    if (name.length > 20) throw new BizError('姓名不能超过 20 个字符')
    target.name = name
  }

  if (patch.account !== undefined) {
    const account = patch.account.trim()
    if (!/^[a-zA-Z0-9_]{3,20}$/.test(account)) {
      throw new BizError('登录账号需为 3~20 位字母、数字或下划线')
    }
    const dup = employees.some(
      (e) => e.id !== id && e.account.toLowerCase() === account.toLowerCase(),
    )
    if (dup) throw new BizError(`登录账号「${account}」已存在`)
    target.account = account
  }

  if (patch.status !== undefined) {
    target.status = patch.status
    if (patch.status === 'ACTIVE') {
      target.failedAttempts = 0
      target.lockedUntil = null
    }
  }

  employees[idx] = target
  saveEmployees(employees)
  return target
}

export async function resetPassword(id: string, newPassword: string): Promise<void> {
  if (!newPassword || newPassword.length < 6) {
    throw new BizError('新密码至少 6 位')
  }
  const employees = loadEmployees()
  const target = employees.find((e) => e.id === id)
  if (!target) throw new BizError('员工不存在')
  target.passwordHash = await hashPassword(newPassword)
  target.failedAttempts = 0
  target.lockedUntil = null
  saveEmployees(employees)
}

export async function changeOwnPassword(
  id: string,
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  const employees = loadEmployees()
  const target = employees.find((e) => e.id === id)
  if (!target) throw new BizError('账号不存在')

  const ok = await verifyPassword(oldPassword, target.passwordHash)
  if (!ok) throw new BizError('原密码不正确')
  if (!newPassword || newPassword.length < 6) throw new BizError('新密码至少 6 位')
  if (newPassword === oldPassword) throw new BizError('新密码不能与原密码相同')

  target.passwordHash = await hashPassword(newPassword)
  saveEmployees(employees)
}

/**
 * 删除员工（CFG-01）
 * 若该员工已有服务记录，则改为停用，避免历史统计断链（LOG-06）
 */
export function deleteEmployee(id: string): { disabled: boolean } {
  const employees = loadEmployees()
  const target = employees.find((e) => e.id === id)
  if (!target) throw new BizError('员工不存在')
  if (target.role === 'ADMIN') throw new BizError('不能删除管理员账号')

  const hasRecords = loadRecords().some((r) => r.employeeId === id)
  if (hasRecords) {
    target.status = 'DISABLED'
    saveEmployees(employees)
    return { disabled: true }
  }

  saveEmployees(employees.filter((e) => e.id !== id))
  savePriceRules(loadPriceRules().filter((p) => p.employeeId !== id))
  return { disabled: false }
}

/* ============================================================
   工作类型管理（CFG-02）
   ============================================================ */

export function listWorkTypes(includeDisabled = true): WorkType[] {
  const all = loadWorkTypes().sort((a, b) => a.sort - b.sort)
  return includeDisabled ? all : all.filter((w) => w.enabled)
}

export function getWorkType(id: string): WorkType | undefined {
  return loadWorkTypes().find((w) => w.id === id)
}

export function getWorkTypeName(id: string): string {
  return getWorkType(id)?.name ?? '未知类型'
}

export function createWorkType(input: {
  name: string
  billingMode: WorkType['billingMode']
}): WorkType {
  const name = input.name.trim()
  if (!name) throw new BizError('请填写类型名称')
  if (name.length > 20) throw new BizError('类型名称不能超过 20 个字符')

  const list = loadWorkTypes()
  if (list.some((w) => w.name === name)) throw new BizError(`类型「${name}」已存在`)

  const wt: WorkType = {
    id: uid('wt'),
    name,
    billingMode: input.billingMode,
    enabled: true,
    sort: list.length + 1,
  }
  list.push(wt)
  saveWorkTypes(list)
  return wt
}

export function updateWorkType(
  id: string,
  patch: Partial<Pick<WorkType, 'name' | 'billingMode' | 'enabled'>>,
): WorkType {
  const list = loadWorkTypes()
  const idx = list.findIndex((w) => w.id === id)
  if (idx < 0) throw new BizError('工作类型不存在')

  const target = list[idx]

  if (patch.name !== undefined) {
    const name = patch.name.trim()
    if (!name) throw new BizError('类型名称不能为空')
    if (list.some((w) => w.id !== id && w.name === name)) {
      throw new BizError(`类型「${name}」已存在`)
    }
    target.name = name
  }

  if (patch.billingMode !== undefined && patch.billingMode !== target.billingMode) {
    // 计费模式变更会影响历史记录口径，但历史记录已固化快照，故允许变更
    target.billingMode = patch.billingMode
  }

  if (patch.enabled !== undefined) {
    target.enabled = patch.enabled
  }

  list[idx] = target
  saveWorkTypes(list)
  return target
}

export function deleteWorkType(id: string): { disabled: boolean } {
  const list = loadWorkTypes()
  const target = list.find((w) => w.id === id)
  if (!target) throw new BizError('工作类型不存在')

  const used = loadRecords().some((r) => r.workTypeId === id)
  if (used) {
    target.enabled = false
    saveWorkTypes(list)
    return { disabled: true }
  }

  saveWorkTypes(list.filter((w) => w.id !== id))
  savePriceRules(loadPriceRules().filter((p) => p.workTypeId !== id))
  return { disabled: false }
}

/* ============================================================
   单价矩阵（CFG-03）
   ============================================================ */

export function listPriceRules(): PriceRule[] {
  return loadPriceRules()
}

/** 取某员工某类型的单价 */
export function getUnitPrice(employeeId: string, workTypeId: string): number {
  const rule = loadPriceRules().find(
    (p) => p.employeeId === employeeId && p.workTypeId === workTypeId,
  )
  return rule?.price ?? 0
}

/** 设置单价（存在则更新，不存在则新增） */
export function setUnitPrice(employeeId: string, workTypeId: string, price: number): PriceRule {
  if (!Number.isFinite(price) || price < 0) throw new BizError('单价必须为非负数')
  if (price > 100000) throw new BizError('单价不能超过 100000')

  const rules = loadPriceRules()
  const existing = rules.find(
    (p) => p.employeeId === employeeId && p.workTypeId === workTypeId,
  )
  if (existing) {
    existing.price = Math.round(price * 100) / 100
    existing.effectiveAt = new Date().toISOString()
    savePriceRules(rules)
    return existing
  }

  const rule: PriceRule = {
    id: uid('pr'),
    employeeId,
    workTypeId,
    price: Math.round(price * 100) / 100,
    effectiveAt: new Date().toISOString(),
  }
  rules.push(rule)
  savePriceRules(rules)
  return rule
}

/** 按行快速复制：把某员工的全部单价复制给其他员工（CFG-03） */
export function copyPriceRow(fromEmployeeId: string, toEmployeeIds: string[]): number {
  const rules = loadPriceRules()
  const source = rules.filter((p) => p.employeeId === fromEmployeeId)
  if (source.length === 0) throw new BizError('该员工尚未配置任何单价')

  let count = 0
  for (const toId of toEmployeeIds) {
    if (toId === fromEmployeeId) continue
    for (const src of source) {
      const existing = rules.find(
        (p) => p.employeeId === toId && p.workTypeId === src.workTypeId,
      )
      if (existing) {
        existing.price = src.price
        existing.effectiveAt = new Date().toISOString()
      } else {
        rules.push({
          id: uid('pr'),
          employeeId: toId,
          workTypeId: src.workTypeId,
          price: src.price,
          effectiveAt: new Date().toISOString(),
        })
      }
      count++
    }
  }
  savePriceRules(rules)
  return count
}

/* ============================================================
   登录与会话（LOG-01 ~ LOG-06）
   ============================================================ */

export interface LoginResult {
  employee: Employee
  session: Session
}

function logLogin(account: string, success: boolean, message: string): void {
  const logs = loadLoginLogs()
  const log: LoginLog = {
    id: uid('log'),
    account,
    at: new Date().toISOString(),
    ip: '127.0.0.1',
    success,
    message,
  }
  logs.unshift(log)
  // 仅保留最近 200 条
  saveLoginLogs(logs.slice(0, 200))
}

export function listLoginLogs(): LoginLog[] {
  return loadLoginLogs()
}

export async function login(account: string, password: string): Promise<LoginResult> {
  const acc = account.trim()
  if (!acc) throw new BizError('请输入登录账号')
  if (!password) throw new BizError('请输入密码')

  const employees = loadEmployees()
  const idx = employees.findIndex((e) => e.account.toLowerCase() === acc.toLowerCase())

  if (idx < 0) {
    logLogin(acc, false, '账号不存在')
    throw new BizError('账号或密码错误')
  }

  const emp = employees[idx]

  // LOG-06：停用员工无法登录
  if (emp.status === 'DISABLED') {
    logLogin(acc, false, '账号已停用')
    throw new BizError('该账号已停用，请联系管理员')
  }

  // LOG-05：锁定检查
  if (emp.lockedUntil) {
    const until = new Date(emp.lockedUntil).getTime()
    if (Date.now() < until) {
      const mins = Math.ceil((until - Date.now()) / 60000)
      logLogin(acc, false, '账号锁定中')
      throw new BizError(`密码连续输错 5 次，账号已锁定，请 ${mins} 分钟后再试`)
    }
    // 锁定已过期，重置
    emp.lockedUntil = null
    emp.failedAttempts = 0
  }

  const ok = await verifyPassword(password, emp.passwordHash)
  if (!ok) {
    emp.failedAttempts += 1
    if (emp.failedAttempts >= MAX_FAILED_ATTEMPTS) {
      emp.lockedUntil = new Date(Date.now() + LOCK_DURATION_MS).toISOString()
      emp.failedAttempts = 0
      employees[idx] = emp
      saveEmployees(employees)
      logLogin(acc, false, '密码错误达 5 次，账号锁定 15 分钟')
      throw new BizError('密码连续输错 5 次，账号已锁定 15 分钟')
    }
    employees[idx] = emp
    saveEmployees(employees)
    const left = MAX_FAILED_ATTEMPTS - emp.failedAttempts
    logLogin(acc, false, `密码错误，剩余 ${left} 次机会`)
    throw new BizError(`账号或密码错误，还可尝试 ${left} 次`)
  }

  // 登录成功：重置失败计数
  emp.failedAttempts = 0
  emp.lockedUntil = null
  employees[idx] = emp
  saveEmployees(employees)

  const now = Date.now()
  const session: Session = {
    employeeId: emp.id,
    expiresAt: now + SESSION_TTL_MS,
    lastActiveAt: now,
  }
  saveSession(session)
  logLogin(acc, true, '登录成功')

  return { employee: emp, session }
}

export function logout(): void {
  saveSession(null)
}

/**
 * 恢复会话（LOG-03 记住登录 / 静默续期）
 * 返回当前登录员工，无有效会话返回 null
 */
export function restoreSession(): Employee | null {
  const session = loadSession()
  if (!session) return null

  const now = Date.now()
  if (now > session.expiresAt) {
    saveSession(null)
    return null
  }

  const emp = getEmployee(session.employeeId)
  if (!emp || emp.status === 'DISABLED') {
    saveSession(null)
    return null
  }

  // 静默续期：每次活跃刷新过期时间
  saveSession({
    employeeId: session.employeeId,
    expiresAt: now + SESSION_TTL_MS,
    lastActiveAt: now,
  })

  return emp
}

/* ============================================================
   服务记录（REC-01 ~ REC-05）
   ============================================================ */

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
  photos: WorkRecord['photos']
}

/** 校验记录草稿，返回字段级错误 */
export function validateDraft(draft: RecordDraft): Record<string, string> {
  const errors: Record<string, string> = {}

  if (!draft.workTypeId) {
    errors.workTypeId = '请选择工作类型'
  } else {
    const wt = getWorkType(draft.workTypeId)
    if (!wt) errors.workTypeId = '工作类型不存在'
    else if (!wt.enabled) errors.workTypeId = '该工作类型已停用'
  }

  if (!draft.serviceDate) {
    errors.serviceDate = '请选择服务日期'
  } else if (draft.serviceDate > todayKey()) {
    errors.serviceDate = '不能填写未来日期'
  }

  if (!draft.startTime) {
    errors.startTime = '请填写开始时间'
  }

  const wt = draft.workTypeId ? getWorkType(draft.workTypeId) : undefined
  if (wt?.billingMode === 'BY_HOUR') {
    if (!draft.endTime) {
      errors.endTime = '按小时计费需填写结束时间'
    } else if (draft.startTime && draft.endTime === draft.startTime) {
      errors.endTime = '结束时间不能与开始时间相同'
    }
  }

  if (!draft.location.trim()) {
    errors.location = '请填写服务地点'
  } else if (draft.location.trim().length > 60) {
    errors.location = '地点不能超过 60 个字符'
  }

  if (!draft.content.trim()) {
    errors.content = '请填写工作内容'
  } else if (draft.content.trim().length > 200) {
    errors.content = '工作内容不能超过 200 个字符'
  }

  if (draft.photos.length < 1) {
    errors.photos = '请至少拍摄 1 张现场凭证'
  } else if (draft.photos.length > MAX_PHOTOS) {
    errors.photos = `最多上传 ${MAX_PHOTOS} 张照片`
  }

  if (draft.remark.length > 200) {
    errors.remark = '备注不能超过 200 个字符'
  }

  return errors
}

/** 生成流水号 */
function nextSerialNo(records: WorkRecord[]): string {
  const monthKey = currentMonthKey().replace('-', '')
  const prefix = `SR${monthKey}`
  const maxSeq = records
    .filter((r) => r.serialNo.startsWith(prefix))
    .reduce((max, r) => {
      const seq = Number(r.serialNo.slice(prefix.length))
      return Number.isFinite(seq) ? Math.max(max, seq) : max
    }, 0)
  return `${prefix}${String(maxSeq + 1).padStart(4, '0')}`
}

/** 新增记录（REC-01 / REC-02） */
export function createRecord(employeeId: string, draft: RecordDraft): WorkRecord {
  const errors = validateDraft(draft)
  const firstError = Object.values(errors)[0]
  if (firstError) throw new BizError(firstError)

  const wt = getWorkType(draft.workTypeId)!
  const unitPrice = getUnitPrice(employeeId, draft.workTypeId)
  if (unitPrice <= 0) {
    throw new BizError('该工作类型尚未配置单价，请联系管理员')
  }

  const cfg = getConfig()
  const records = loadRecords()
  const now = new Date().toISOString()

  const record: WorkRecord = {
    id: uid('rec'),
    serialNo: nextSerialNo(records),
    employeeId,
    workTypeId: draft.workTypeId,
    serviceDate: draft.serviceDate,
    startTime: draft.startTime,
    endTime: wt.billingMode === 'BY_HOUR' ? draft.endTime : null,
    location: draft.location.trim(),
    latitude: draft.latitude,
    longitude: draft.longitude,
    geoCaptured: draft.geoCaptured,
    content: draft.content.trim(),
    remark: draft.remark.trim(),
    photos: draft.photos.slice(0, MAX_PHOTOS),
    status: 'PENDING',
    // REC-02：非当日提交自动标记补录
    isBackfill: draft.serviceDate !== todayKey(),
    rejectReason: null,
    submittedAt: now,
    confirmedAt: null,
    rejectedAt: null,
    billingSnapshot: {
      billingMode: wt.billingMode,
      unitPrice,
      overtimeStart: cfg.overtimeStart,
      overtimeMultiplier: cfg.overtimeMultiplier,
      roundingRule: cfg.roundingRule,
    },
  }

  records.unshift(record)
  saveRecords(records)
  return record
}

/** 修改记录（REC-03：仅待确认 / 已驳回的本人记录可改） */
export function updateRecord(
  recordId: string,
  employeeId: string,
  draft: RecordDraft,
): WorkRecord {
  const records = loadRecords()
  const idx = records.findIndex((r) => r.id === recordId)
  if (idx < 0) throw new BizError('记录不存在')

  const record = records[idx]
  if (record.employeeId !== employeeId) throw new BizError('只能修改自己的记录')
  if (record.status === 'APPROVED') throw new BizError('已确认的记录已锁定，无法修改')

  const errors = validateDraft(draft)
  const firstError = Object.values(errors)[0]
  if (firstError) throw new BizError(firstError)

  const wt = getWorkType(draft.workTypeId)!
  const unitPrice = getUnitPrice(employeeId, draft.workTypeId)
  if (unitPrice <= 0) throw new BizError('该工作类型尚未配置单价，请联系管理员')

  const cfg = getConfig()

  // 修改后重新提交，重走审核（AUD-05）
  record.workTypeId = draft.workTypeId
  record.serviceDate = draft.serviceDate
  record.startTime = draft.startTime
  record.endTime = wt.billingMode === 'BY_HOUR' ? draft.endTime : null
  record.location = draft.location.trim()
  record.latitude = draft.latitude
  record.longitude = draft.longitude
  record.geoCaptured = draft.geoCaptured
  record.content = draft.content.trim()
  record.remark = draft.remark.trim()
  record.photos = draft.photos.slice(0, MAX_PHOTOS)
  record.status = 'PENDING'
  record.isBackfill = draft.serviceDate !== todayKey()
  record.rejectReason = null
  record.rejectedAt = null
  record.submittedAt = new Date().toISOString()
  record.billingSnapshot = {
    billingMode: wt.billingMode,
    unitPrice,
    overtimeStart: cfg.overtimeStart,
    overtimeMultiplier: cfg.overtimeMultiplier,
    roundingRule: cfg.roundingRule,
  }

  records[idx] = record
  saveRecords(records)
  return record
}

/** 删除记录（REC-03） */
export function deleteRecord(recordId: string, employeeId: string): void {
  const records = loadRecords()
  const record = records.find((r) => r.id === recordId)
  if (!record) throw new BizError('记录不存在')
  if (record.employeeId !== employeeId) throw new BizError('只能删除自己的记录')
  if (record.status === 'APPROVED') throw new BizError('已确认的记录已锁定，无法删除')

  saveRecords(records.filter((r) => r.id !== recordId))
}

export function getRecord(id: string): WorkRecord | undefined {
  return loadRecords().find((r) => r.id === id)
}

/** 我的记录列表（REC-04）：按月 + 状态筛选 */
export function listMyRecords(
  employeeId: string,
  monthKey: string,
  status?: RecordStatus | 'ALL',
): WorkRecord[] {
  return loadRecords()
    .filter((r) => r.employeeId === employeeId && monthOf(r.serviceDate) === monthKey)
    .filter((r) => !status || status === 'ALL' || r.status === status)
    .sort((a, b) => {
      // 按服务日期倒序，同日按开始时间倒序
      if (a.serviceDate !== b.serviceDate) return a.serviceDate < b.serviceDate ? 1 : -1
      return a.startTime < b.startTime ? 1 : -1
    })
}

/** 全部记录（管理端） */
export function listAllRecords(filter?: {
  monthKey?: string
  status?: RecordStatus | 'ALL'
  employeeId?: string
  workTypeId?: string
  keyword?: string
}): WorkRecord[] {
  let list = loadRecords()
  if (filter?.monthKey) list = list.filter((r) => monthOf(r.serviceDate) === filter.monthKey!)
  if (filter?.status && filter.status !== 'ALL') {
    list = list.filter((r) => r.status === filter.status)
  }
  if (filter?.employeeId) list = list.filter((r) => r.employeeId === filter.employeeId)
  if (filter?.workTypeId) list = list.filter((r) => r.workTypeId === filter.workTypeId)
  if (filter?.keyword) {
    const kw = filter.keyword.trim().toLowerCase()
    if (kw) {
      list = list.filter(
        (r) =>
          r.location.toLowerCase().includes(kw) ||
          r.content.toLowerCase().includes(kw) ||
          r.serialNo.toLowerCase().includes(kw) ||
          getEmployeeName(r.employeeId).toLowerCase().includes(kw),
      )
    }
  }
  return list.sort((a, b) => {
    if (a.serviceDate !== b.serviceDate) return a.serviceDate < b.serviceDate ? 1 : -1
    return a.startTime < b.startTime ? 1 : -1
  })
}

/* ============================================================
   审核（AUD-01 ~ AUD-05）
   ============================================================ */

/** 确认单条（AUD-02） */
export function approveRecord(recordId: string): WorkRecord {
  const records = loadRecords()
  const record = records.find((r) => r.id === recordId)
  if (!record) throw new BizError('记录不存在')
  if (record.status === 'APPROVED') throw new BizError('该记录已确认')
  if (record.status === 'REJECTED') throw new BizError('已驳回的记录需员工修改后重新提交')

  record.status = 'APPROVED'
  record.confirmedAt = new Date().toISOString()
  record.rejectReason = null
  saveRecords(records)
  return record
}

/** 批量确认（AUD-03） */
export function approveRecords(recordIds: string[]): { success: number; failed: number } {
  const records = loadRecords()
  const now = new Date().toISOString()
  let success = 0
  let failed = 0

  for (const id of recordIds) {
    const record = records.find((r) => r.id === id)
    if (!record || record.status !== 'PENDING') {
      failed++
      continue
    }
    record.status = 'APPROVED'
    record.confirmedAt = now
    record.rejectReason = null
    success++
  }

  saveRecords(records)
  return { success, failed }
}

/** 驳回（AUD-02：驳回原因必填） */
export function rejectRecord(recordId: string, reason: string): WorkRecord {
  const trimmed = reason.trim()
  if (!trimmed) throw new BizError('驳回时必须填写驳回原因')
  if (trimmed.length > 200) throw new BizError('驳回原因不能超过 200 个字符')

  const records = loadRecords()
  const record = records.find((r) => r.id === recordId)
  if (!record) throw new BizError('记录不存在')
  if (record.status === 'APPROVED') throw new BizError('已确认的记录不能驳回')

  record.status = 'REJECTED'
  record.rejectReason = trimmed
  record.rejectedAt = new Date().toISOString()
  saveRecords(records)
  return record
}

/** 修正已确认记录（AUD-04：留痕） */
export interface RevisionInput {
  startTime?: string
  endTime?: string | null
  serviceDate?: string
  location?: string
  content?: string
  note: string
}

export function reviseRecord(
  recordId: string,
  operatorName: string,
  input: RevisionInput,
): WorkRecord {
  const records = loadRecords()
  const idx = records.findIndex((r) => r.id === recordId)
  if (idx < 0) throw new BizError('记录不存在')

  const record = records[idx]
  if (record.status !== 'APPROVED') {
    throw new BizError('仅已确认的记录需要走修正流程')
  }
  if (!input.note.trim()) throw new BizError('请填写修正说明')

  const before: Record<string, unknown> = {
    serviceDate: record.serviceDate,
    startTime: record.startTime,
    endTime: record.endTime,
    location: record.location,
    content: record.content,
  }

  const next = { ...record }
  if (input.serviceDate !== undefined) next.serviceDate = input.serviceDate
  if (input.startTime !== undefined) next.startTime = input.startTime
  if (input.endTime !== undefined) next.endTime = input.endTime
  if (input.location !== undefined) next.location = input.location.trim()
  if (input.content !== undefined) next.content = input.content.trim()

  // 校验修正后的时间合法性
  if (next.endTime) {
    if (next.startTime === next.endTime) throw new BizError('结束时间不能与开始时间相同')
  }

  const after: Record<string, unknown> = {
    serviceDate: next.serviceDate,
    startTime: next.startTime,
    endTime: next.endTime,
    location: next.location,
    content: next.content,
  }

  const changed = Object.keys(before).some((k) => before[k] !== after[k])
  if (!changed) throw new BizError('未检测到任何修改')

  const revision: RecordRevision = {
    id: uid('rev'),
    recordId,
    operatorName,
    revisedAt: new Date().toISOString(),
    before,
    after,
    note: input.note.trim(),
  }

  const revisions = loadRevisions()
  revisions.unshift(revision)
  saveRevisions(revisions)

  records[idx] = next
  saveRecords(records)
  return next
}

export function listRevisions(recordId: string): RecordRevision[] {
  return loadRevisions().filter((r) => r.recordId === recordId)
}

/* ============================================================
   核算（STA-01 ~ STA-03）
   ============================================================ */

/** 计算单条记录的核算结果 */
export function computeRecord(record: WorkRecord): BillingResult {
  return calculateBilling({
    billingMode: record.billingSnapshot.billingMode,
    unitPrice: record.billingSnapshot.unitPrice,
    startTime: record.startTime,
    endTime: record.endTime,
    overtimeStart: record.billingSnapshot.overtimeStart,
    overtimeMultiplier: record.billingSnapshot.overtimeMultiplier,
    roundingRule: record.billingSnapshot.roundingRule,
  })
}

/** 安全计算：异常时返回 0 金额结果，避免整页崩溃 */
export function computeRecordSafe(record: WorkRecord): BillingResult {
  try {
    return computeRecord(record)
  } catch {
    return {
      billingMode: record.billingSnapshot.billingMode,
      count: record.billingSnapshot.billingMode === 'BY_COUNT' ? 1 : 0,
      normalHours: 0,
      overtimeHours: 0,
      normalAmount: 0,
      overtimeAmount: 0,
      total: 0,
      rawNormalHours: 0,
      rawOvertimeHours: 0,
      crossMidnight: false,
      explanation: '数据异常，无法核算',
    }
  }
}

export interface MonthlyReport {
  monthKey: string
  /** 汇总视图（STA-02） */
  summary: MonthlySummary
  /** 按员工汇总 */
  byEmployee: Array<{
    employeeId: string
    employeeName: string
    summary: MonthlySummary
  }>
  /** 按工作类型汇总 */
  byWorkType: Array<{
    workTypeId: string
    workTypeName: string
    billingMode: WorkType['billingMode']
    summary: MonthlySummary
  }>
  /** 已确认记录明细 */
  approvedRecords: WorkRecord[]
  /** 未计入核算的条数（STA-03） */
  pendingCount: number
  rejectedCount: number
}

/**
 * 生成月度报表（STA-01 / STA-02 / STA-03）
 * 仅「已确认」记录计入核算
 */
export function buildMonthlyReport(filter?: {
  monthKey?: string
  employeeId?: string
  workTypeId?: string
}): MonthlyReport {
  const monthKey = filter?.monthKey ?? currentMonthKey()

  const monthRecords = loadRecords().filter((r) => monthOf(r.serviceDate) === monthKey)

  const scoped = monthRecords.filter((r) => {
    if (filter?.employeeId && r.employeeId !== filter.employeeId) return false
    if (filter?.workTypeId && r.workTypeId !== filter.workTypeId) return false
    return true
  })

  const approved = scoped.filter((r) => r.status === 'APPROVED')
  const pendingCount = scoped.filter((r) => r.status === 'PENDING').length
  const rejectedCount = scoped.filter((r) => r.status === 'REJECTED').length

  const summary = emptySummary()
  const empMap = new Map<string, MonthlySummary>()
  const typeMap = new Map<string, MonthlySummary>()

  for (const record of approved) {
    const result = computeRecordSafe(record)
    accumulate(summary, result)

    if (!empMap.has(record.employeeId)) empMap.set(record.employeeId, emptySummary())
    accumulate(empMap.get(record.employeeId)!, result)

    if (!typeMap.has(record.workTypeId)) typeMap.set(record.workTypeId, emptySummary())
    accumulate(typeMap.get(record.workTypeId)!, result)
  }

  const byEmployee = [...empMap.entries()]
    .map(([employeeId, s]) => ({
      employeeId,
      employeeName: getEmployeeName(employeeId),
      summary: s,
    }))
    .sort((a, b) => b.summary.totalAmount - a.summary.totalAmount)

  const byWorkType = [...typeMap.entries()]
    .map(([workTypeId, s]) => {
      const wt = getWorkType(workTypeId)
      return {
        workTypeId,
        workTypeName: wt?.name ?? '未知类型',
        billingMode: wt?.billingMode ?? ('BY_COUNT' as const),
        summary: s,
      }
    })
    .sort((a, b) => b.summary.totalAmount - a.summary.totalAmount)

  return {
    monthKey,
    summary,
    byEmployee,
    byWorkType,
    approvedRecords: approved.sort((a, b) => {
      if (a.serviceDate !== b.serviceDate) return a.serviceDate < b.serviceDate ? 1 : -1
      return a.startTime < b.startTime ? 1 : -1
    }),
    pendingCount,
    rejectedCount,
  }
}

/** 员工当月汇总（REC-05） */
export function buildMyMonthlySummary(
  employeeId: string,
  monthKey: string,
): {
  summary: MonthlySummary
  pendingCount: number
  rejectedCount: number
  recordCount: number
} {
  const records = listMyRecords(employeeId, monthKey)
  const approved = records.filter((r) => r.status === 'APPROVED')

  const summary = emptySummary()
  for (const record of approved) {
    accumulate(summary, computeRecordSafe(record))
  }

  return {
    summary,
    pendingCount: records.filter((r) => r.status === 'PENDING').length,
    rejectedCount: records.filter((r) => r.status === 'REJECTED').length,
    recordCount: records.length,
  }
}

/** 待审核数量（用于角标） */
export function countPending(): number {
  return loadRecords().filter((r) => r.status === 'PENDING').length
}
