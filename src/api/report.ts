/**
 * 报表接口
 */

import { http, BASE_URL } from './request'
import type { BillingMode, WorkRecord } from '@/core/types'
import type { MonthlySummary } from '@/core/billing'

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

export function getMonthlyReport(params: {
  month: string
  employeeId?: string
  workTypeId?: string
}): Promise<MonthlyReport> {
  return http.get<MonthlyReport>('/report/monthly', params)
}

export function getMySummary(month: string): Promise<MonthlyReport> {
  return http.get<MonthlyReport>('/report/mine', { month })
}

/** 导出 Excel（触发浏览器下载） */
export async function exportMonthlyReport(month: string): Promise<void> {
  const token = localStorage.getItem('smtchs.token')
  const response = await fetch(`${BASE_URL}/report/export?month=${encodeURIComponent(month)}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error('导出失败')
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `月度报表_${month}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}
