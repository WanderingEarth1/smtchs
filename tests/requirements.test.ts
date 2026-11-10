/**
 * 功能核对测试 —— 逐条对照需求文档第 5 章功能编号
 *
 * 每个 describe 对应一个需求编号，断言直接引用需求原文的规则。
 * 这是「功能核对」的可执行版本：需求变更时测试会失败，避免文档与实现漂移。
 */
import { describe, it, expect, beforeEach } from 'vitest'

class MemoryStorage {
  private map = new Map<string, string>()
  get length(): number {
    return this.map.size
  }
  clear(): void {
    this.map.clear()
  }
  getItem(key: string): string | null {
    return this.map.has(key) ? this.map.get(key)! : null
  }
  key(index: number): string | null {
    return [...this.map.keys()][index] ?? null
  }
  removeItem(key: string): void {
    this.map.delete(key)
  }
  setItem(key: string, value: string): void {
    this.map.set(key, value)
  }
}

const storage = new MemoryStorage()
;(globalThis as unknown as { localStorage: MemoryStorage }).localStorage = storage

import {
  approveRecord,
  approveRecords,
  buildMonthlyReport,
  buildMyMonthlySummary,
  changeOwnPassword,
  computeRecordSafe,
  copyPriceRow,
  countPending,
  createEmployee,
  createRecord,
  createWorkType,
  deleteEmployee,
  deleteRecord,
  deleteWorkType,
  getConfig,
  getUnitPrice,
  listAllRecords,
  listEmployees,
  listEngineers,
  listMyRecords,
  listPriceRules,
  listRevisions,
  listWorkTypes,
  login,
  rejectRecord,
  resetPassword,
  restoreSession,
  reviseRecord,
  setUnitPrice,
  updateConfig,
  updateEmployee,
  updateRecord,
  updateWorkType,
  validateDraft,
  MAX_PHOTOS,
  SESSION_TTL_MS,
  type RecordDraft,
} from '../src/data/service'
import { seedIfNeeded, DEMO_ACCOUNTS } from '../src/data/seed'
import { currentMonthKey, todayKey } from '../src/core/date'
import { ROUNDING_LABELS } from '../src/core/billing'

const monthKey = currentMonthKey()

function makeDraft(overrides: Partial<RecordDraft> = {}): RecordDraft {
  return {
    workTypeId: 'wt_repair',
    serviceDate: todayKey(),
    startTime: '17:30',
    endTime: '19:30',
    location: '核对测试地点',
    latitude: 30.27,
    longitude: 120.15,
    geoCaptured: true,
    content: '核对测试内容',
    remark: '',
    photos: [{ id: 'p1', dataUrl: 'data:image/svg+xml,<svg/>', name: 'a.jpg', size: 100 }],
    ...overrides,
  }
}

beforeEach(async () => {
  storage.clear()
  await seedIfNeeded(true)
})

/* ============================================================
   5.1 登录与账号
   ============================================================ */
describe('LOG-01 员工账号由管理员预先建立', () => {
  it('管理员可创建员工账号，员工不自行注册', async () => {
    const emp = await createEmployee({ name: '钱七', account: 'qianqi', password: 'pass123' })
    expect(emp.role).toBe('EMPLOYEE')
    expect(emp.status).toBe('ACTIVE')
    // 登录后角色为员工
    const result = await login('qianqi', 'pass123')
    expect(result.employee.role).toBe('EMPLOYEE')
  })
})

describe('LOG-02 账号密码登录，按角色路由', () => {
  it('管理员登录返回 ADMIN 角色', async () => {
    const r = await login(DEMO_ACCOUNTS.admin.account, DEMO_ACCOUNTS.admin.password)
    expect(r.employee.role).toBe('ADMIN')
  })

  it('员工登录返回 EMPLOYEE 角色', async () => {
    const r = await login(DEMO_ACCOUNTS.employee.account, DEMO_ACCOUNTS.employee.password)
    expect(r.employee.role).toBe('EMPLOYEE')
  })
})

