/**
 * 业务服务层测试
 *
 * 覆盖：登录安全、记录状态机、审核、修正留痕、月度核算口径
 * 使用内存版 localStorage 模拟持久层
 */

// ---- localStorage 桩（需在导入被测模块前安装） ----
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
    this.map.set(key, String(value))
  }
}

const storage = new MemoryStorage()
;(globalThis as unknown as { localStorage: MemoryStorage }).localStorage = storage

import { describe, it, expect, beforeEach } from 'vitest'
import {
  approveRecord,
  approveRecords,
  buildMonthlyReport,
  buildMyMonthlySummary,
  computeRecordSafe,
  createRecord,
  deleteRecord,
  getRecord,
  getUnitPrice,
  listMyRecords,
  listRevisions,
  login,
  logout,
  rejectRecord,
  restoreSession,
  reviseRecord,
  updateRecord,
  updateConfig,
  createEmployee,
  updateEmployee,
  deleteEmployee,
  resetPassword,
  changeOwnPassword,
  MAX_FAILED_ATTEMPTS,
  BizError,
  type RecordDraft,
} from '../src/data/service'
import { seedIfNeeded, DEMO_ACCOUNTS } from '../src/data/seed'
import { currentMonthKey, todayKey } from '../src/core/date'

/** 构造合法草稿 */
function makeDraft(overrides: Partial<RecordDraft> = {}): RecordDraft {
  return {
    workTypeId: 'wt_repair',
    serviceDate: todayKey(),
    startTime: '17:30',
    endTime: '19:30',
    location: '测试地点 1 号楼',
    latitude: 30.27,
    longitude: 120.15,
    geoCaptured: true,
    content: '测试工作内容',
    remark: '',
    photos: [{ id: 'p1', dataUrl: 'data:image/svg+xml,<svg/>', name: 'a.jpg', size: 100 }],
    ...overrides,
  }
}

const monthKey = currentMonthKey()

beforeEach(async () => {
  storage.clear()
  await seedIfNeeded(true)
})

/* ============================================================
   登录与会话（LOG-01 ~ LOG-06）
   ============================================================ */
describe('登录与会话', () => {
  it('管理员与员工均可登录并按角色返回', async () => {
    const admin = await login(DEMO_ACCOUNTS.admin.account, DEMO_ACCOUNTS.admin.password)
    expect(admin.employee.role).toBe('ADMIN')

    const emp = await login(DEMO_ACCOUNTS.employee.account, DEMO_ACCOUNTS.employee.password)
    expect(emp.employee.role).toBe('EMPLOYEE')
    expect(emp.employee.name).toBe('张三')
  })

  it('密码错误提示剩余次数', async () => {
    await expect(login('zhangsan', 'wrong')).rejects.toThrow(/还可尝试 4 次/)
    await expect(login('zhangsan', 'wrong')).rejects.toThrow(/还可尝试 3 次/)
  })

  it('连续输错 5 次锁定 15 分钟（LOG-05）', async () => {
    for (let i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
      await expect(login('zhangsan', 'wrong')).rejects.toThrow()
    }
    await expect(login('zhangsan', 'wrong')).rejects.toThrow(/锁定 15 分钟/)
    // 锁定期间即使密码正确也不能登录
    await expect(login('zhangsan', DEMO_ACCOUNTS.employee.password)).rejects.toThrow(/锁定/)
  })

  it('停用员工无法登录（LOG-06）', async () => {
    await expect(login('zhaoliu', '123456')).rejects.toThrow(/已停用/)
  })

  it('登录后可恢复会话（LOG-03）', async () => {
    const { employee } = await login('zhangsan', '123456')
    const restored = restoreSession()
    expect(restored?.id).toBe(employee.id)

    logout()
    expect(restoreSession()).toBeNull()
  })

  it('会话过期后不再恢复', async () => {
    await login('zhangsan', '123456')
    const raw = JSON.parse(storage.getItem('smtchs.v1.session')!)
    raw.expiresAt = Date.now() - 1000
    storage.setItem('smtchs.v1.session', JSON.stringify(raw))
    expect(restoreSession()).toBeNull()
  })

  it('修改密码需校验原密码', async () => {
    await expect(changeOwnPassword('emp_zhangsan', 'bad', 'newpass123')).rejects.toThrow(/原密码/)
    await changeOwnPassword('emp_zhangsan', '123456', 'newpass123')
    await expect(login('zhangsan', '123456')).rejects.toThrow()
    const ok = await login('zhangsan', 'newpass123')
    expect(ok.employee.name).toBe('张三')
  })
})

