/**
 * Excel 导出（STA-04）
 *
 * 产出两个 Sheet：
 *  ① 明细表：员工、日期、工作类型、计费模式、起止时间、正常时长、加班时长、地点、定位、
 *            工作内容摘要、照片链接、状态、是否补录、单价、金额
 *  ② 按人汇总表：员工、次数合计、正常时长合计、加班时长合计、按次金额、按时金额、提成合计
 *
 * 使用 SheetJS 生成标准 .xlsx，WPS / Office 均可正常打开，UTF-8 中文无乱码。
 */
import * as XLSX from 'xlsx'
import type { WorkRecord, WorkType } from '@/core/types'
import type { MonthlyReport } from '@/data/service'
import { computeRecordSafe } from '@/data/service'
import { formatMonthLabel } from '@/core/date'

export interface ExportContext {
  report: MonthlyReport
  records: WorkRecord[]
  workTypeMap: Map<string, WorkType>
  employeeNameMap: Map<string, string>
}

const STATUS_LABELS: Record<WorkRecord['status'], string> = {
  PENDING: '待确认',
  APPROVED: '已确认',
  REJECTED: '已驳回',
}

const MODE_LABELS: Record<WorkType['billingMode'], string> = {
  BY_COUNT: '按次',
  BY_HOUR: '按小时',
}

/** 截断摘要 */
function summarize(text: string, max = 60): string {
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

/** 定位文本 */
function geoText(record: WorkRecord): string {
  if (!record.geoCaptured || record.latitude === null || record.longitude === null) {
    return '未获取定位'
  }
  return `${record.latitude.toFixed(5)},${record.longitude.toFixed(5)}`
}

/** 照片链接（原型中为内联 dataURL 的缩略标记） */
function photoLinks(record: WorkRecord): string {
  if (record.photos.length === 0) return '无'
  return record.photos.map((p, i) => `凭证${i + 1}(${Math.round(p.size / 1024)}KB)`).join('；')
}

/** 生成明细行 */
function buildDetailRows(ctx: ExportContext): Record<string, string | number>[] {
  return ctx.records.map((record) => {
    const billing = computeRecordSafe(record)
    const wt = ctx.workTypeMap.get(record.workTypeId)
    const mode = wt?.billingMode ?? record.billingSnapshot.billingMode

    return {
      流水号: record.serialNo,
      员工: ctx.employeeNameMap.get(record.employeeId) ?? '未知员工',
      日期: record.serviceDate,
      工作类型: wt?.name ?? '未知类型',
      计费模式: MODE_LABELS[mode],
      开始时间: record.startTime,
      结束时间: record.endTime ?? '—',
      正常时长h: billing.billingMode === 'BY_HOUR' ? billing.normalHours : 0,
      加班时长h: billing.billingMode === 'BY_HOUR' ? billing.overtimeHours : 0,
      地点: record.location,
      定位: geoText(record),
      工作内容摘要: summarize(record.content),
      照片链接: photoLinks(record),
      状态: STATUS_LABELS[record.status],
      是否补录: record.isBackfill ? '是' : '否',
      单价: record.billingSnapshot.unitPrice,
      金额: billing.total,
    }
  })
}

/** 生成按人汇总行 */
function buildSummaryRows(ctx: ExportContext): Record<string, string | number>[] {
  const rows: Record<string, string | number>[] = []
  for (const item of ctx.report.byEmployee) {
    rows.push({
      员工: item.employeeName,
      次数合计: item.summary.countTotal,
      正常时长合计h: Math.round(item.summary.normalHoursTotal * 100) / 100,
      加班时长合计h: Math.round(item.summary.overtimeHoursTotal * 100) / 100,
      按次金额: item.summary.byCountAmount,
      按时金额: item.summary.byHourAmount,
      提成合计: item.summary.totalAmount,
    })
  }

  // 合计行
  const s = ctx.report.summary
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

/**
 * 导出月度提成报表
 * 触发浏览器下载
 */
export function exportMonthlyReport(ctx: ExportContext): { filename: string; rows: number } {
  const monthLabel = formatMonthLabel(ctx.report.monthKey)
  const wb = XLSX.utils.book_new()

  /* ---------- Sheet 1：明细表 ---------- */
  const detailRows = buildDetailRows(ctx)
  const wsDetail = XLSX.utils.json_to_sheet(detailRows)
  wsDetail['!cols'] = [
    { wch: 16 }, // 流水号
    { wch: 8 }, // 员工
    { wch: 12 }, // 日期
    { wch: 10 }, // 工作类型
    { wch: 9 }, // 计费模式
    { wch: 9 }, // 开始时间
    { wch: 9 }, // 结束时间
    { wch: 10 }, // 正常时长
    { wch: 10 }, // 加班时长
    { wch: 22 }, // 地点
    { wch: 20 }, // 定位
    { wch: 40 }, // 工作内容摘要
    { wch: 24 }, // 照片链接
    { wch: 8 }, // 状态
    { wch: 8 }, // 是否补录
    { wch: 8 }, // 单价
    { wch: 10 }, // 金额
  ]
  wsDetail['!freeze'] = { xSplit: 0, ySplit: 1 }
  XLSX.utils.book_append_sheet(wb, wsDetail, '明细表')

  /* ---------- Sheet 2：按人汇总表 ---------- */
  const summaryRows = buildSummaryRows(ctx)
  const wsSummary = XLSX.utils.json_to_sheet(summaryRows)
  wsSummary['!cols'] = [
    { wch: 10 },
    { wch: 10 },
    { wch: 14 },
    { wch: 14 },
    { wch: 12 },
    { wch: 12 },
    { wch: 12 },
  ]
  XLSX.utils.book_append_sheet(wb, wsSummary, '按人汇总表')

  /* ---------- 导出 ---------- */
  const filename = `提成报表_${monthLabel.replace(/年|月/g, '-').replace(/-$/, '')}_${ctx.report.monthKey}.xlsx`
  XLSX.writeFile(wb, filename, { compression: true, bookType: 'xlsx' })

  return { filename, rows: detailRows.length }
}

/** 仅导出明细（用于员工端不提供导出，管理端筛选明细时使用） */
export function exportDetailOnly(rows: Record<string, string | number>[], filename: string): void {
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.json_to_sheet(rows)
  const keys = Object.keys(rows[0] ?? {})
  ws['!cols'] = keys.map((k) => ({ wch: Math.max(10, k.length * 2 + 6) }))
  XLSX.utils.book_append_sheet(wb, ws, '明细表')
  XLSX.writeFile(wb, filename, { compression: true, bookType: 'xlsx' })
}
