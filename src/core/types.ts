/**
 * 领域模型类型定义
 * 对应需求文档第 7 章「数据模型设计」
 */

/** 角色 */
export type Role = 'EMPLOYEE' | 'ADMIN'

/** 员工状态 */
export type EmployeeStatus = 'ACTIVE' | 'DISABLED'

/** 计费模式 */
export type BillingMode = 'BY_COUNT' | 'BY_HOUR'

/** 记录审核状态 */
export type RecordStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** 时长取整规则 */
export type RoundingRule = 'EXACT' | 'HALF_HOUR_UP' | 'HOUR_UP'

/** 员工表 employee */
export interface Employee {
  id: string
  /** 姓名 */
  name: string
  /** 登录账号 */
  account: string
  /** 密码（仅后端持有，前端不接收） */
  passwordHash: string
  /** 状态：启用 / 停用 */
  status: EmployeeStatus
  /** 角色 */
  role: Role
  /** 创建时间 ISO */
  createdAt: string
  /** 连续登录失败次数（LOG-05） */
  failedAttempts: number
  /** 锁定截止时间 ISO，null 表示未锁定 */
  lockedUntil: string | null
}

/** 工作类型表 work_type */
export interface WorkType {
  id: string
  /** 类型名称 */
  name: string
  /** 计费模式 */
  billingMode: BillingMode
  /** 状态 */
  enabled: boolean
  /** 排序 */
  sort: number
}

/** 单价表 price_rule —— 工程师 × 工作类型 */
export interface PriceRule {
  id: string
  employeeId: string
  workTypeId: string
  /** 单价：元/次 或 元/小时 */
  price: number
  /** 生效时间 ISO */
  effectiveAt: string
}

/** 系统配置表 sys_config */
export interface SysConfig {
  /** 加班起算时间 HH:mm，默认 18:00 */
  overtimeStart: string
  /** 加班倍率，默认 2 */
  overtimeMultiplier: number
  /** 时长取整规则 */
  roundingRule: RoundingRule
}

/** 照片凭证 */
export interface PhotoRef {
  id: string
  /** 缩略图 dataURL（前端压缩后） */
  dataUrl: string
  /** 原始文件名 */
  name: string
  /** 压缩后字节数 */
  size: number
}

/** 服务记录表 work_record */
export interface WorkRecord {
  id: string
  /** 流水号（展示用） */
  serialNo: string
  employeeId: string
  workTypeId: string
  /** 服务日期 YYYY-MM-DD */
  serviceDate: string
  /** 开始时间 HH:mm（按次类型即上门时间） */
  startTime: string
  /** 结束时间 HH:mm（按次类型为 null） */
  endTime: string | null
  /** 地点文本 */
  location: string
  /** 经纬度（可空） */
  latitude: number | null
  longitude: number | null
  /** 定位是否获取成功 */
  geoCaptured: boolean
  /** 工作内容 */
  content: string
  /** 备注 */
  remark: string
  /** 照片列表（1~3 张） */
  photos: PhotoRef[]
  /** 状态 */
  status: RecordStatus
  /** 是否补录 */
  isBackfill: boolean
  /** 驳回原因 */
  rejectReason: string | null
  /** 提交时间 ISO */
  submittedAt: string
  /** 确认时间 ISO */
  confirmedAt: string | null
  /** 驳回时间 ISO */
  rejectedAt: string | null
  /** 提交时快照的计费参数（保证历史记录口径不随配置漂移） */
  billingSnapshot: BillingSnapshot
}

/** 提交时固化的计费参数快照 */
export interface BillingSnapshot {
  billingMode: BillingMode
  /** 单价（元/次 或 元/小时） */
  unitPrice: number
  overtimeStart: string
  overtimeMultiplier: number
  roundingRule: RoundingRule
}

/** 修正痕迹表 record_revision */
export interface RecordRevision {
  id: string
  recordId: string
  /** 修正人姓名 */
  operatorName: string
  /** 修正时间 ISO */
  revisedAt: string
  /** 修正前快照 JSON */
  before: Record<string, unknown>
  /** 修正后快照 JSON */
  after: Record<string, unknown>
  /** 修正说明 */
  note: string
}

/** 登录日志 login_log */
export interface LoginLog {
  id: string
  account: string
  at: string
  ip: string
  success: boolean
  message: string
}

/** 核算结果 */
export interface BillingResult {
  /** 计费模式 */
  billingMode: BillingMode
  /** 次数（按次计费为 1，按小时为 0） */
  count: number
  /** 正常时长（小时，已取整） */
  normalHours: number
  /** 加班时长（小时，已取整） */
  overtimeHours: number
  /** 正常时段金额 */
  normalAmount: number
  /** 加班时段金额 */
  overtimeAmount: number
  /** 合计金额 */
  total: number
  /** 取整前的原始时长，用于展示 */
  rawNormalHours: number
  rawOvertimeHours: number
  /** 是否跨 0 点 */
  crossMidnight: boolean
  /** 计算说明（可读文本，便于核对） */
  explanation: string
}
