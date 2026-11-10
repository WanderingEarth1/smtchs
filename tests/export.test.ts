/**
 * 验收 #5：月度报表数字与导出 Excel 数字一致
 *
 * 做法：在 Node 环境用内存 localStorage 跑完整业务链路，
 * 生成报表 → 用与 export.ts 相同的行构造逻辑生成 Sheet 数据 →
 * 用 SheetJS 真实写出 .xlsx 再读回 → 逐格比对。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as XLSX from 'xlsx'

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

import {
  buildMonthlyReport,
  computeRecordSafe,
  getEmployeeName,
  getWorkType,
  listEngineers,
  listWorkTypes,
} from '../src/data/service'
import { seedIfNeeded } from '../src/data/seed'
import { currentMonthKey } from '../src/core/date'
import type { WorkRecord, WorkType } from '../src/core/types'

const monthKey = currentMonthKey()

beforeEach(async () => {
  storage.clear()
  await seedIfNeeded(true)
})

/** 与 src/data/export.ts 保持一致的明细行构造（此处独立复刻以便断言） */
function buildDetailRows(
  records: WorkRecord[],
  workTypeMap: Map<string, WorkType>,
  employeeNameMap: Map<string, string>,
): Record<string, string | number>[] {
  return records.map((record) => {
    const billing = computeRecordSafe(record)
    const wt = workTypeMap.get(record.workTypeId)
    const mode = wt?.billingMode ?? record.billingSnapshot.billingMode
    return {
      流水号: record.serialNo,
      员工: employeeNameMap.get(record.employeeId) ?? '未知员工',
      日期: record.serviceDate,
      工作类型: wt?.name ?? '未知类型',
      计费模式: mode === 'BY_HOUR' ? '按小时' : '按次',
      开始时间: record.startTime,
      结束时间: record.endTime ?? '—',
      正常时长h: billing.billingMode === 'BY_HOUR' ? billing.normalHours : 0,
      加班时长h: billing.billingMode === 'BY_HOUR' ? billing.overtimeHours : 0,
      地点: record.location,
      定位: record.geoCaptured ? '已获取' : '未获取定位',
      工作内容摘要: record.content,
      照片链接: record.photos.length > 0 ? `凭证1` : '无',
      状态: record.status === 'APPROVED' ? '已确认' : record.status === 'PENDING' ? '待确认' : '已驳回',
      是否补录: record.isBackfill ? '是' : '否',
      单价: record.billingSnapshot.unitPrice,
      金额: billing.total,
    }
  })
}

function buildSummaryRows(report: ReturnType<typeof buildMonthlyReport>) {
  const rows = report.byEmployee.map((item) => ({
    员工: item.employeeName,
    次数合计: item.summary.countTotal,
    正常时长合计h: Math.round(item.summary.normalHoursTotal * 100) / 100,
    加班时长合计h: Math.round(item.summary.overtimeHoursTotal * 100) / 100,
    按次金额: item.summary.byCountAmount,
    按时金额: item.summary.byHourAmount,
    提成合计: item.summary.totalAmount,
  }))
  const s = report.summary
  rows.push({
    员工: '合计',
    次数合计: s.countTotal,
    正常时长合计h: Math.round(s.normalHoursTotal * 100) / 100,
    加班时长合计h: Math.round(s.overtimeHoursTotal * 100) / 100,
    按次金额: s.byCountAmount,
    按时金额: s.byHourAmount,
    提成合计: s.totalAmount,
  })
  return rows
}

