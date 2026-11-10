/**
 * 单价矩阵接口
 * 后端：SmtPriceController（/smtchs/price）
 */
import { http } from './request'
import type { PriceRule } from '@/core/types'

/** 矩阵查询（返回全部单价规则） */
export async function getPriceMatrix(): Promise<PriceRule[]> {
  const list = await http.get<PriceRule[]>('/price/matrix')
  return list || []
}

/** 设置单价 */
export async function setUnitPrice(
  employeeId: string,
  workTypeId: string,
  price: number,
): Promise<void> {
  await http.put('/price', { employeeId, workTypeId, price })
}

/** 按行复制 */
export async function copyPriceRow(
  fromEmployeeId: string,
  toEmployeeIds: string[],
): Promise<number> {
  const count = await http.post<number>('/price/copy', { fromEmployeeId, toEmployeeIds })
  return Number(count) || 0
}