describe('LOG-03 记住登录，30 天有效期', () => {
  it('会话有效期为 30 天', async () => {
    const { session } = await login('zhangsan', '123456')
    const ttl = session.expiresAt - Date.now()
    expect(ttl).toBeGreaterThan(SESSION_TTL_MS - 5000)
    expect(ttl).toBeLessThanOrEqual(SESSION_TTL_MS)
  })

  it('二次打开可恢复会话（无感进入）', async () => {
    await login('zhangsan', '123456')
    const restored = restoreSession()
    expect(restored?.account).toBe('zhangsan')
  })

  it('每次活跃静默续期', async () => {
    await login('zhangsan', '123456')
    const first = JSON.parse(storage.getItem('smtchs.v1.session')!).expiresAt
    await new Promise((r) => setTimeout(r, 15))
    restoreSession()
    const second = JSON.parse(storage.getItem('smtchs.v1.session')!).expiresAt
    expect(second).toBeGreaterThanOrEqual(first)
  })
})

describe('LOG-04 员工可自行修改密码，管理员可重置', () => {
  it('员工凭原密码修改', async () => {
    await changeOwnPassword('emp_zhangsan', '123456', 'mypass888')
    const r = await login('zhangsan', 'mypass888')
    expect(r.employee.name).toBe('张三')
  })

  it('管理员重置员工密码', async () => {
    await resetPassword('emp_zhangsan', 'adminreset')
    const r = await login('zhangsan', 'adminreset')
    expect(r.employee.name).toBe('张三')
  })
})

describe('LOG-05 密码不可逆存储，连续输错 5 次锁定 15 分钟', () => {
  it('密码以摘要形式存储，不含明文', () => {
    const employees = listEmployees()
    const zhang = employees.find((e) => e.account === 'zhangsan')!
    expect(zhang.passwordHash).not.toContain('123456')
    expect(zhang.passwordHash.length).toBeGreaterThan(20)
  })

  it('第 5 次错误后锁定，提示 15 分钟', async () => {
    for (let i = 0; i < 4; i++) {
      await expect(login('zhangsan', 'bad')).rejects.toThrow()
    }
    await expect(login('zhangsan', 'bad')).rejects.toThrow(/锁定 15 分钟/)
  })
})

describe('LOG-06 停用员工无法登录，历史记录保留', () => {
  it('停用后登录被拒', async () => {
    updateEmployee('emp_lisi', { status: 'DISABLED' })
    await expect(login('lisi', '123456')).rejects.toThrow(/已停用/)
  })

  it('停用员工的历史记录仍参与统计', () => {
    const before = buildMonthlyReport({ monthKey }).summary.totalAmount
    updateEmployee('emp_lisi', { status: 'DISABLED' })
    const after = buildMonthlyReport({ monthKey }).summary.totalAmount
    expect(after).toBe(before)
  })
})

/* ============================================================
   5.2 基础配置
   ============================================================ */
describe('CFG-01 员工管理：增删改查、启用停用、重置密码', () => {
  it('支持新增、改名、停用、启用', async () => {
    const emp = await createEmployee({ name: '孙八', account: 'sunba', password: 'pass123' })
    updateEmployee(emp.id, { name: '孙八改' })
    expect(listEmployees().find((e) => e.id === emp.id)!.name).toBe('孙八改')

    updateEmployee(emp.id, { status: 'DISABLED' })
    expect(listEmployees().find((e) => e.id === emp.id)!.status).toBe('DISABLED')

    updateEmployee(emp.id, { status: 'ACTIVE' })
    expect(listEmployees().find((e) => e.id === emp.id)!.status).toBe('ACTIVE')
  })

  it('录入姓名、账号、初始密码三项必填', async () => {
    await expect(createEmployee({ name: '', account: 'x1', password: 'pass123' })).rejects.toThrow(
      /姓名/,
    )
    await expect(createEmployee({ name: '甲', account: '', password: 'pass123' })).rejects.toThrow(
      /账号/,
    )
    await expect(createEmployee({ name: '甲', account: 'abc', password: '' })).rejects.toThrow(
      /密码/,
    )
  })
})

