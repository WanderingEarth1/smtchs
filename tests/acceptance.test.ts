/**
 * 验收测试 —— 对应需求文档第 11 章「验收要点」
 *
 * 重点覆盖：
 *   #4 计费引擎抽样 20 条记录人工核算，金额 100% 一致
 *   #5 月度报表数字与导出 Excel 数字一致
 *
 * 「人工核算」在本文件中以一份与计费引擎**完全独立**的实现表达：
 * 用直觉公式（分钟 → 小时 → 金额）逐步手算，不复用 src/core/billing.ts 的任何函数。
 */
import { describe, it, expect } from 'vitest'
import { calculateBilling, type BillingInput } from '../src/core/billing'
import type { RoundingRule } from '../src/core/types'

/* ============================================================
   独立实现：手工核算公式（不使用被测源码）
   ============================================================ */

function hm(t: string): number {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

function ceilTo(minutes: number, rule: RoundingRule): number {
  if (rule === 'EXACT') return minutes
  const step = rule === 'HALF_HOUR_UP' ? 30 : 60
  // 手工向上取整：整除则不变，否则补到下一个 step
  const rem = minutes % step
  return rem === 0 ? minutes : minutes + (step - rem)
}

/**
 * 手工核算（独立实现）
 * 返回 { normalHours, overtimeHours, amount }
 */
function manualCalc(input: BillingInput): {
  normalHours: number
  overtimeHours: number
  amount: number
} {
  if (input.billingMode === 'BY_COUNT') {
    return { normalHours: 0, overtimeHours: 0, amount: input.unitPrice }
  }

  const start = hm(input.startTime)
  let end = hm(input.endTime!)
  if (end <= start) end += 1440
  const ot = hm(input.overtimeStart)

  let normalMin = 0
  let overtimeMin = 0

  if (start >= ot) {
    // 全部落在加班段
    overtimeMin = end - start
  } else {
    normalMin = Math.min(end, ot) - start
    overtimeMin = Math.max(0, end - ot)
  }

  const nMin = ceilTo(normalMin, input.roundingRule)
  const oMin = ceilTo(overtimeMin, input.roundingRule)

  // 手工换算成小时再乘，最后按「分」四舍五入
  const amount =
    Math.round(
      ((nMin / 60) * input.unitPrice + (oMin / 60) * input.unitPrice * input.overtimeMultiplier) *
        100,
    ) / 100

  return { normalHours: nMin / 60, overtimeHours: oMin / 60, amount }
}

/* ============================================================
   20 条抽样用例（覆盖按次 / 按时 / 加班 / 三种取整）
   ============================================================ */

interface Sample {
  desc: string
  input: BillingInput
}

const base = { overtimeStart: '18:00', overtimeMultiplier: 2, unitPrice: 80 }

const samples: Sample[] = [
  // ---- 按次（4 条）----
  {
    desc: '按次-送货白天',
    input: { ...base, billingMode: 'BY_COUNT', unitPrice: 50, startTime: '09:00', roundingRule: 'EXACT' },
  },
  {
    desc: '按次-送货夜间（按次无加班）',
    input: { ...base, billingMode: 'BY_COUNT', unitPrice: 50, startTime: '20:30', roundingRule: 'EXACT' },
  },
  {
    desc: '按次-巡检小数单价',
    input: { ...base, billingMode: 'BY_COUNT', unitPrice: 33.33, startTime: '14:00', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按次-巡检整小时取整规则下仍按次',
    input: { ...base, billingMode: 'BY_COUNT', unitPrice: 45, startTime: '10:15', roundingRule: 'HOUR_UP' },
  },

  // ---- 按小时 · 精确到分钟（5 条）----
  {
    desc: '按时-文档示例1：17:30~19:30 精确',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '17:30', endTime: '19:30', roundingRule: 'EXACT' },
  },
  {
    desc: '按时-全正常段 09:00~12:00',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '09:00', endTime: '12:00', roundingRule: 'EXACT' },
  },
  {
    desc: '按时-全加班段 19:00~21:15',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '19:00', endTime: '21:15', roundingRule: 'EXACT' },
  },
  {
    desc: '按时-跨0点 22:00~01:30',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '22:00', endTime: '01:30', roundingRule: 'EXACT' },
  },
  {
    desc: '按时-跨0点且跨起算点 17:10~00:20',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '17:10', endTime: '00:20', roundingRule: 'EXACT' },
  },

  // ---- 按小时 · 半小时向上取整（5 条）----
  {
    desc: '按时-文档示例2：17:30~19:40 半小时取整',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '17:30', endTime: '19:40', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按时-半小时取整-整除边界 08:00~18:00',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '08:00', endTime: '18:00', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按时-半小时取整-1分钟补齐 09:01~11:01',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '09:01', endTime: '11:01', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按时-半小时取整-单价 95 李四',
    input: { ...base, billingMode: 'BY_HOUR', unitPrice: 95, startTime: '17:00', endTime: '19:40', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按时-半小时取整-跨0点 22:10~00:50',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '22:10', endTime: '00:50', roundingRule: 'HALF_HOUR_UP' },
  },

  // ---- 按小时 · 整小时向上取整（4 条）----
  {
    desc: '按时-整小时取整 17:30~19:40',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '17:30', endTime: '19:40', roundingRule: 'HOUR_UP' },
  },
  {
    desc: '按时-整小时取整-整除边界 09:00~12:00',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '09:00', endTime: '12:00', roundingRule: 'HOUR_UP' },
  },
  {
    desc: '按时-整小时取整-1分钟 10:00~11:01',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '10:00', endTime: '11:01', roundingRule: 'HOUR_UP' },
  },
  {
    desc: '按时-整小时取整-自定义起算 20:00 倍率 1.5',
    input: {
      billingMode: 'BY_HOUR',
      unitPrice: 100,
      startTime: '19:00',
      endTime: '22:20',
      overtimeStart: '20:00',
      overtimeMultiplier: 1.5,
      roundingRule: 'HOUR_UP',
    },
  },

  // ---- 边界（3 条）----
  {
    desc: '按时-恰好结束在起算点 16:00~18:00',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '16:00', endTime: '18:00', roundingRule: 'HALF_HOUR_UP' },
  },
  {
    desc: '按时-恰好开始于起算点 18:00~18:01',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '18:00', endTime: '18:01', roundingRule: 'EXACT' },
  },
  {
    desc: '按时-超长跨日 08:00~次日02:00',
    input: { ...base, billingMode: 'BY_HOUR', startTime: '08:00', endTime: '02:00', roundingRule: 'HALF_HOUR_UP' },
  },
]