describe('验收 #5：报表与 Excel 数字一致', () => {
  it('双 Sheet 结构正确', () => {
    const report = buildMonthlyReport({ monthKey })
    const workTypeMap = new Map(listWorkTypes().map((w) => [w.id, w]))
    const employeeNameMap = new Map(listEngineers().map((e) => [e.id, e.name]))

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(
      wb,
      XLSX.utils.json_to_sheet(buildDetailRows(report.approvedRecords, workTypeMap, employeeNameMap)),
      '明细表',
    )
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(buildSummaryRows(report)), '按人汇总表')

    // 真实写出再读回，验证 xlsx 往返无损
    const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
    const readBack = XLSX.read(buf, { type: 'buffer' })

    expect(readBack.SheetNames).toEqual(['明细表', '按人汇总表'])
  })

  it('明细表金额合计 = 报表提成总额', () => {
    const report = buildMonthlyReport({ monthKey })
    const workTypeMap = new Map(listWorkTypes().map((w) => [w.id, w]))
    const employeeNameMap = new Map(listEngineers().map((e) => [e.id, e.name]))

    const detailRows = buildDetailRows(report.approvedRecords, workTypeMap, employeeNameMap)
    const excelTotal =
      Math.round(detailRows.reduce((sum, r) => sum + Number(r['金额']), 0) * 100) / 100

    expect(excelTotal).toBe(report.summary.totalAmount)
  })

  it('按人汇总表合计行 = 报表汇总', () => {
    const report = buildMonthlyReport({ monthKey })
    const rows = buildSummaryRows(report)
    const totalRow = rows[rows.length - 1]

    expect(totalRow['员工']).toBe('合计')
    expect(totalRow['提成合计']).toBe(report.summary.totalAmount)
    expect(totalRow['次数合计']).toBe(report.summary.countTotal)
    expect(totalRow['按次金额']).toBe(report.summary.byCountAmount)
    expect(totalRow['按时金额']).toBe(report.summary.byHourAmount)
  })

  it('按人汇总表各员工金额之和 = 合计行', () => {
    const report = buildMonthlyReport({ monthKey })
    const rows = buildSummaryRows(report)
    const employeeRows = rows.slice(0, -1)
    const sum = Math.round(employeeRows.reduce((s, r) => s + Number(r['提成合计']), 0) * 100) / 100

    expect(sum).toBe(report.summary.totalAmount)
  })

  it('Excel 往返后数字不变（无精度损失）', () => {
    const report = buildMonthlyReport({ monthKey })
    const workTypeMap = new Map(listWorkTypes().map((w) => [w.id, w]))
    const employeeNameMap = new Map(listEngineers().map((e) => [e.id, e.name]))

    const detailRows = buildDetailRows(report.approvedRecords, workTypeMap, employeeNameMap)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(detailRows), '明细表')

    const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
    const readBack = XLSX.read(buf, { type: 'buffer' })
    const rows = XLSX.utils.sheet_to_json<Record<string, string | number>>(
      readBack.Sheets['明细表'],
    )

    expect(rows.length).toBe(detailRows.length)
    for (let i = 0; i < rows.length; i++) {
      expect(rows[i]['金额']).toBe(detailRows[i]['金额'])
      expect(rows[i]['单价']).toBe(detailRows[i]['单价'])
      expect(rows[i]['员工']).toBe(detailRows[i]['员工'])
      expect(rows[i]['日期']).toBe(detailRows[i]['日期'])
    }
  })

  it('明细表包含需求要求的全部字段（STA-04）', () => {
    const report = buildMonthlyReport({ monthKey })
    const workTypeMap = new Map(listWorkTypes().map((w) => [w.id, w]))
    const employeeNameMap = new Map(listEngineers().map((e) => [e.id, e.name]))
    const rows = buildDetailRows(report.approvedRecords, workTypeMap, employeeNameMap)

    const required = [
      '员工',
      '日期',
      '工作类型',
      '计费模式',
      '开始时间',
      '结束时间',
      '正常时长h',
      '加班时长h',
      '地点',
      '定位',
      '工作内容摘要',
      '照片链接',
      '状态',
      '是否补录',
      '单价',
      '金额',
    ]
    const keys = Object.keys(rows[0])
    for (const field of required) {
      expect(keys).toContain(field)
    }
  })

  it('按人汇总表包含需求要求的全部字段（STA-04）', () => {
    const report = buildMonthlyReport({ monthKey })
    const rows = buildSummaryRows(report)
    const required = [
      '员工',
      '次数合计',
      '正常时长合计h',
      '加班时长合计h',
      '按次金额',
      '按时金额',
      '提成合计',
    ]
    const keys = Object.keys(rows[0])
    for (const field of required) {
      expect(keys).toContain(field)
    }
  })

  it('中文表头在 xlsx 中不乱码', () => {
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(
      wb,
      XLSX.utils.json_to_sheet([{ 员工: '张三', 工作类型: '维修维护', 金额: 280 }]),
      '明细表',
    )
    const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
    const readBack = XLSX.read(buf, { type: 'buffer' })
    const rows = XLSX.utils.sheet_to_json<Record<string, string | number>>(
      readBack.Sheets['明细表'],
    )

    expect(rows[0]['员工']).toBe('张三')
    expect(rows[0]['工作类型']).toBe('维修维护')
    expect(rows[0]['金额']).toBe(280)
  })

  it('报表按员工下钻明细之和 = 该员工汇总金额', () => {
    const report = buildMonthlyReport({ monthKey })
    for (const item of report.byEmployee) {
      const drill = report.approvedRecords.filter((r) => r.employeeId === item.employeeId)
      const sum = Math.round(drill.reduce((s, r) => s + computeRecordSafe(r).total, 0) * 100) / 100
      expect(sum).toBe(item.summary.totalAmount)
    }
  })

  it('报表按类型汇总之和 = 报表总额', () => {
    const report = buildMonthlyReport({ monthKey })
    const sum =
      Math.round(report.byWorkType.reduce((s, t) => s + t.summary.totalAmount, 0) * 100) / 100
    expect(sum).toBe(report.summary.totalAmount)
  })

  it('员工姓名与工作类型名称在导出中可正确解析', () => {
    const report = buildMonthlyReport({ monthKey })
    for (const record of report.approvedRecords) {
      expect(getEmployeeName(record.employeeId)).not.toBe('未知员工')
      expect(getWorkType(record.workTypeId)).toBeTruthy()
    }
  })
})
