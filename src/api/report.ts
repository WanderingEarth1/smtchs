/**
 * 报表接口
 * 后端：SmtReportController（/smtchs/report）
 */
import { http, BASE_URL } from './request'
import type { BillingMode, WorkRecord } from '@/core/types'
import type { MonthlySummary } from '@/core/billing'
import { normalizeRecord, type RecordDto } from './record'

/** 后端报表 DTO */
export interface ReportDto {
  monthKey: string
  summary: MonthlySummary
  byEmployee: Array<{ employeeId: string | number; employeeName: string; summary: MonthlySummary }>
  byWorkType: Array<{
    workTypeId: string | number
    workTypeName: string
    billingMode: string
    summary: MonthlySummary
  }>
  approvedRecords: RecordDto[]
  pendingCount: number
  rejectedCount: number
}

/** 前端报表模型 */
export interface MonthlyReport {
  monthKey: string
  summary: MonthlySummary
  byEmployee: Array<{ employeeId: string; employeeName: string; summary: MonthlySummary }>
  byWorkType: Array<{
    workTypeId: string
    workTypeName: string
    billingMode: BillingMode
    summary: MonthlySummary
  }>
  approvedRecords: WorkRecord[]
  pendingCount: number
  rejectedCount: number
}

function normalizeReport(dto: ReportDto): MonthlyReport {
  return {
    monthKey: dto.monthKey,
    summary: dto.summary,
    byEmployee: (dto.byEmployee || []).map((e) => ({
      employeeId: String(e.employeeId),
      employeeName: e.employeeName,
      summary: e.summary,
    })),
    byWorkType: (dto.byWorkType || []).map((t) => ({
      workTypeId: String(t.workTypeId),
      workTypeName: t.workTypeName,
      billingMode: t.billingMode as BillingMode,
      summary: t.summary,
    })),
    approvedRecords: (dto.approvedRecords || []).map(normalizeRecord),
    pendingCount: dto.pendingCount ?? 0,
    rejectedCount: dto.rejectedCount ?? 0,
  }
}

/** 月度报表 */
export async function getMonthlyReport(params: {
  month: string
  employeeId?: string
  workTypeId?: string
}): Promise<MonthlyReport> {
  const dto = await http.get<ReportDto>('/report/monthly', params)
  return normalizeReport(dto)
}

/** 本人当月汇总 */
export async function getMySummary(month: string): Promise<MonthlyReport> {
  const dto = await http.get<ReportDto>('/report/my-summary', { month })
  return normalizeReport(dto)
}

/** 导出 Excel（触发浏览器下载） */
export async function exportMonthlyReport(month: string): Promise<void> {
  const token = localStorage.getItem('smtchs.token')
  const response = await fetch(
    `${BASE_URL}/report/export?month=${encodeURIComponent(month)}`,
    {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    },
  )
  if (!response.ok) throw new Error('导出失败')
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `月度报表_${month}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}
