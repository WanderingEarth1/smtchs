/**
 * 计费引擎 —— 严格对应需求文档第 6 章「计费规则详细说明」
 *
 * 6.1 按次计费：金额 = 单价（元/次） × 次数（一条记录 = 1 次），不参与加班与取整
 * 6.2 按小时计费（含加班）：
 *     1) 分段：以加班起算时间为界切分
 *        T₁ = min(结束, 起算) − 开始（开始已晚于起算点则 T₁ = 0）
 *        T₂ = 结束 − max(开始, 起算)（结束早于起算点则 T₂ = 0；跨 0 点连续服务归入加班段）
 *     2) 取整：T₁、T₂ 分别按当前取整规则向上补足（EXACT 不取整）
 *     3) 计费：金额 = T₁ × 单价 + T₂ × 单价 × 加班倍率
 */
import type { BillingMode, BillingResult, RoundingRule } from './types'

/** 计费入参 */
export interface BillingInput {
  billingMode: BillingMode
  /** 单价：元/次 或 元/小时 */
  unitPrice: number
  /** 开始时间 HH:mm */
  startTime: string
  /** 结束时间 HH:mm，按次类型可省略 */
  endTime?: string | null
  /** 加班起算时间 HH:mm */
  overtimeStart: string
  /** 加班倍率 */
  overtimeMultiplier: number
  /** 取整规则 */
  roundingRule: RoundingRule
}

/** 把 HH:mm 解析为「当日分钟数」 */
export function parseTimeToMinutes(time: string): number {
  const m = /^(\d{1,2}):(\d{2})$/.exec(time.trim())
  if (!m) throw new Error(`时间格式非法：${time}（应为 HH:mm）`)
  const h = Number(m[1])
  const min = Number(m[2])
  if (h < 0 || h > 23 || min < 0 || min > 59) throw new Error(`时间超出范围：${time}`)
  return h * 60 + min
}