/* ============================================================
   记录状态机（REC-01 ~ REC-03 / AUD-02 / AUD-04 / AUD-05）
   ============================================================ */
describe('记录状态机', () => {
  it('新增记录进入待确认状态（REC-01）', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    expect(record.status).toBe('PENDING')
    expect(record.billingSnapshot.unitPrice).toBe(80)
    expect(record.billingSnapshot.billingMode).toBe('BY_HOUR')
    expect(record.photos.length).toBe(1)
  })

  it('非当日记录自动标记补录（REC-02）', () => {
    const backfillDate = new Date()
    backfillDate.setDate(backfillDate.getDate() - 3)
    const dateKey = `${backfillDate.getFullYear()}-${String(backfillDate.getMonth() + 1).padStart(2, '0')}-${String(backfillDate.getDate()).padStart(2, '0')}`

    const record = createRecord('emp_zhangsan', makeDraft({ serviceDate: dateKey }))
    expect(record.isBackfill).toBe(true)

    const todayRecord = createRecord('emp_zhangsan', makeDraft())
    expect(todayRecord.isBackfill).toBe(false)
  })

  it('未来日期被拒绝', () => {
    const tomorrow = new Date()
    tomorrow.setDate(tomorrow.getDate() + 1)
    const dateKey = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`
    expect(() => createRecord('emp_zhangsan', makeDraft({ serviceDate: dateKey }))).toThrow(/未来/)
  })

  it('缺少照片被拒绝', () => {
    expect(() => createRecord('emp_zhangsan', makeDraft({ photos: [] }))).toThrow(/至少拍摄 1 张/)
  })

  it('超过 3 张照片被拒绝', () => {
    const photos = [1, 2, 3, 4].map((i) => ({
      id: `p${i}`,
      dataUrl: 'x',
      name: `${i}.jpg`,
      size: 1,
    }))
    expect(() => createRecord('emp_zhangsan', makeDraft({ photos }))).toThrow(/最多上传 3 张/)
  })

  it('按小时类型缺少结束时间被拒绝', () => {
    expect(() => createRecord('emp_zhangsan', makeDraft({ endTime: null }))).toThrow(/结束时间/)
  })

  it('按次类型自动清空结束时间', () => {
    const record = createRecord('emp_zhangsan', makeDraft({ workTypeId: 'wt_delivery', endTime: '18:00' }))
    expect(record.endTime).toBeNull()
    expect(record.billingSnapshot.billingMode).toBe('BY_COUNT')
  })

  it('待确认记录可修改并重走审核（AUD-05）', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    const updated = updateRecord(record.id, 'emp_zhangsan', makeDraft({ content: '修改后的内容' }))
    expect(updated.content).toBe('修改后的内容')
    expect(updated.status).toBe('PENDING')
  })

  it('已确认记录不可修改、不可删除（REC-03）', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)

    expect(() => updateRecord(record.id, 'emp_zhangsan', makeDraft())).toThrow(/已锁定/)
    expect(() => deleteRecord(record.id, 'emp_zhangsan')).toThrow(/已锁定/)
  })

  it('只能操作自己的记录', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    expect(() => updateRecord(record.id, 'emp_lisi', makeDraft())).toThrow(/只能修改自己/)
    expect(() => deleteRecord(record.id, 'emp_lisi')).toThrow(/只能删除自己/)
  })

  it('待确认记录可删除', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    deleteRecord(record.id, 'emp_zhangsan')
    expect(getRecord(record.id)).toBeUndefined()
  })

  it('已驳回记录可修改后重新提交', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    rejectRecord(record.id, '照片不清晰')
    expect(getRecord(record.id)!.status).toBe('REJECTED')

    const updated = updateRecord(record.id, 'emp_zhangsan', makeDraft({ content: '补充说明后重提' }))
    expect(updated.status).toBe('PENDING')
    expect(updated.rejectReason).toBeNull()
  })
})

/* ============================================================
   审核（AUD-02 / AUD-03 / AUD-04）
   ============================================================ */
describe('审核', () => {
  it('确认后记录锁定并记录确认时间', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    const approved = approveRecord(record.id)
    expect(approved.status).toBe('APPROVED')
    expect(approved.confirmedAt).toBeTruthy()
  })

  it('重复确认被拒绝', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)
    expect(() => approveRecord(record.id)).toThrow(/已确认/)
  })

  it('驳回原因必填（AUD-02）', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    expect(() => rejectRecord(record.id, '   ')).toThrow(/必须填写驳回原因/)
    const rejected = rejectRecord(record.id, '时间有误')
    expect(rejected.status).toBe('REJECTED')
    expect(rejected.rejectReason).toBe('时间有误')
  })

  it('批量确认只处理待确认记录（AUD-03）', () => {
    const a = createRecord('emp_zhangsan', makeDraft())
    const b = createRecord('emp_zhangsan', makeDraft())
    const c = createRecord('emp_zhangsan', makeDraft())
    rejectRecord(c.id, '不合规')

    const result = approveRecords([a.id, b.id, c.id])
    expect(result.success).toBe(2)
    expect(result.failed).toBe(1)
    expect(getRecord(a.id)!.status).toBe('APPROVED')
    expect(getRecord(c.id)!.status).toBe('REJECTED')
  })

  it('修正已确认记录并留痕（AUD-04）', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)

    const revised = reviseRecord(record.id, '王管理', {
      endTime: '20:00',
      note: '电话核实实际结束时间为 20:00',
    })
    expect(revised.endTime).toBe('20:00')

    const logs = listRevisions(record.id)
    expect(logs).toHaveLength(1)
    expect(logs[0].operatorName).toBe('王管理')
    expect(logs[0].before.endTime).toBe('19:30')
    expect(logs[0].after.endTime).toBe('20:00')
    expect(logs[0].note).toContain('电话核实')
  })

  it('修正必须填写说明', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)
    expect(() => reviseRecord(record.id, '王管理', { endTime: '20:00', note: '' })).toThrow(/修正说明/)
  })

  it('未修改任何字段时拒绝提交修正', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)
    expect(() => reviseRecord(record.id, '王管理', { note: '无变化' })).toThrow(/未检测到任何修改/)
  })

  it('未确认记录不走修正流程', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    expect(() => reviseRecord(record.id, '王管理', { endTime: '20:00', note: 'x' })).toThrow(
      /仅已确认/,
    )
  })

  it('修正后按新值参与核算', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    approveRecord(record.id)
    const before = computeRecordSafe(getRecord(record.id)!).total

    reviseRecord(record.id, '王管理', { endTime: '20:00', note: '修正结束时间' })
    const after = computeRecordSafe(getRecord(record.id)!).total

    expect(after).toBeGreaterThan(before)
    // 17:30~20:00：正常 0.5h × 80 = 40；加班 2h × 80 × 2 = 320 → 360
    expect(after).toBe(360)
  })
})

/* ============================================================
   核算口径（STA-02 / STA-03）
   ============================================================ */
describe('月度核算', () => {
  it('仅已确认记录计入报表（STA-03）', () => {
    const before = buildMonthlyReport({ monthKey }).summary.totalAmount

    const a = createRecord('emp_zhangsan', makeDraft())
    approveRecord(a.id)
    createRecord('emp_zhangsan', makeDraft())
    rejectRecord(createRecord('emp_zhangsan', makeDraft()).id, '驳回测试')

    const after = buildMonthlyReport({ monthKey })
    expect(after.summary.totalAmount).toBeGreaterThan(before)
    expect(after.pendingCount).toBeGreaterThanOrEqual(1)
    expect(after.rejectedCount).toBeGreaterThanOrEqual(1)
  })

  it('报表按员工汇总金额排序', () => {
    const report = buildMonthlyReport({ monthKey })
    const amounts = report.byEmployee.map((e) => e.summary.totalAmount)
    const sorted = [...amounts].sort((a, b) => b - a)
    expect(amounts).toEqual(sorted)
  })

  it('报表数字与明细逐条累加一致（统计一致性）', () => {
    const report = buildMonthlyReport({ monthKey })

    const manualTotal = report.approvedRecords.reduce(
      (sum, r) => sum + computeRecordSafe(r).total,
      0,
    )
    expect(Math.round(manualTotal * 100) / 100).toBe(report.summary.totalAmount)

    const manualNormalHours = report.approvedRecords.reduce(
      (sum, r) => sum + computeRecordSafe(r).normalHours,
      0,
    )
    expect(Math.round(manualNormalHours * 10000) / 10000).toBe(report.summary.normalHoursTotal)
  })

  it('员工当月汇总包含待确认/已驳回计数（REC-05）', () => {
    const created = createRecord('emp_zhangsan', makeDraft())
    const summary = buildMyMonthlySummary('emp_zhangsan', monthKey)
    expect(summary.recordCount).toBeGreaterThan(0)
    expect(summary.pendingCount).toBeGreaterThanOrEqual(1)
    expect(summary.summary.totalAmount).toBeGreaterThan(0)
    expect(created.status).toBe('PENDING')
  })

  it('员工记录列表按月与状态筛选（REC-04）', () => {
    const all = listMyRecords('emp_zhangsan', monthKey)
    const pending = listMyRecords('emp_zhangsan', monthKey, 'PENDING')
    const approved = listMyRecords('emp_zhangsan', monthKey, 'APPROVED')

    expect(all.length).toBe(pending.length + approved.length + listMyRecords('emp_zhangsan', monthKey, 'REJECTED').length)
    expect(pending.every((r) => r.status === 'PENDING')).toBe(true)
  })
})

/* ============================================================
   计费规则变更的影响面（CFG-04 / CFG-05）
   ============================================================ */
describe('计费规则变更', () => {
  it('加班规则变更仅影响新提交记录', () => {
    const oldRecord = createRecord('emp_zhangsan', makeDraft())
    const oldSnapshot = { ...oldRecord.billingSnapshot }

    updateConfig({ overtimeStart: '17:00', overtimeMultiplier: 3 })

    // 旧记录快照不变
    expect(getRecord(oldRecord.id)!.billingSnapshot.overtimeStart).toBe(oldSnapshot.overtimeStart)
    expect(getRecord(oldRecord.id)!.billingSnapshot.overtimeMultiplier).toBe(
      oldSnapshot.overtimeMultiplier,
    )

    // 新记录使用新配置
    const newRecord = createRecord('emp_zhangsan', makeDraft())
    expect(newRecord.billingSnapshot.overtimeStart).toBe('17:00')
    expect(newRecord.billingSnapshot.overtimeMultiplier).toBe(3)
  })

  it('取整规则变更写入新记录快照', () => {
    updateConfig({ roundingRule: 'HALF_HOUR_UP' })
    const record = createRecord('emp_zhangsan', makeDraft({ startTime: '17:30', endTime: '19:40' }))
    expect(record.billingSnapshot.roundingRule).toBe('HALF_HOUR_UP')

    // 17:30~18:00 正常 0.5h → 40；18:00~19:40 加班 1h40m → 取整 2h × 80 × 2 = 320
    const billing = computeRecordSafe(record)
    expect(billing.normalHours).toBe(0.5)
    expect(billing.overtimeHours).toBe(2)
    expect(billing.total).toBe(360)
  })

  it('非法配置被拒绝', () => {
    expect(() => updateConfig({ overtimeStart: '25:00' })).toThrow()
    expect(() => updateConfig({ overtimeMultiplier: 0 })).toThrow()
    expect(() => updateConfig({ overtimeMultiplier: 11 })).toThrow()
  })
})

/* ============================================================
   配置管理（CFG-01 / CFG-02 / CFG-03）
   ============================================================ */
describe('配置管理', () => {
  it('新增员工可用账号登录（LOG-01）', async () => {
    const emp = await createEmployee({ name: '新员工', account: 'newbie', password: 'pass123' })
    expect(emp.status).toBe('ACTIVE')
    const result = await login('newbie', 'pass123')
    expect(result.employee.name).toBe('新员工')
  })

  it('账号重复被拒绝', async () => {
    await expect(
      createEmployee({ name: '重名', account: 'zhangsan', password: 'pass123' }),
    ).rejects.toThrow(/已存在/)
  })

  it('密码过短被拒绝', async () => {
    await expect(
      createEmployee({ name: '短密码', account: 'shortpw', password: '123' }),
    ).rejects.toThrow(/至少 6 位/)
  })

  it('停用员工后无法登录', async () => {
    updateEmployee('emp_lisi', { status: 'DISABLED' })
    await expect(login('lisi', '123456')).rejects.toThrow(/已停用/)
    updateEmployee('emp_lisi', { status: 'ACTIVE' })
    const ok = await login('lisi', '123456')
    expect(ok.employee.name).toBe('李四')
  })

  it('不能停用管理员', () => {
    expect(() => updateEmployee('emp_admin', { status: 'DISABLED' })).toThrow(/管理员/)
  })

  it('有记录的员工删除时改为停用', () => {
    const result = deleteEmployee('emp_zhangsan')
    expect(result.disabled).toBe(true)
  })

  it('无记录的员工真正删除', async () => {
    const emp = await createEmployee({ name: '临时工', account: 'temp01', password: 'pass123' })
    const result = deleteEmployee(emp.id)
    expect(result.disabled).toBe(false)
  })

  it('重置密码后可用新密码登录', async () => {
    await resetPassword('emp_zhangsan', 'reset999')
    const ok = await login('zhangsan', 'reset999')
    expect(ok.employee.name).toBe('张三')
  })

  it('单价矩阵按员工与类型取值（CFG-03）', () => {
    expect(getUnitPrice('emp_zhangsan', 'wt_repair')).toBe(80)
    expect(getUnitPrice('emp_lisi', 'wt_repair')).toBe(75)
    expect(getUnitPrice('emp_wangwu', 'wt_delivery')).toBe(45)
  })
})

/* ============================================================
   异常与边界
   ============================================================ */
describe('异常与边界', () => {
  it('BizError 携带可展示的中文消息', () => {
    try {
      createRecord('emp_zhangsan', makeDraft({ location: '' }))
    } catch (err) {
      expect(err).toBeInstanceOf(BizError)
      expect((err as BizError).message).toBe('请填写服务地点')
    }
  })

  it('关键字段为空的记录在核算时降级为 0 而不崩溃', () => {
    const record = createRecord('emp_zhangsan', makeDraft())
    const broken = {
      ...record,
      endTime: null,
      billingSnapshot: { ...record.billingSnapshot, billingMode: 'BY_HOUR' as const },
    }
    expect(computeRecordSafe(broken as never).total).toBe(0)
  })
})
