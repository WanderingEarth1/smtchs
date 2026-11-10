import { describe, it, expect } from 'vitest'
import {
  calculateBilling,
  applyRounding,
  parseTimeToMinutes,
  minutesToTime,
  roundMoney,
  accumulate,
  emptySummary,
} from '../src/core/billing'

const base = {
  overtimeStart: '18:00',
  overtimeMultiplier: 2,
}

describe('时间工具', () => {
  it('解析 HH:mm', () => {
    expect(parseTimeToMinutes('00:00')).toBe(0)
    expect(parseTimeToMinutes('09:30')).toBe(570)
    expect(parseTimeToMinutes('18:00')).toBe(1080)
    expect(parseTimeToMinutes('23:59')).toBe(1439)
  })

  it('非法时间抛错', () => {
    expect(() => parseTimeToMinutes('24:00')).toThrow()
    expect(() => parseTimeToMinutes('9:5')).toThrow()
    expect(() => parseTimeToMinutes('abc')).toThrow()
  })

  it('分钟转 HH:mm 支持跨日回绕', () => {
    expect(minutesToTime(1080)).toBe('18:00')
    expect(minutesToTime(1500)).toBe('01:00')
  })

  it('金额四舍五入到分', () => {
    expect(roundMoney(0.1 + 0.2)).toBe(0.3)
    expect(roundMoney(40.005)).toBe(40.01)
  })
})

describe('取整规则', () => {
  it('EXACT 不取整', () => {
    expect(applyRounding(100, 'EXACT')).toBe(100)
    expect(applyRounding(1, 'EXACT')).toBe(1)
  })

  it('HALF_HOUR_UP 半小时向上取整', () => {
    expect(applyRounding(30, 'HALF_HOUR_UP')).toBe(30)
    expect(applyRounding(31, 'HALF_HOUR_UP')).toBe(60)
    expect(applyRounding(100, 'HALF_HOUR_UP')).toBe(120)
    expect(applyRounding(1, 'HALF_HOUR_UP')).toBe(30)
  })

  it('HOUR_UP 整小时向上取整', () => {
    expect(applyRounding(60, 'HOUR_UP')).toBe(60)
    expect(applyRounding(61, 'HOUR_UP')).toBe(120)
    expect(applyRounding(100, 'HOUR_UP')).toBe(120)
    expect(applyRounding(1, 'HOUR_UP')).toBe(60)
  })

  it('0 或负数不取整', () => {
    expect(applyRounding(0, 'HOUR_UP')).toBe(0)
    expect(applyRounding(-10, 'HALF_HOUR_UP')).toBe(0)
  })
})

describe('6.1 按次计费', () => {
  it('示例 3：李四送货 50 元/次，一次 50 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_COUNT',
      unitPrice: 50,
      startTime: '09:00',
      roundingRule: 'EXACT',
    })
    expect(r.total).toBe(50)
    expect(r.count).toBe(1)
    expect(r.overtimeAmount).toBe(0)
  })

  it('示例 3 续：当晚 20:00 再送一次仍为 50 元（按次无加班）', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_COUNT',
      unitPrice: 50,
      startTime: '20:00',
      roundingRule: 'EXACT',
    })
    expect(r.total).toBe(50)
    expect(r.overtimeHours).toBe(0)
  })

  it('按次不受取整规则影响', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_COUNT',
      unitPrice: 33.33,
      startTime: '09:00',
      roundingRule: 'HOUR_UP',
    })
    expect(r.total).toBe(33.33)
  })
})