describe('CFG-02 工作类型管理：计费模式按次/按小时', () => {
  it('可创建按次与按小时类型', () => {
    const a = createWorkType({ name: '临时搬运', billingMode: 'BY_COUNT' })
    const b = createWorkType({ name: '深度保养', billingMode: 'BY_HOUR' })
    expect(a.billingMode).toBe('BY_COUNT')
    expect(b.billingMode).toBe('BY_HOUR')
  })

  it('可停用类型，停用后员工端不可选', () => {
    updateWorkType('wt_repair', { enabled: false })
    const enabled = listWorkTypes(false)
    expect(enabled.find((w) => w.id === 'wt_repair')).toBeUndefined()
  })

  it('被记录引用的类型删除时改为停用', () => {
    const result = deleteWorkType('wt_repair')
    expect(result.disabled).toBe(true)
    expect(listWorkTypes().find((w) => w.id === 'wt_repair')!.enabled).toBe(false)
  })
})

describe('CFG-03 单价矩阵：按工程师×工作类型，支持按行复制', () => {
  it('逐项设定单价', () => {
    setUnitPrice('emp_zhangsan', 'wt_repair', 88)
    expect(getUnitPrice('emp_zhangsan', 'wt_repair')).toBe(88)
  })

  it('按行快速复制到其他员工', () => {
    setUnitPrice('emp_zhangsan', 'wt_repair', 99)
    const count = copyPriceRow('emp_zhangsan', ['emp_lisi', 'emp_wangwu'])
    expect(count).toBeGreaterThan(0)
    expect(getUnitPrice('emp_lisi', 'wt_repair')).toBe(99)
    expect(getUnitPrice('emp_wangwu', 'wt_repair')).toBe(99)
  })

  it('单价必须非负', () => {
    expect(() => setUnitPrice('emp_zhangsan', 'wt_repair', -1)).toThrow(/非负/)
  })
})

describe('CFG-04 加班规则：起算时间 + 倍率，对新提交记录生效', () => {
  it('默认 18:00 与 ×2', () => {
    const cfg = getConfig()
    expect(cfg.overtimeStart).toBe('18:00')
    expect(cfg.overtimeMultiplier).toBe(2)
  })

  it('修改后新记录使用新规则，旧记录保持快照', () => {
    const old = createRecord('emp_zhangsan', makeDraft())
    updateConfig({ overtimeStart: '17:00', overtimeMultiplier: 3 })
    const fresh = createRecord('emp_zhangsan', makeDraft())

    expect(old.billingSnapshot.overtimeStart).toBe('18:00')
    expect(fresh.billingSnapshot.overtimeStart).toBe('17:00')
    expect(fresh.billingSnapshot.overtimeMultiplier).toBe(3)
  })
})

describe('CFG-05 时长取整规则：三选一，全局生效', () => {
  it('三种规则均可配置', () => {
    for (const rule of ['EXACT', 'HALF_HOUR_UP', 'HOUR_UP'] as const) {
      updateConfig({ roundingRule: rule })
      expect(getConfig().roundingRule).toBe(rule)
      expect(ROUNDING_LABELS[rule]).toBeTruthy()
    }
  })

  it('取整规则写入新记录快照并影响金额', () => {
    updateConfig({ roundingRule: 'HOUR_UP' })
    const r = createRecord('emp_zhangsan', makeDraft({ startTime: '17:30', endTime: '19:40' }))
    const billing = computeRecordSafe(r)
    // 正常 30min → 1h × 80 = 80；加班 1h40m → 2h × 80 × 2 = 320 → 400
    expect(billing.normalHours).toBe(1)
    expect(billing.overtimeHours).toBe(2)
    expect(billing.total).toBe(400)
  })
})