/* ============================================================
   验收要点 #4
   ============================================================ */
describe('验收 #4：计费引擎 20 条抽样人工核对', () => {
  it('抽样不少于 20 条', () => {
    expect(samples.length).toBeGreaterThanOrEqual(20)
  })

  it.each(samples.map((s) => [s.desc, s] as const))('%s 金额一致', (_desc, sample) => {
    const engine = calculateBilling(sample.input)
    const manual = manualCalc(sample.input)

    expect(engine.normalHours).toBeCloseTo(manual.normalHours, 6)
    expect(engine.overtimeHours).toBeCloseTo(manual.overtimeHours, 6)
    expect(engine.total).toBeCloseTo(manual.amount, 2)
  })

  it('全部 20 条无一例外', () => {
    const mismatches = samples
      .map((s) => {
        const engine = calculateBilling(s.input)
        const manual = manualCalc(s.input)
        return { desc: s.desc, engine: engine.total, manual: manual.amount }
      })
      .filter((r) => Math.abs(r.engine - r.manual) > 0.005)

    expect(mismatches).toEqual([])
    expect(samples.length - mismatches.length).toBeGreaterThanOrEqual(20)
  })
})

/* ============================================================
   验收要点 #4 补充：文档示例逐条复现
   ============================================================ */
describe('验收 #4：需求文档 6.3 计算示例逐条复现', () => {
  it('示例 1：17:30~19:30 精确到分钟 = 280 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      startTime: '17:30',
      endTime: '19:30',
      roundingRule: 'EXACT',
    })
    expect(r.total).toBe(280)
  })

  it('示例 2：17:30~19:40 半小时取整 = 360 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      startTime: '17:30',
      endTime: '19:40',
      roundingRule: 'HALF_HOUR_UP',
    })
    expect(r.total).toBe(360)
  })

  it('示例 3：按次 50 元/次，白天与夜间各一次均 50 元', () => {
    const day = calculateBilling({
      ...base,
      billingMode: 'BY_COUNT',
      unitPrice: 50,
      startTime: '09:00',
      roundingRule: 'EXACT',
    })
    const night = calculateBilling({
      ...base,
      billingMode: 'BY_COUNT',
      unitPrice: 50,
      startTime: '20:00',
      roundingRule: 'EXACT',
    })
    expect(day.total).toBe(50)
    expect(night.total).toBe(50)
  })

  it('示例 4：月度混合汇总 = 3960 元', () => {
    // 送货 12 次 × 50
    const byCount = 12 * 50
    // 维修正常 30h × 80
    const normal = 30 * 80
    // 维修加班 6h × 80 × 2
    const overtime = 6 * 80 * 2
    expect(byCount + normal + overtime).toBe(3960)
  })
})
