/**
 * 单价矩阵接口
 */

import { http } from './request'
import type { PriceRule } from '@/core/types'

export function getPriceMatrix(): Promise<PriceRule[]> {
  return http.get<PriceRule[]>('/prices')
}

export function setUnitPrice(
  employeeId: string,
  workTypeId: string,
  price: number,
): Promise<PriceRule> {
  return http.put<PriceRule>('/prices', { employeeId, workTypeId, price })
}

export function copyPriceRow(fromEmployeeId: string, toEmployeeIds: string[]): Promise<number> {
  return http.post<number>('/prices/copy', { fromEmployeeId, toEmployeeIds })
}
