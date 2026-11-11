/**
 * 单价矩阵接口
 * 后端：SmtPriceController（/smtchs/price）
 */
import { http } from './request'
import type { PriceRule } from '@/core/types'

/** 后端单价 DTO */
export interface PriceDto {
  id: string | number
  employeeId: string | number
  workTypeId: string | number
  price: number | string
  effectiveAt?: string
}

/** 归一化：id 全部转 string，price 转 number */
export function normalizePrice(dto: PriceDto): PriceRule {
  return {
    id: String(dto.id),
    employeeId: String(dto.employeeId),
    workTypeId: String(dto.workTypeId),
    price: Number(dto.price) || 0,
    effectiveAt: dto.effectiveAt ?? '',
  } as PriceRule
}

/** 矩阵查询（返回全部单价规则） */
export async function getPriceMatrix(): Promise<PriceRule[]> {
  const list = await http.get<PriceDto[]>('/price/matrix')
  return (list || []).map(normalizePrice)
}

/**
 * 查询本人单价（员工用，不需要管理权限）
 *
 * 员工提交记录时需要金额预估，但没有「查看全部单价」权限，
 * 后端提供只返回本人单价的接口。
 */
export async function getMyPriceMatrix(): Promise<PriceRule[]> {
  const list = await http.get<PriceDto[]>('/price/my')
  return (list || []).map(normalizePrice)
}

/** 设置单价 */
export async function setUnitPrice(
  employeeId: string,
  workTypeId: string,
  price: number,
): Promise<void> {
  await http.put('/price', {
    employeeId: Number(employeeId) || employeeId,
    workTypeId: Number(workTypeId) || workTypeId,
    price,
  })
}

/** 按行复制 */
export async function copyPriceRow(
  fromEmployeeId: string,
  toEmployeeIds: string[],
): Promise<number> {
  const count = await http.post<number>('/price/copy', {
    fromEmployeeId: Number(fromEmployeeId) || fromEmployeeId,
    toEmployeeIds: toEmployeeIds.map((v) => Number(v) || v),
  })
  return Number(count) || 0
}
