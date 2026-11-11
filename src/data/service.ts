/**
 * 业务服务层 —— HTTP API 适配层
 *
 * 改造说明（V2.0）：
 * - 原实现基于 localStorage，现改为调用后端 REST API
 * - 纯逻辑函数（计费、校验）保持不变，仍在前端运行
 * - 名称查询类函数（getEmployeeName 等）走模块级缓存，保持同步签名
 * - 所有 API 错误统一包装为 BizError，便于 UI 层处理
 */
import type {
  BillingResult,
  Employee,
  PriceRule,
  RecordRevision,
  RecordStatus,
  SysConfig,
  WorkRecord,
  WorkType,
} from '@/core/types'
import { calculateBilling, accumulate, emptySummary, type MonthlySummary } from '@/core/billing'
import { currentMonthKey, todayKey } from '@/core/date'

import * as authApi from '@/api/auth'
import * as employeeApi from '@/api/employee'
import * as workTypeApi from '@/api/worktype'
import * as priceApi from '@/api/price'
import * as configApi from '@/api/config'
import * as recordApi from '@/api/record'
import * as reviewApi from '@/api/review'
import * as reportApi from '@/api/report'
import * as uploadApi from '@/api/upload'
import { ApiError } from '@/api/request'

/* ============================================================
   常量
   ============================================================ */

/** 登录态有效期：30 天（LOG-03） */
export const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000
/** 连续输错锁定阈值（LOG-05） */
export const MAX_FAILED_ATTEMPTS = 5
/** 锁定持续时间：15 分钟（LOG-05） */
export const LOCK_DURATION_MS = 15 * 60 * 1000
/** 照片数量上限 */
export const MAX_PHOTOS = 3
/** 照片压缩目标体积 */
export const PHOTO_MAX_BYTES = 300 * 1024

/* ============================================================
   异常
   ============================================================ */

/** 业务异常：携带可直接展示给用户的中文消息 */
export class BizError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'BizError'
  }
}

/** 把 API 错误统一包装为 BizError */
function wrapError(err: unknown): never {
  if (err instanceof BizError) throw err
  if (err instanceof ApiError) throw new BizError(err.message)
  if (err instanceof Error) throw new BizError(err.message)
  throw new BizError('操作失败，请稍后重试')
}

/* ============================================================
   模块级缓存（供同步查询函数使用）
   ============================================================ */

let employeeCache: Employee[] = []
let workTypeCache: WorkType[] = []
let priceCache: PriceRule[] = []

/** 刷新员工缓存 */
export async function refreshEmployeeCache(): Promise<void> {
  try {
    employeeCache = await employeeApi.listEmployees()
  } catch (err) {
    wrapError(err)
  }
}

/**
 * 刷新工作类型缓存
 *
 * 管理员走 /worktype/list（含停用）；员工无该权限时降级到 /worktype/enabled。
 */
export async function refreshWorkTypeCache(): Promise<void> {
  try {
    workTypeCache = await workTypeApi.listWorkTypes()
  } catch {
    workTypeCache = await workTypeApi.listEnabledWorkTypes()
  }
}

/**
 * 刷新单价缓存
 *
 * 管理员走 /price/matrix（全部）；员工走 /price/my（仅本人）。
 * 两者都失败时保持空缓存，页面会隐藏金额预估但不影响提交。
 */
export async function refreshPriceCache(): Promise<void> {
  try {
    priceCache = await priceApi.getPriceMatrix()
  } catch {
    try {
      priceCache = await priceApi.getMyPriceMatrix()
    } catch {
      priceCache = []
    }
  }
}

/**
 * 一次性刷新全部缓存（应用启动或登录后调用）
 *
 * 员工角色没有 employee:list / price:list 权限，接口会返回 403。
 * 这里逐项容错：某项失败只清空该项缓存，不影响登录与页面主流程。
 */