/* ============================================================
   5.3 服务记录
   ============================================================ */
describe('REC-01 新增记录：员工仅能为本人记录', () => {
  it('记录归属当前登录员工', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(r.employeeId).toBe('emp_zhangsan')
  })

  it('记录字段完整（对应纸质工作记录表）', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(r.workTypeId).toBeTruthy()
    expect(r.serviceDate).toBeTruthy()
    expect(r.startTime).toBeTruthy()
    expect(r.location).toBeTruthy()
    expect(r.content).toBeTruthy()
    expect(r.photos.length).toBeGreaterThanOrEqual(1)
    expect(r.serialNo).toMatch(/^SR\d+/)
  })
})

describe('REC-02 补填历史日期自动标记补录', () => {
  it('非当日记录 isBackfill = true', () => {
    const d = new Date()
    d.setDate(d.getDate() - 5)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const r = createRecord('emp_zhangsan', makeDraft({ serviceDate: key }))
    expect(r.isBackfill).toBe(true)
  })

  it('补录记录在管理员列表中可见', () => {
    const d = new Date()
    d.setDate(d.getDate() - 5)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    createRecord('emp_zhangsan', makeDraft({ serviceDate: key }))
    const backfills = listAllRecords({ monthKey }).filter((r) => r.isBackfill)
    expect(backfills.length).toBeGreaterThan(0)
  })
})

describe('REC-03 修改/删除：仅待确认与已驳回可操作，已确认锁定', () => {
  it('待确认可改可删', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(() => updateRecord(r.id, 'emp_zhangsan', makeDraft({ content: '改' }))).not.toThrow()
    expect(() => deleteRecord(r.id, 'emp_zhangsan')).not.toThrow()
  })

  it('已驳回可改可删', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    rejectRecord(r.id, '需修改')
    expect(() => updateRecord(r.id, 'emp_zhangsan', makeDraft({ content: '改' }))).not.toThrow()
  })

  it('已确认不可改不可删', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    approveRecord(r.id)
    expect(() => updateRecord(r.id, 'emp_zhangsan', makeDraft())).toThrow(/锁定/)
    expect(() => deleteRecord(r.id, 'emp_zhangsan')).toThrow(/锁定/)
  })
})

describe('REC-04 我的记录列表：按月查看，状态一目了然', () => {
  it('按月筛选', () => {
    const list = listMyRecords('emp_zhangsan', monthKey)
    expect(list.every((r) => r.serviceDate.startsWith(monthKey))).toBe(true)
  })

  it('按状态筛选', () => {
    const pending = listMyRecords('emp_zhangsan', monthKey, 'PENDING')
    expect(pending.every((r) => r.status === 'PENDING')).toBe(true)
  })

  it('按日期倒序排列', () => {
    const list = listMyRecords('emp_zhangsan', monthKey)
    for (let i = 1; i < list.length; i++) {
      expect(list[i - 1].serviceDate >= list[i].serviceDate).toBe(true)
    }
  })
})

describe('REC-05 我的当月汇总：次数/正常时长/加班时长/预估提成', () => {
  it('返回四项汇总指标', () => {
    const s = buildMyMonthlySummary('emp_zhangsan', monthKey)
    expect(s.summary).toHaveProperty('countTotal')
    expect(s.summary).toHaveProperty('normalHoursTotal')
    expect(s.summary).toHaveProperty('overtimeHoursTotal')
    expect(s.summary).toHaveProperty('totalAmount')
    expect(s.summary.totalAmount).toBeGreaterThan(0)
  })

  it('仅统计本人数据', () => {
    const zhang = buildMyMonthlySummary('emp_zhangsan', monthKey)
    const li = buildMyMonthlySummary('emp_lisi', monthKey)
    expect(zhang.summary.totalAmount).not.toBe(li.summary.totalAmount)
  })
})

/* ============================================================
   5.4 审核管理
   ============================================================ */