describe('6.2 按小时计费（含加班）', () => {
  it('示例 1：17:30~19:30，80 元/小时，精确到分钟 → 280 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '17:30',
      endTime: '19:30',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(0.5)
    expect(r.overtimeHours).toBe(1.5)
    expect(r.normalAmount).toBe(40)
    expect(r.overtimeAmount).toBe(240)
    expect(r.total).toBe(280)
  })

  it('示例 2：17:30~19:40，半小时向上取整 → 360 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '17:30',
      endTime: '19:40',
      roundingRule: 'HALF_HOUR_UP',
    })
    expect(r.rawNormalHours).toBe(0.5)
    expect(r.normalHours).toBe(0.5)
    expect(r.rawOvertimeHours).toBeCloseTo(1.6667, 4)
    expect(r.overtimeHours).toBe(2)
    expect(r.normalAmount).toBe(40)
    expect(r.overtimeAmount).toBe(320)
    expect(r.total).toBe(360)
  })

  it('示例 2 变体：整小时向上取整 → 正常 1h + 加班 2h = 400 元', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '17:30',
      endTime: '19:40',
      roundingRule: 'HOUR_UP',
    })
    // 正常段 30 分钟 → 整小时向上取整 = 1h
    expect(r.normalHours).toBe(1)
    expect(r.overtimeHours).toBe(2)
    expect(r.normalAmount).toBe(80)
    expect(r.overtimeAmount).toBe(320)
    expect(r.total).toBe(400)
  })

  it('示例 4：维修正常 30h × 80 + 加班 6h × 80 × 2 = 3360 元（单条记录口径）', () => {
    // 用一条 30h 正常 + 6h 加班无法单条表达，这里验证月度累加口径
    const summary = emptySummary()
    accumulate(
      summary,
      calculateBilling({
        ...base,
        billingMode: 'BY_HOUR',
        unitPrice: 80,
        startTime: '08:00',
        endTime: '18:00',
        roundingRule: 'EXACT',
      }),
    )
    expect(summary.normalHoursTotal).toBe(10)
    expect(summary.byHourAmount).toBe(800)
  })

  it('全部落在加班时段：开始已晚于起算点 → T₁ = 0', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '19:00',
      endTime: '21:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(0)
    expect(r.normalAmount).toBe(0)
    expect(r.overtimeHours).toBe(2)
    expect(r.overtimeAmount).toBe(320)
    expect(r.total).toBe(320)
  })

  it('全部落在正常时段：结束早于起算点 → T₂ = 0', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '09:00',
      endTime: '12:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(3)
    expect(r.overtimeHours).toBe(0)
    expect(r.overtimeAmount).toBe(0)
    expect(r.total).toBe(240)
  })

  it('恰好结束在起算点：无加班', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '16:00',
      endTime: '18:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(2)
    expect(r.overtimeHours).toBe(0)
    expect(r.total).toBe(160)
  })

  it('恰好开始于起算点：全部加班', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '18:00',
      endTime: '20:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(0)
    expect(r.overtimeHours).toBe(2)
    expect(r.total).toBe(320)
  })

  it('跨 0 点连续服务归入加班段（约定 8）', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 80,
      startTime: '22:00',
      endTime: '01:00',
      roundingRule: 'EXACT',
    })
    expect(r.crossMidnight).toBe(true)
    expect(r.normalHours).toBe(0)
    expect(r.overtimeHours).toBe(3)
    expect(r.total).toBe(480)
  })

  it('跨 0 点且跨越起算点：正常段与加班段分别计算', () => {
    // 17:00 开始，次日 01:00 结束 → 正常 17:00~18:00 = 1h，加班 18:00~次日01:00 = 7h
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 100,
      startTime: '17:00',
      endTime: '01:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(1)
    expect(r.overtimeHours).toBe(7)
    expect(r.normalAmount).toBe(100)
    expect(r.overtimeAmount).toBe(1400)
    expect(r.total).toBe(1500)
  })

  it('自定义加班起算时间 20:00 与倍率 1.5', () => {
    const r = calculateBilling({
      overtimeStart: '20:00',
      overtimeMultiplier: 1.5,
      billingMode: 'BY_HOUR',
      unitPrice: 100,
      startTime: '19:00',
      endTime: '22:00',
      roundingRule: 'EXACT',
    })
    expect(r.normalHours).toBe(1)
    expect(r.overtimeHours).toBe(2)
    expect(r.normalAmount).toBe(100)
    expect(r.overtimeAmount).toBe(300)
    expect(r.total).toBe(400)
  })

  it('按小时缺少结束时间抛错', () => {
    expect(() =>
      calculateBilling({
        ...base,
        billingMode: 'BY_HOUR',
        unitPrice: 80,
        startTime: '09:00',
        endTime: null,
        roundingRule: 'EXACT',
      }),
    ).toThrow()
  })

  it('单价为负抛错', () => {
    expect(() =>
      calculateBilling({
        ...base,
        billingMode: 'BY_COUNT',
        unitPrice: -1,
        startTime: '09:00',
        roundingRule: 'EXACT',
      }),
    ).toThrow()
  })

  it('取整后金额与手工核算一致（浮点安全）', () => {
    const r = calculateBilling({
      ...base,
      billingMode: 'BY_HOUR',
      unitPrice: 33.33,
      startTime: '17:10',
      endTime: '19:20',
      roundingRule: 'HALF_HOUR_UP',
    })
    // 正常 50min → 60min = 1h → 33.33
    // 加班 80min → 90min = 1.5h → 1.5 × 33.33 × 2 = 99.99
    expect(r.normalAmount).toBe(33.33)
    expect(r.overtimeAmount).toBe(99.99)
    expect(r.total).toBe(133.32)
  })
})

describe('月度汇总累加', () => {
  it('示例 4：送货 12 次 × 50 + 维修正常 30h × 80 + 加班 6h × 80 × 2 = 3960 元', () => {
    const summary = emptySummary()
    // 12 次送货
    for (let i = 0; i < 12; i++) {
      accumulate(
        summary,
        calculateBilling({
          ...base,
          billingMode: 'BY_COUNT',
          unitPrice: 50,
          startTime: '09:00',
          roundingRule: 'EXACT',
        }),
      )
    }
    // 维修正常 30h：3 条 10h 记录（08:00~18:00）
    for (let i = 0; i < 3; i++) {
      accumulate(
        summary,
        calculateBilling({
          ...base,
          billingMode: 'BY_HOUR',
          unitPrice: 80,
          startTime: '08:00',
          endTime: '18:00',
          roundingRule: 'EXACT',
        }),
      )
    }
    // 维修加班 6h：3 条 2h 加班记录（18:00~20:00）
    for (let i = 0; i < 3; i++) {
      accumulate(
        summary,
        calculateBilling({
          ...base,
          billingMode: 'BY_HOUR',
          unitPrice: 80,
          startTime: '18:00',
          endTime: '20:00',
          roundingRule: 'EXACT',
        }),
      )
    }

    expect(summary.recordCount).toBe(18)
    expect(summary.countTotal).toBe(12)
    expect(summary.normalHoursTotal).toBe(30)
    expect(summary.overtimeHoursTotal).toBe(6)
    expect(summary.byCountAmount).toBe(600)
    expect(summary.byHourAmount).toBe(2400 + 960)
    expect(summary.totalAmount).toBe(3960)
  })
})
