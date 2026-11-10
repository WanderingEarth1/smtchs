/**
 * 日期与时间工具
 */

/** 补零 */
export function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** Date → YYYY-MM-DD（本地时区） */
export function toDateKey(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** Date → HH:mm */
export function toTimeKey(d: Date): string {
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/** 今天的日期键 */
export function todayKey(): string {
  return toDateKey(new Date())
}

/** 当前月份键 YYYY-MM */
export function currentMonthKey(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}

/** 日期键 → 月份键 */
export function monthOf(dateKey: string): string {
  return dateKey.slice(0, 7)
}

/** 月份键 → 中文展示 2026年11月 */
export function formatMonthLabel(monthKey: string): string {
  const [y, m] = monthKey.split('-')
  return `${y}年${Number(m)}月`
}

/** 日期键 → 中文展示 11月5日 */
export function formatDateLabel(dateKey: string): string {
  const [, m, d] = dateKey.split('-')
  return `${Number(m)}月${Number(d)}日`
}

/** 日期键 → 星期几 */
export function weekdayLabel(dateKey: string): string {
  const [y, m, d] = dateKey.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
}

/** 月份偏移：monthKey 加减 n 个月 */
export function shiftMonth(monthKey: string, delta: number): string {
  const [y, m] = monthKey.split('-').map(Number)
  const date = new Date(y, m - 1 + delta, 1)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`
}

/** 该月的起止日期键 */
export function monthRange(monthKey: string): { start: string; end: string } {
  const [y, m] = monthKey.split('-').map(Number)
  const start = `${y}-${pad2(m)}-01`
  const lastDay = new Date(y, m, 0).getDate()
  const end = `${y}-${pad2(m)}-${pad2(lastDay)}`
  return { start, end }
}

/** 是否为未来日期 */
export function isFutureDate(dateKey: string): boolean {
  return dateKey > todayKey()
}

/** ISO 时间 → 本地展示 MM-DD HH:mm */
export function formatDateTime(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/** ISO 时间 → 本地展示 YYYY-MM-DD HH:mm */
export function formatFullDateTime(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return `${toDateKey(d)} ${toTimeKey(d)}`
}

/** 相对时间描述 */
export function relativeTime(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso).getTime()
  if (Number.isNaN(d)) return '—'
  const diff = Date.now() - d
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  return formatFullDateTime(iso)
}

/** 小时数 → 可读时长 1.5h / 30min */
export function formatHours(hours: number): string {
  if (!hours) return '0h'
  const totalMin = Math.round(hours * 60)
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  if (h === 0) return `${m}min`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

/** 金额格式化：1234.5 → 1,234.50 */
export function formatMoney(value: number): string {
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 金额格式化（整数省略小数）：1234 → 1,234 */
export function formatMoneyShort(value: number): string {
  const rounded = Math.round(value * 100) / 100
  return rounded.toLocaleString('zh-CN', {
    minimumFractionDigits: Number.isInteger(rounded) ? 0 : 2,
    maximumFractionDigits: 2,
  })
}