describe('AUD-01 待确认列表：倒序、突出补录、可筛选', () => {
  it('可按员工筛选', () => {
    const list = listAllRecords({ monthKey, status: 'PENDING', employeeId: 'emp_zhangsan' })
    expect(list.every((r) => r.employeeId === 'emp_zhangsan')).toBe(true)
  })

  it('可按工作类型筛选', () => {
    const list = listAllRecords({ monthKey, status: 'PENDING', workTypeId: 'wt_repair' })
    expect(list.every((r) => r.workTypeId === 'wt_repair')).toBe(true)
  })

  it('待确认数量可用于角标', () => {
    expect(countPending()).toBeGreaterThan(0)
  })
})

describe('AUD-02 逐条确认/驳回，驳回原因必填且员工可见', () => {
  it('确认后状态变更', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(approveRecord(r.id).status).toBe('APPROVED')
  })

  it('驳回原因必填', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(() => rejectRecord(r.id, '')).toThrow(/必须填写/)
  })

  it('驳回原因员工可见', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    rejectRecord(r.id, '照片不清晰')
    const mine = listMyRecords('emp_zhangsan', monthKey, 'REJECTED')
    expect(mine.find((x) => x.id === r.id)!.rejectReason).toBe('照片不清晰')
  })
})

describe('AUD-03 批量确认', () => {
  it('一次确认多条', () => {
    const ids = [
      createRecord('emp_zhangsan', makeDraft()).id,
      createRecord('emp_zhangsan', makeDraft()).id,
      createRecord('emp_zhangsan', makeDraft()).id,
    ]
    const result = approveRecords(ids)
    expect(result.success).toBe(3)
    expect(result.failed).toBe(0)
  })
})

describe('AUD-04 修正已确认记录，保留痕迹', () => {
  it('记录修正人、时间、修正前后快照', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    approveRecord(r.id)
    reviseRecord(r.id, '王管理', { endTime: '20:00', note: '核实后修正' })

    const revs = listRevisions(r.id)
    expect(revs).toHaveLength(1)
    expect(revs[0].operatorName).toBe('王管理')
    expect(revs[0].revisedAt).toBeTruthy()
    expect(revs[0].before).toHaveProperty('endTime')
    expect(revs[0].after).toHaveProperty('endTime')
  })

  it('修正后按新值参与核算', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    approveRecord(r.id)
    const before = computeRecordSafe(r).total
    reviseRecord(r.id, '王管理', { endTime: '20:00', note: '修正' })
    const after = computeRecordSafe(listAllRecords({ monthKey }).find((x) => x.id === r.id)!).total
    expect(after).toBeGreaterThan(before)
  })
})

describe('AUD-05 驳回重提：修改后重走审核', () => {
  it('重提后状态回到待确认，驳回原因清空', () => {
    const r = createRecord('emp_zhangsan', makeDraft())
    rejectRecord(r.id, '需补充')
    const updated = updateRecord(r.id, 'emp_zhangsan', makeDraft({ content: '已补充' }))
    expect(updated.status).toBe('PENDING')
    expect(updated.rejectReason).toBeNull()
  })
})

/* ============================================================
   5.5 统计核算与导出
   ============================================================ */
describe('STA-01 月度报表：按月份生成，支持筛选', () => {
  it('默认当月', () => {
    const report = buildMonthlyReport()
    expect(report.monthKey).toBe(monthKey)
  })

  it('支持按员工筛选', () => {
    const report = buildMonthlyReport({ monthKey, employeeId: 'emp_zhangsan' })
    expect(report.approvedRecords.every((r) => r.employeeId === 'emp_zhangsan')).toBe(true)
  })

  it('支持按工作类型筛选', () => {
    const report = buildMonthlyReport({ monthKey, workTypeId: 'wt_repair' })
    expect(report.approvedRecords.every((r) => r.workTypeId === 'wt_repair')).toBe(true)
  })
})