/** 分钟数格式化为 HH:mm */
export function minutesToTime(totalMinutes: number): string {
  const normalized = ((totalMinutes % 1440) + 1440) % 1440
  const h = Math.floor(normalized / 60)
  const m = normalized % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** 分钟 → 小时（保留精确值，取整只用于展示，避免汇总时累积误差） */
function toHours(minutes: number): number {
  return minutes / 60
}

/** 清除浮点噪声：保留 6 位小数（约 3.6 毫秒精度） */
function tidy(value: number): number {
  return Math.round(value * 1e6) / 1e6
}

/** 分钟 → 可读时长（1h40m / 30min），用于计算说明文本 */
export function minutesText(minutes: number): string {
  if (minutes <= 0) return '0h'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}min`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

/** 金额四舍五入到分 */
export function roundMoney(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100
}

/**
 * 按取整规则向上补足时长（单位：分钟）
 * - EXACT：精确到分钟，不取整
 * - HALF_HOUR_UP：半小时向上取整
 * - HOUR_UP：整小时向上取整
 */
export function applyRounding(minutes: number, rule: RoundingRule): number {
  if (minutes <= 0) return 0
  switch (rule) {
    case 'EXACT':
      return minutes
    case 'HALF_HOUR_UP':
      return Math.ceil(minutes / 30) * 30
    case 'HOUR_UP':
      return Math.ceil(minutes / 60) * 60
    default:
      return minutes
  }
}

/** 取整规则的中文名 */
export const ROUNDING_LABELS: Record<RoundingRule, string> = {
  EXACT: '精确到分钟',
  HALF_HOUR_UP: '半小时向上取整',
  HOUR_UP: '整小时向上取整',
}

/**
 * 计算单条记录的提成金额
 */
export function calculateBilling(input: BillingInput): BillingResult {
  const {
    billingMode,
    unitPrice,
    startTime,
    endTime,
    overtimeStart,
    overtimeMultiplier,
    roundingRule,
  } = input

  if (!Number.isFinite(unitPrice) || unitPrice < 0) {
    throw new Error('单价必须为非负数')
  }

  // ---------- 6.1 按次计费 ----------
  if (billingMode === 'BY_COUNT') {
    const total = roundMoney(unitPrice * 1)
    return {
      billingMode,
      count: 1,
      normalHours: 0,
      overtimeHours: 0,
      normalAmount: total,
      overtimeAmount: 0,
      total,
      rawNormalHours: 0,
      rawOvertimeHours: 0,
      crossMidnight: false,
      explanation: `按次计费：1 次 × ${unitPrice} 元/次 = ${total} 元（按次不参与加班与取整）`,
    }
  }

  // ---------- 6.2 按小时计费 ----------
  if (!endTime) {
    throw new Error('按小时计费必须填写结束时间')
  }

  const startMin = parseTimeToMinutes(startTime)
  const endMinRaw = parseTimeToMinutes(endTime)
  const otStartMin = parseTimeToMinutes(overtimeStart)

  // 跨 0 点：结束时间 <= 开始时间视为次日结束（连续服务）
  const crossMidnight = endMinRaw <= startMin
  const endMin = crossMidnight ? endMinRaw + 1440 : endMinRaw

  if (endMin <= startMin) {
    throw new Error('结束时间必须晚于开始时间')
  }

  // 1) 分段（严格按文档 6.2 步骤 1）
  //    T₁ = min(结束, 起算) − 开始；若开始已晚于起算点，T₁ = 0
  //    T₂ = 结束 − max(开始, 起算)；若结束早于起算点，T₂ = 0
  //    跨 0 点的连续服务时长归入加班时段连续计算
  const durationMin = endMin - startMin
  const startsInOvertime = startMin >= otStartMin
  const rawNormalMin = startsInOvertime ? 0 : Math.min(durationMin, otStartMin - startMin)
  const rawOvertimeMin = Math.max(0, durationMin - rawNormalMin)
  // 加班段在「当日分钟轴」上的起点（用于展示）
  const overtimeStartMin = startMin + rawNormalMin

  // 2) 取整（T₁、T₂ 分别向上补足）
  const normalMin = applyRounding(rawNormalMin, roundingRule)
  const overtimeMin = applyRounding(rawOvertimeMin, roundingRule)

  // 3) 计费（直接用分钟数换算，避免小时数四舍五入引入的分位误差）
  const normalHours = toHours(normalMin)
  const overtimeHours = toHours(overtimeMin)
  const normalAmount = roundMoney((normalMin / 60) * unitPrice)
  const overtimeAmount = roundMoney((overtimeMin / 60) * unitPrice * overtimeMultiplier)
  const total = roundMoney(normalAmount + overtimeAmount)

  const rawNormalHours = toHours(rawNormalMin)
  const rawOvertimeHours = toHours(rawOvertimeMin)

  const parts: string[] = []
  parts.push(
    `正常段 ${startTime}~${minutesToTime(startMin + rawNormalMin)}：原始 ${minutesText(rawNormalMin)}` +
      (roundingRule === 'EXACT' ? '' : ` → 取整 ${normalHours}h`) +
      ` × ${unitPrice} = ${normalAmount} 元`,
  )
  if (rawOvertimeMin > 0) {
    parts.push(
      `加班段 ${minutesToTime(overtimeStartMin)}~${minutesToTime(endMin)}：原始 ${minutesText(rawOvertimeMin)}` +
        (roundingRule === 'EXACT' ? '' : ` → 取整 ${overtimeHours}h`) +
        ` × ${unitPrice} × ${overtimeMultiplier} = ${overtimeAmount} 元`,
    )
  } else {
    parts.push('无加班时段')
  }
  parts.push(`合计 ${total} 元`)

  return {
    billingMode,
    count: 0,
    normalHours,
    overtimeHours,
    normalAmount,
    overtimeAmount,
    total,
    rawNormalHours,
    rawOvertimeHours,
    crossMidnight,
    explanation: parts.join('；'),
  }
}

/** 月度汇总结果 */
export interface MonthlySummary {
  /** 记录条数 */
  recordCount: number
  /** 按次次数合计 */
  countTotal: number
  /** 正常时长合计（小时） */
  normalHoursTotal: number
  /** 加班时长合计（小时） */
  overtimeHoursTotal: number
  /** 按次金额合计 */
  byCountAmount: number
  /** 按时金额合计 */
  byHourAmount: number
  /** 提成合计 */
  totalAmount: number
}

/** 空汇总 */
export function emptySummary(): MonthlySummary {
  return {
    recordCount: 0,
    countTotal: 0,
    normalHoursTotal: 0,
    overtimeHoursTotal: 0,
    byCountAmount: 0,
    byHourAmount: 0,
    totalAmount: 0,
  }
}

/** 把一条核算结果累加进汇总 */
export function accumulate(summary: MonthlySummary, result: BillingResult): MonthlySummary {
  summary.recordCount += 1
  summary.countTotal += result.count
  summary.normalHoursTotal = tidy(summary.normalHoursTotal + result.normalHours)
  summary.overtimeHoursTotal = tidy(summary.overtimeHoursTotal + result.overtimeHours)
  if (result.billingMode === 'BY_COUNT') {
    summary.byCountAmount = roundMoney(summary.byCountAmount + result.total)
  } else {
    summary.byHourAmount = roundMoney(summary.byHourAmount + result.total)
  }
  summary.totalAmount = roundMoney(summary.byCountAmount + summary.byHourAmount)
  return summary
}