export async function refreshAllCaches(): Promise<void> {
  // 逐项容错：员工角色没有 employee:list / price:list 权限，
  // 任一接口 403 都不能让整个登录/恢复流程失败。
  await Promise.all([
    refreshWorkTypeCache().catch(() => {
      workTypeCache = []
    }),
    refreshEmployeeCache().catch(() => {
      employeeCache = []
    }),
    refreshPriceCache().catch(() => {
      priceCache = []
    }),
  ])
}

/* ============================================================
   配置（CFG-04 / CFG-05）
   ============================================================ */

export async function getConfig(): Promise<SysConfig> {
  try {
    return await configApi.getConfig()
  } catch (err) {
    wrapError(err)
  }
}

export async function updateConfig(patch: Partial<SysConfig>): Promise<SysConfig> {
  try {
    return await configApi.updateConfig(patch)
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   员工管理（CFG-01）
   ============================================================ */

export async function listEmployees(): Promise<Employee[]> {
  try {
    employeeCache = await employeeApi.listEmployees()
    return employeeCache
  } catch (err) {
    wrapError(err)
  }
}

export async function listActiveEmployees(): Promise<Employee[]> {
  const all = await listEmployees()
  return all.filter((e) => e.status === 'ACTIVE')
}

export async function listEngineers(): Promise<Employee[]> {
  const all = await listEmployees()
  return all.filter((e) => e.role === 'EMPLOYEE')
}

export async function getEmployee(id: string): Promise<Employee | undefined> {
  try {
    return await employeeApi.getEmployee(id)
  } catch {
    return undefined
  }
}

/** 同步查缓存（模板中大量使用） */
export function getEmployeeName(id: string): string {
  return employeeCache.find((e) => e.id === id)?.name ?? '未知员工'
}

export interface CreateEmployeeInput {
  name: string
  account: string
  password: string
}

export async function createEmployee(input: CreateEmployeeInput): Promise<Employee> {
  try {
    const emp = await employeeApi.createEmployee(input)
    await refreshEmployeeCache()
    return emp
  } catch (err) {
    wrapError(err)
  }
}

export async function updateEmployee(
  id: string,
  patch: Partial<Pick<Employee, 'name' | 'account' | 'status'>>,
): Promise<Employee> {
  try {
    await employeeApi.updateEmployee({
      id,
      name: patch.name,
      account: patch.account,
      status: patch.status === undefined ? undefined : patch.status === 'ACTIVE' ? '0' : '1',
    })
    await refreshEmployeeCache()
    const updated = employeeCache.find((e) => e.id === id)
    if (!updated) throw new BizError('员工不存在或已被删除')
    return updated
  } catch (err) {
    wrapError(err)
  }
}

export async function resetPassword(id: string, newPassword: string): Promise<void> {
  try {
    await employeeApi.resetPassword(id, newPassword)
  } catch (err) {
    wrapError(err)
  }
}

export async function changeOwnPassword(
  _id: string,
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  try {
    await authApi.changePassword(oldPassword, newPassword)
  } catch (err) {
    wrapError(err)
  }
}

export async function deleteEmployee(id: string): Promise<{ disabled: boolean }> {
  try {
    const result = await employeeApi.deleteEmployee(id)
    await refreshEmployeeCache()
    return result
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   工作类型管理（CFG-02）
   ============================================================ */

export async function listWorkTypes(includeDisabled = true): Promise<WorkType[]> {
  try {
    workTypeCache = await workTypeApi.listWorkTypes()
    return includeDisabled ? workTypeCache : workTypeCache.filter((w) => w.enabled)
  } catch (err) {
    wrapError(err)
  }
}

/** 同步查缓存 */
export function getWorkType(id: string): WorkType | undefined {
  return workTypeCache.find((w) => w.id === id)
}

/** 同步查缓存 */
export function getWorkTypeName(id: string): string {
  return workTypeCache.find((w) => w.id === id)?.name ?? '未知类型'
}

export async function createWorkType(input: {
  name: string
  billingMode: WorkType['billingMode']
}): Promise<WorkType> {
  try {
    await workTypeApi.createWorkType(input)
    await refreshWorkTypeCache()
    const created = workTypeCache.find((w) => w.name === input.name)
    if (!created) throw new BizError('工作类型创建失败')
    return created
  } catch (err) {
    wrapError(err)
  }
}

export async function updateWorkType(
  id: string,
  patch: Partial<Pick<WorkType, 'name' | 'billingMode' | 'enabled'>>,
): Promise<WorkType> {
  try {
    await workTypeApi.updateWorkType({ id, ...patch })
    await refreshWorkTypeCache()
    const updated = workTypeCache.find((w) => w.id === id)
    if (!updated) throw new BizError('工作类型不存在或已被删除')
    return updated
  } catch (err) {
    wrapError(err)
  }
}

export async function deleteWorkType(id: string): Promise<{ disabled: boolean }> {
  try {
    const result = await workTypeApi.deleteWorkType(id)
    await refreshWorkTypeCache()
    return result
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   单价矩阵（CFG-03）
   ============================================================ */

export async function listPriceRules(): Promise<PriceRule[]> {
  try {
    priceCache = await priceApi.getPriceMatrix()
    return priceCache
  } catch (err) {
    wrapError(err)
  }
}

/** 同步查缓存 */
export function getUnitPrice(employeeId: string, workTypeId: string): number {
  return (
    priceCache.find((p) => p.employeeId === employeeId && p.workTypeId === workTypeId)?.price ?? 0
  )
}

export async function setUnitPrice(
  employeeId: string,
  workTypeId: string,
  price: number,
): Promise<PriceRule> {
  try {
    await priceApi.setUnitPrice(employeeId, workTypeId, price)
    await refreshPriceCache()
    const rule = priceCache.find((p) => p.employeeId === employeeId && p.workTypeId === workTypeId)
    if (!rule) throw new BizError('单价保存失败')
    return rule
  } catch (err) {
    wrapError(err)
  }
}

export async function copyPriceRow(
  fromEmployeeId: string,
  toEmployeeIds: string[],
): Promise<number> {
  try {
    const count = await priceApi.copyPriceRow(fromEmployeeId, toEmployeeIds)
    await refreshPriceCache()
    return count
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   登录与会话（LOG-01 ~ LOG-06）
   ============================================================ */

export interface LoginResult {
  employee: Employee
  token: string
}

export async function login(account: string, password: string): Promise<LoginResult> {
  try {
    const result = await authApi.login(account, password)
    localStorage.setItem('smtchs.token', result.token)
    // 缓存刷新失败（如员工无管理权限）不应阻断登录
    await refreshAllCaches().catch(() => undefined)
    return { employee: result.user, token: result.token }
  } catch (err) {
    wrapError(err)
  }
}

export async function logout(): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    // 登出失败不阻断本地清理
  } finally {
    localStorage.removeItem('smtchs.token')
    employeeCache = []
    workTypeCache = []
    priceCache = []
  }
}

/**
 * 恢复会话（LOG-03 记住登录）
 * 有 token 则拉取用户信息，失败则清 token
 */
export async function restoreSession(): Promise<Employee | null> {
  const token = localStorage.getItem('smtchs.token')
  if (!token) return null

  try {
    const user = await authApi.getInfo()
    await refreshAllCaches().catch(() => undefined)
    return user
  } catch {
    localStorage.removeItem('smtchs.token')
    return null
  }
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

/** 校验记录草稿，返回字段级错误（纯逻辑，保持同步） */
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

/** base64 dataURL 转 File（用于提交前上传到后端） */
function dataUrlToFile(dataUrl: string, fileName: string): File {
  const commaIndex = dataUrl.indexOf(',')
  const meta = commaIndex >= 0 ? dataUrl.slice(0, commaIndex) : ''
  const base64 = commaIndex >= 0 ? dataUrl.slice(commaIndex + 1) : dataUrl
  const mime = /:(.*?);/.exec(meta)?.[1] ?? 'image/jpeg'
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], fileName, { type: mime })
}

/**
 * 把草稿里的本地照片上传到后端，返回可持久化的 URL 列表
 *
 * 已上传过的（dataUrl 以 / 或 http 开头的）直接复用，避免重复上传。
 */
async function uploadDraftPhotos(draft: RecordDraft): Promise<string[]> {
  const urls: string[] = []
  for (const photo of draft.photos) {
    const src = photo.dataUrl
    // 已经是服务端 URL，直接复用
    if (src.startsWith('/') || src.startsWith('http')) {
      urls.push(src)
      continue
    }
    if (!src.startsWith('data:')) {
      throw new BizError('照片数据异常，请重新拍摄')
    }
    const file = dataUrlToFile(src, photo.name || 'photo.jpg')
    try {
      const result = await uploadApi.uploadPhoto(file)
      urls.push(result.url)
    } catch (err) {
      wrapError(err)
    }
  }
  return urls
}

/** 把前端草稿转为 API 请求体 */
function toApiDraft(draft: RecordDraft, photoUrls: string[]): recordApi.RecordDraft {
  return {
    workTypeId: draft.workTypeId,
    serviceDate: draft.serviceDate,
    startTime: draft.startTime,
    endTime: draft.endTime,
    location: draft.location.trim(),
    latitude: draft.latitude,
    longitude: draft.longitude,
    geoCaptured: draft.geoCaptured,
    content: draft.content.trim(),
    remark: draft.remark.trim(),
    photoUrls,
  }
}

export async function createRecord(draft: RecordDraft): Promise<WorkRecord> {
  const errors = validateDraft(draft)
  const firstError = Object.values(errors)[0]
  if (firstError) throw new BizError(firstError)

  try {
    const photoUrls = await uploadDraftPhotos(draft)
    return await recordApi.createRecord(toApiDraft(draft, photoUrls))
  } catch (err) {
    wrapError(err)
  }
}

export async function updateRecord(recordId: string, draft: RecordDraft): Promise<WorkRecord> {
  const errors = validateDraft(draft)
  const firstError = Object.values(errors)[0]
  if (firstError) throw new BizError(firstError)

  try {
    const photoUrls = await uploadDraftPhotos(draft)
    return await recordApi.updateRecord(recordId, toApiDraft(draft, photoUrls))
  } catch (err) {
    wrapError(err)
  }
}

export async function deleteRecord(recordId: string): Promise<void> {
  try {
    await recordApi.deleteRecord(recordId)
  } catch (err) {
    wrapError(err)
  }
}

export async function getRecord(id: string): Promise<WorkRecord | undefined> {
  try {
    return await recordApi.getRecord(id)
  } catch {
    return undefined
  }
}

export async function listMyRecords(
  monthKey: string,
  status?: RecordStatus | 'ALL',
): Promise<WorkRecord[]> {
  try {
    return await recordApi.listMyRecords(monthKey, status === 'ALL' ? undefined : status)
  } catch (err) {
    wrapError(err)
  }
}

export async function listAllRecords(filter?: {
  monthKey?: string
  status?: RecordStatus | 'ALL'
  employeeId?: string
  workTypeId?: string
  keyword?: string
}): Promise<WorkRecord[]> {
  try {
    return await recordApi.listAllRecords({
      month: filter?.monthKey,
      status: filter?.status === 'ALL' ? undefined : filter?.status,
      employeeId: filter?.employeeId,
      workTypeId: filter?.workTypeId,
      keyword: filter?.keyword,
    })
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   审核（AUD-01 ~ AUD-05）
   ============================================================ */

export async function approveRecord(recordId: string): Promise<void> {
  try {
    await reviewApi.approveRecord(recordId)
  } catch (err) {
    wrapError(err)
  }
}

export async function approveRecords(
  recordIds: string[],
): Promise<{ success: number; failed: number }> {
  try {
    return await reviewApi.approveRecords(recordIds)
  } catch (err) {
    wrapError(err)
  }
}

export async function rejectRecord(recordId: string, reason: string): Promise<void> {
  const trimmed = reason.trim()
  if (!trimmed) throw new BizError('驳回时必须填写驳回原因')
  if (trimmed.length > 200) throw new BizError('驳回原因不能超过 200 个字符')

  try {
    await reviewApi.rejectRecord(recordId, trimmed)
  } catch (err) {
    wrapError(err)
  }
}

export interface RevisionInput {
  startTime?: string
  endTime?: string | null
  serviceDate?: string
  location?: string
  content?: string
  note: string
}

export async function reviseRecord(
  recordId: string,
  input: RevisionInput,
): Promise<WorkRecord> {
  if (!input.note.trim()) throw new BizError('请填写修正说明')

  try {
    await reviewApi.reviseRecord(recordId, {
      serviceDate: input.serviceDate,
      startTime: input.startTime,
      endTime: input.endTime ?? undefined,
      location: input.location,
      content: input.content,
      note: input.note.trim(),
    })
    const updated = await recordApi.getRecord(recordId)
    if (!updated) throw new BizError('记录不存在')
    return updated
  } catch (err) {
    wrapError(err)
  }
}

export async function listRevisions(recordId: string): Promise<RecordRevision[]> {
  try {
    return await reviewApi.listRevisions(recordId)
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   核算（STA-01 ~ STA-03）
   ============================================================ */

/** 计算单条记录的核算结果（纯逻辑，保持同步） */
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

/** 安全计算：异常时返回 0 金额结果，避免整页崩溃（纯逻辑，保持同步） */
export function computeRecordSafe(record: WorkRecord): BillingResult {
  try {
    return computeRecord(record)
  } catch {
    const mode = record?.billingSnapshot?.billingMode ?? 'BY_HOUR'
    return {
      billingMode: mode,
      count: mode === 'BY_COUNT' ? 1 : 0,
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

export type { MonthlyReport } from '@/api/report'

/** 生成月度报表（STA-01 / STA-02 / STA-03） */
export async function buildMonthlyReport(filter?: {
  monthKey?: string
  employeeId?: string
  workTypeId?: string
}): Promise<reportApi.MonthlyReport> {
  try {
    return await reportApi.getMonthlyReport({
      month: filter?.monthKey ?? '',
      employeeId: filter?.employeeId,
      workTypeId: filter?.workTypeId,
    })
  } catch (err) {
    wrapError(err)
  }
}

/** 员工当月汇总（REC-05） */
export async function buildMyMonthlySummary(monthKey: string): Promise<{
  summary: MonthlySummary
  pendingCount: number
  rejectedCount: number
  recordCount: number
}> {
  try {
    const report = await reportApi.getMySummary(monthKey)
    return {
      summary: report.summary,
      pendingCount: report.pendingCount,
      rejectedCount: report.rejectedCount,
      recordCount: report.approvedRecords.length,
    }
  } catch (err) {
    wrapError(err)
  }
}

/** 待审核数量（用于角标） */
export async function countPending(): Promise<number> {
  try {
    const report = await reportApi.getMonthlyReport({ month: currentMonthKey() })
    return report.pendingCount
  } catch {
    return 0
  }
}

/* ============================================================
   导出（STA-04）
   ============================================================ */

export async function exportMonthlyReport(monthKey: string): Promise<void> {
  try {
    await reportApi.exportMonthlyReport(monthKey)
  } catch (err) {
    wrapError(err)
  }
}

/* ============================================================
   兼容导出：保留 accumulate / emptySummary 供页面使用
   ============================================================ */

export { accumulate, emptySummary }