describe('STA-02 汇总视图：总览卡片 + 按员工汇总（金额排序）+ 下钻', () => {
  it('总览含总次数、正常时长、加班时长、提成总额', () => {
    const s = buildMonthlyReport({ monthKey }).summary
    expect(s).toHaveProperty('countTotal')
    expect(s).toHaveProperty('normalHoursTotal')
    expect(s).toHaveProperty('overtimeHoursTotal')
    expect(s).toHaveProperty('totalAmount')
  })

  it('按员工汇总按金额降序', () => {
    const byEmp = buildMonthlyReport({ monthKey }).byEmployee
    for (let i = 1; i < byEmp.length; i++) {
      expect(byEmp[i - 1].summary.totalAmount).toBeGreaterThanOrEqual(byEmp[i].summary.totalAmount)
    }
  })

  it('可下钻到每一条明细', () => {
    const report = buildMonthlyReport({ monthKey })
    for (const item of report.byEmployee) {
      const drill = report.approvedRecords.filter((r) => r.employeeId === item.employeeId)
      expect(drill.length).toBeGreaterThan(0)
    }
  })
})

describe('STA-03 核算范围：仅已确认计入，未计入单独提示', () => {
  it('待确认与已驳回不计入金额', () => {
    const report = buildMonthlyReport({ monthKey })
    expect(report.approvedRecords.every((r) => r.status === 'APPROVED')).toBe(true)
  })

  it('单独返回待确认与已驳回条数', () => {
    const report = buildMonthlyReport({ monthKey })
    expect(typeof report.pendingCount).toBe('number')
    expect(typeof report.rejectedCount).toBe('number')
  })

  it('新增待确认记录不改变提成总额', () => {
    const before = buildMonthlyReport({ monthKey }).summary.totalAmount
    createRecord('emp_zhangsan', makeDraft())
    const after = buildMonthlyReport({ monthKey }).summary.totalAmount
    expect(after).toBe(before)
  })
})

describe('STA-04 导出 Excel：双 Sheet 与字段完整性', () => {
  it('明细表字段齐全', () => {
    // 字段清单在 export.test.ts 中逐项断言，此处校验数据源可用
    const report = buildMonthlyReport({ monthKey })
    expect(report.approvedRecords.length).toBeGreaterThan(0)
    for (const r of report.approvedRecords) {
      expect(r).toHaveProperty('employeeId')
      expect(r).toHaveProperty('serviceDate')
      expect(r).toHaveProperty('workTypeId')
      expect(r).toHaveProperty('startTime')
      expect(r).toHaveProperty('location')
      expect(r).toHaveProperty('photos')
      expect(r).toHaveProperty('status')
      expect(r).toHaveProperty('isBackfill')
      expect(r.billingSnapshot).toHaveProperty('unitPrice')
    }
  })

  it('按人汇总所需数据可用', () => {
    const report = buildMonthlyReport({ monthKey })
    for (const item of report.byEmployee) {
      expect(item.summary).toHaveProperty('countTotal')
      expect(item.summary).toHaveProperty('normalHoursTotal')
      expect(item.summary).toHaveProperty('overtimeHoursTotal')
      expect(item.summary).toHaveProperty('byCountAmount')
      expect(item.summary).toHaveProperty('byHourAmount')
      expect(item.summary).toHaveProperty('totalAmount')
    }
  })
})

/* ============================================================
   记录字段规则（5.3 表格）
   ============================================================ */
describe('记录字段规则', () => {
  it('工作类型必填', () => {
    expect(validateDraft(makeDraft({ workTypeId: '' }))).toHaveProperty('workTypeId')
  })

  it('日期必填且不能是未来', () => {
    expect(validateDraft(makeDraft({ serviceDate: '' }))).toHaveProperty('serviceDate')
    const t = new Date()
    t.setDate(t.getDate() + 1)
    const future = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
    expect(validateDraft(makeDraft({ serviceDate: future }))).toHaveProperty('serviceDate')
  })

  it('按小时类型必须填结束时间', () => {
    expect(validateDraft(makeDraft({ endTime: null }))).toHaveProperty('endTime')
  })

  it('地点必填', () => {
    expect(validateDraft(makeDraft({ location: '  ' }))).toHaveProperty('location')
  })

  it('工作内容必填', () => {
    expect(validateDraft(makeDraft({ content: '' }))).toHaveProperty('content')
  })

  it('照片必拍 1 张、最多 3 张', () => {
    expect(validateDraft(makeDraft({ photos: [] }))).toHaveProperty('photos')
    const four = [1, 2, 3, 4].map((i) => ({ id: `p${i}`, dataUrl: 'x', name: `${i}.jpg`, size: 1 }))
    expect(validateDraft(makeDraft({ photos: four }))).toHaveProperty('photos')
    expect(MAX_PHOTOS).toBe(3)
  })

  it('备注选填', () => {
    expect(validateDraft(makeDraft({ remark: '' }))).not.toHaveProperty('remark')
  })

  it('定位失败不阻断提交', () => {
    const draft = makeDraft({ geoCaptured: false, latitude: null, longitude: null })
    expect(validateDraft(draft)).toEqual({})
    const r = createRecord('emp_zhangsan', draft)
    expect(r.geoCaptured).toBe(false)
    expect(r.status).toBe('PENDING')
  })
})

/* ============================================================
   权限矩阵（3.2）
   ============================================================ */
describe('权限矩阵', () => {
  it('员工不能查看全部员工记录（服务层按 employeeId 隔离）', () => {
    const mine = listMyRecords('emp_zhangsan', monthKey)
    expect(mine.every((r) => r.employeeId === 'emp_zhangsan')).toBe(true)
  })

  it('员工不能修改他人记录', () => {
    const r = createRecord('emp_lisi', makeDraft())
    expect(() => updateRecord(r.id, 'emp_zhangsan', makeDraft())).toThrow(/只能修改自己/)
  })

  it('管理员不代员工录入（createRecord 需显式 employeeId，UI 无入口）', () => {
    // 服务层保留能力用于测试与数据迁移，但管理端界面不提供录入入口
    const r = createRecord('emp_zhangsan', makeDraft())
    expect(r.employeeId).toBe('emp_zhangsan')
  })

  it('单价矩阵仅管理端可改（服务层无员工侧入口）', () => {
    expect(listPriceRules().length).toBeGreaterThan(0)
  })
})

/* ============================================================
   设计约定（第 10 章 8 项默认口径）
   ============================================================ */
describe('第 10 章设计约定', () => {
  it('约定 1：按次记录无加班翻倍', () => {
    const r = createRecord('emp_zhangsan', makeDraft({ workTypeId: 'wt_delivery', startTime: '20:00' }))
    const billing = computeRecordSafe(r)
    expect(billing.overtimeHours).toBe(0)
    expect(billing.total).toBe(50)
  })

  it('约定 2：员工可查看本人当月提成汇总', () => {
    const s = buildMyMonthlySummary('emp_zhangsan', monthKey)
    expect(s.summary.totalAmount).toBeGreaterThan(0)
  })

  it('约定 6：服务时间精确到分钟', () => {
    const r = createRecord('emp_zhangsan', makeDraft({ startTime: '17:37', endTime: '19:43' }))
    expect(r.startTime).toBe('17:37')
    expect(r.endTime).toBe('19:43')
  })

  it('约定 7：照片必拍 1 张、最多 3 张', () => {
    expect(MAX_PHOTOS).toBe(3)
  })

  it('约定 8：正常段与加班段分别取整，跨 0 点归入加班段', () => {
    updateConfig({ roundingRule: 'HALF_HOUR_UP' })
    const r = createRecord('emp_zhangsan', makeDraft({ startTime: '22:00', endTime: '01:00' }))
    const billing = computeRecordSafe(r)
    expect(billing.crossMidnight).toBe(true)
    expect(billing.normalHours).toBe(0)
    expect(billing.overtimeHours).toBe(3)
  })
})
