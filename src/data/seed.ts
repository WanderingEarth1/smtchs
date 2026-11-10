/**
 * 演示种子数据
 *
 * 覆盖需求文档验收要点所需的全部场景：
 * - 按次 / 按小时两种计费模式
 * - 正常时段 / 加班时段 / 跨 0 点
 * - 待确认 / 已确认 / 已驳回 三种状态
 * - 补录标记
 * - 定位成功 / 未获取定位
 * - 停用员工
 */
import type {
  Employee,
  WorkType,
  PriceRule,
  SysConfig,
  WorkRecord,
  PhotoRef,
  BillingSnapshot,
  BillingMode,
} from '@/core/types'
import { hashPassword } from '@/core/hash'
import { currentMonthKey, pad2, shiftMonth, toDateKey } from '@/core/date'
import {
  loadEmployees,
  loadPriceRules,
  loadRecords,
  loadSysConfig,
  loadWorkTypes,
  markSeeded,
  isSeeded,
  saveEmployees,
  savePriceRules,
  saveRecords,
  saveSysConfig,
  saveWorkTypes,
} from './storage'

/** 生成一张占位凭证图（SVG dataURL，模拟现场照片） */
export function makePlaceholderPhoto(seed: string, label: string): PhotoRef {
  const hue = [...seed].reduce((a, c) => a + c.charCodeAt(0), 0) % 360
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" viewBox="0 0 480 360">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="hsl(${hue} 42% 34%)"/>
      <stop offset="100%" stop-color="hsl(${(hue + 42) % 360} 38% 18%)"/>
    </linearGradient>
  </defs>
  <rect width="480" height="360" fill="url(#g)"/>
  <g fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="2">
    <path d="M0 250 L120 190 L240 240 L360 170 L480 220"/>
    <path d="M0 300 L140 250 L280 292 L420 232 L480 262"/>
  </g>
  <g fill="rgba(255,255,255,0.9)" font-family="system-ui, sans-serif">
    <text x="24" y="48" font-size="20" font-weight="600">现场凭证</text>
    <text x="24" y="80" font-size="15" fill="rgba(255,255,255,0.72)">${label}</text>
  </g>
  <g transform="translate(400,300)" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="2.5">
    <circle r="18"/><circle r="7"/>
  </g>
</svg>`
  const dataUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
  return {
    id: `ph_${seed}`,
    dataUrl,
    name: `${label}.jpg`,
    size: Math.round(dataUrl.length * 0.75),
  }
}

/** 生成计费快照 */
function snapshot(
  billingMode: BillingMode,
  unitPrice: number,
  cfg: SysConfig,
): BillingSnapshot {
  return {
    billingMode,
    unitPrice,
    overtimeStart: cfg.overtimeStart,
    overtimeMultiplier: cfg.overtimeMultiplier,
    roundingRule: cfg.roundingRule,
  }
}

/** 生成 ISO 时间 */
function isoAt(dateKey: string, time: string): string {
  return new Date(`${dateKey}T${time}:00`).toISOString()
}

/**
 * 生成「提交时间」：取服务结束时刻，但不晚于当前时间
 * 避免出现未来时间的提交记录
 */
function submittedAtOf(dateKey: string, time: string): string {
  const candidate = new Date(`${dateKey}T${time}:00`).getTime()
  const now = Date.now()
  return new Date(Math.min(candidate, now - 60_000)).toISOString()
}

/** 生成某月内的日期键 */
function dayInMonth(monthKey: string, day: number): string {
  return `${monthKey}-${pad2(day)}`
}

/**
 * 生成「当月 N 天前」的日期键
 * - 始终落在当前月份内，保证演示数据的当月视图有内容
 * - 回退超出当月月初时钳制到 1 号，且绝不超过今天（避免未来日期）
 */
function daysAgoInMonth(monthKey: string, daysAgo: number): string {
  const today = new Date()
  const todayDay = today.getDate()
  const day = Math.max(1, todayDay - daysAgo)
  return `${monthKey}-${pad2(day)}`
}

export const DEMO_ACCOUNTS = {
  admin: { account: 'admin', password: 'admin123' },
  employee: { account: 'zhangsan', password: '123456' },
  employee2: { account: 'lisi', password: '123456' },
} as const

/**
 * 初始化种子数据（幂等：已初始化则跳过）
 */
export async function seedIfNeeded(force = false): Promise<void> {
  if (!force && isSeeded()) return

  const now = new Date()
  const monthKey = currentMonthKey()
  const prevMonthKey = shiftMonth(monthKey, -1)

  /* ---------------- 系统配置 ---------------- */
  const sysConfig: SysConfig = {
    overtimeStart: '18:00',
    overtimeMultiplier: 2,
    roundingRule: 'EXACT',
  }

  /* ---------------- 员工 ---------------- */
  const [adminHash, zhangHash, liHash, wangHash, zhaoHash] = await Promise.all([
    hashPassword(DEMO_ACCOUNTS.admin.password),
    hashPassword(DEMO_ACCOUNTS.employee.password),
    hashPassword(DEMO_ACCOUNTS.employee2.password),
    hashPassword('123456'),
    hashPassword('123456'),
  ])

  const employees: Employee[] = [
    {
      id: 'emp_admin',
      name: '王管理',
      account: DEMO_ACCOUNTS.admin.account,
      passwordHash: adminHash,
      status: 'ACTIVE',
      role: 'ADMIN',
      createdAt: isoAt(prevMonthKey, '09:00'),
      failedAttempts: 0,
      lockedUntil: null,
    },
    {
      id: 'emp_zhangsan',
      name: '张三',
      account: DEMO_ACCOUNTS.employee.account,
      passwordHash: zhangHash,
      status: 'ACTIVE',
      role: 'EMPLOYEE',
      createdAt: isoAt(prevMonthKey, '09:10'),
      failedAttempts: 0,
      lockedUntil: null,
    },
    {
      id: 'emp_lisi',
      name: '李四',
      account: DEMO_ACCOUNTS.employee2.account,
      passwordHash: liHash,
      status: 'ACTIVE',
      role: 'EMPLOYEE',
      createdAt: isoAt(prevMonthKey, '09:12'),
      failedAttempts: 0,
      lockedUntil: null,
    },
    {
      id: 'emp_wangwu',
      name: '王五',
      account: 'wangwu',
      passwordHash: wangHash,
      status: 'ACTIVE',
      role: 'EMPLOYEE',
      createdAt: isoAt(prevMonthKey, '09:15'),
      failedAttempts: 0,
      lockedUntil: null,
    },
    {
      id: 'emp_zhaoliu',
      name: '赵六',
      account: 'zhaoliu',
      passwordHash: zhaoHash,
      status: 'DISABLED',
      role: 'EMPLOYEE',
      createdAt: isoAt(prevMonthKey, '09:18'),
      failedAttempts: 0,
      lockedUntil: null,
    },
  ]

  /* ---------------- 工作类型 ---------------- */
  const workTypes: WorkType[] = [
    { id: 'wt_delivery', name: '送货上门', billingMode: 'BY_COUNT', enabled: true, sort: 1 },
    { id: 'wt_install', name: '安装调试', billingMode: 'BY_HOUR', enabled: true, sort: 2 },
    { id: 'wt_repair', name: '维修维护', billingMode: 'BY_HOUR', enabled: true, sort: 3 },
    { id: 'wt_inspect', name: '巡检保养', billingMode: 'BY_COUNT', enabled: true, sort: 4 },
    { id: 'wt_training', name: '客户培训', billingMode: 'BY_HOUR', enabled: false, sort: 5 },
  ]

  /* ---------------- 单价矩阵 ---------------- */
  const effectiveAt = isoAt(prevMonthKey, '00:00')
  const priceMatrix: Record<string, Record<string, number>> = {
    emp_zhangsan: { wt_delivery: 50, wt_install: 90, wt_repair: 80, wt_inspect: 40, wt_training: 100 },
    emp_lisi: { wt_delivery: 50, wt_install: 85, wt_repair: 75, wt_inspect: 40, wt_training: 95 },
    emp_wangwu: { wt_delivery: 45, wt_install: 80, wt_repair: 70, wt_inspect: 35, wt_training: 90 },
    emp_zhaoliu: { wt_delivery: 45, wt_install: 80, wt_repair: 70, wt_inspect: 35, wt_training: 90 },
  }

  const priceRules: PriceRule[] = []
  for (const [employeeId, byType] of Object.entries(priceMatrix)) {
    for (const [workTypeId, price] of Object.entries(byType)) {
      priceRules.push({
        id: `pr_${employeeId}_${workTypeId}`,
        employeeId,
        workTypeId,
        price,
        effectiveAt,
      })
    }
  }

  /* ---------------- 服务记录 ---------------- */
  const records: WorkRecord[] = []
  let serial = 1

  const pushRecord = (input: {
    employeeId: string
    workTypeId: string
    date: string
    start: string
    end?: string | null
    location: string
    content: string
    remark?: string
    status: WorkRecord['status']
    isBackfill?: boolean
    geo?: boolean
    rejectReason?: string | null
    photos?: number
    submittedAt?: string
    confirmedAt?: string | null
    rejectedAt?: string | null
  }) => {
    const wt = workTypes.find((w) => w.id === input.workTypeId)!
    const price = priceMatrix[input.employeeId]?.[input.workTypeId] ?? 0
    const photoCount = input.photos ?? 1
    const photos: PhotoRef[] = Array.from({ length: photoCount }, (_, i) =>
      makePlaceholderPhoto(
        `${input.employeeId}_${input.date}_${input.start}_${i}`,
        `${input.location} · ${wt.name}`,
      ),
    )
    const geo = input.geo !== false
    records.push({
      id: `rec_${String(serial).padStart(4, '0')}`,
      serialNo: `SR${monthKey.replace('-', '')}${String(serial).padStart(4, '0')}`,
      employeeId: input.employeeId,
      workTypeId: input.workTypeId,
      serviceDate: input.date,
      startTime: input.start,
      endTime: input.end ?? null,
      location: input.location,
      latitude: geo ? 30.2741 + Math.random() * 0.08 : null,
      longitude: geo ? 120.1551 + Math.random() * 0.08 : null,
      geoCaptured: geo,
      content: input.content,
      remark: input.remark ?? '',
      photos,
      status: input.status,
      isBackfill: input.isBackfill ?? false,
      rejectReason: input.rejectReason ?? null,
      submittedAt: input.submittedAt ?? submittedAtOf(input.date, input.end ?? input.start),
      confirmedAt: input.confirmedAt ?? null,
      rejectedAt: input.rejectedAt ?? null,
      billingSnapshot: snapshot(wt.billingMode, price, sysConfig),
    })
    serial++
  }

  // ---- 张三：本月已确认记录（覆盖按次 + 按小时 + 加班 + 取整） ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 6),
    start: '09:00',
    location: '滨江花园 3 栋 2 单元',
    content: '配送净水器 1 台，客户签收确认',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 6), '20:10'),
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 6),
    start: '20:00',
    location: '锦绣江南 12 栋',
    content: '夜间加急配送滤芯套装',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 6), '20:12'),
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_repair',
    date: daysAgoInMonth(monthKey, 4),
    start: '17:30',
    end: '19:30',
    location: '阳光里小区 7 栋 1 单元 502',
    content: '热水器不加热，更换加热管并测试',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 4), '21:00'),
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_install',
    date: daysAgoInMonth(monthKey, 3),
    start: '09:00',
    end: '12:00',
    location: '万科城市之光 A 座 1801',
    content: '新机安装调试，含管路改造',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 3), '19:30'),
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_repair',
    date: daysAgoInMonth(monthKey, 2),
    start: '22:00',
    end: '01:00',
    location: '城西工业园 B 区 4 号厂房',
    content: '夜间抢修供水泵，跨 0 点连续作业',
    remark: '客户要求当晚必须恢复供水',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 1), '09:20'),
  })

  // ---- 张三：待确认 ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_inspect',
    date: daysAgoInMonth(monthKey, 1),
    start: '10:00',
    location: '翠苑一区 5 栋',
    content: '季度巡检，滤芯状态正常',
    status: 'PENDING',
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_repair',
    date: daysAgoInMonth(monthKey, 0),
    start: '17:00',
    end: '18:40',
    location: '和平小区 2 栋 301',
    content: '更换水龙头阀芯，测试无渗漏',
    status: 'PENDING',
  })

  // ---- 张三：补录（历史日期） ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 8),
    start: '14:00',
    location: '西溪诚园 9 幢',
    content: '补录：月初配送软水机 1 台',
    status: 'PENDING',
    isBackfill: true,
    submittedAt: isoAt(daysAgoInMonth(monthKey, 1), '21:30'),
  })

  // ---- 张三：已驳回 ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_install',
    date: daysAgoInMonth(monthKey, 5),
    start: '13:00',
    end: '15:00',
    location: '钱江新城 2 号楼',
    content: '安装净水设备',
    status: 'REJECTED',
    rejectReason: '照片不清晰，无法确认安装完成情况，请重新拍摄后提交',
    rejectedAt: isoAt(daysAgoInMonth(monthKey, 5), '18:00'),
  })

  // ---- 张三：定位未获取 ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 0),
    start: '11:00',
    location: '古荡新村 4 栋',
    content: '配送滤芯 2 支',
    status: 'PENDING',
    geo: false,
  })

  // ---- 李四：本月记录 ----
  pushRecord({
    employeeId: 'emp_lisi',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 7),
    start: '09:30',
    location: '文三路 90 号 3 楼',
    content: '配送办公净水设备 2 台',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 7), '19:00'),
  })
  pushRecord({
    employeeId: 'emp_lisi',
    workTypeId: 'wt_repair',
    date: daysAgoInMonth(monthKey, 4),
    start: '14:00',
    end: '17:30',
    location: '拱墅区香积寺路 88 号',
    content: '商用净水机滤芯更换及管路清洗',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 4), '20:30'),
  })
  pushRecord({
    employeeId: 'emp_lisi',
    workTypeId: 'wt_install',
    date: daysAgoInMonth(monthKey, 3),
    start: '17:00',
    end: '19:40',
    location: '西湖区教工路 21 号',
    content: '新装软水机并调试',
    status: 'PENDING',
  })
  pushRecord({
    employeeId: 'emp_lisi',
    workTypeId: 'wt_delivery',
    date: daysAgoInMonth(monthKey, 2),
    start: '15:00',
    location: '上城区解放路 156 号',
    content: '配送滤芯套装',
    status: 'PENDING',
    isBackfill: true,
    submittedAt: isoAt(daysAgoInMonth(monthKey, 0), '08:30'),
  })

  // ---- 王五：本月记录 ----
  pushRecord({
    employeeId: 'emp_wangwu',
    workTypeId: 'wt_repair',
    date: daysAgoInMonth(monthKey, 5),
    start: '10:00',
    end: '12:30',
    location: '余杭区未来科技城 5 号楼',
    content: '净水主机漏水维修',
    status: 'APPROVED',
    confirmedAt: isoAt(daysAgoInMonth(monthKey, 5), '18:30'),
  })
  pushRecord({
    employeeId: 'emp_wangwu',
    workTypeId: 'wt_inspect',
    date: daysAgoInMonth(monthKey, 1),
    start: '09:00',
    location: '临平区南苑街道 12 号',
    content: '季度巡检',
    status: 'PENDING',
  })

  // ---- 上月历史数据（验证按月筛选与历史统计） ----
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_delivery',
    date: dayInMonth(prevMonthKey, 12),
    start: '10:00',
    location: '滨江花园 1 栋',
    content: '上月配送记录',
    status: 'APPROVED',
    confirmedAt: isoAt(dayInMonth(prevMonthKey, 12), '19:00'),
  })
  pushRecord({
    employeeId: 'emp_zhangsan',
    workTypeId: 'wt_repair',
    date: dayInMonth(prevMonthKey, 18),
    start: '16:00',
    end: '19:00',
    location: '阳光里小区 3 栋',
    content: '上月维修记录',
    status: 'APPROVED',
    confirmedAt: isoAt(dayInMonth(prevMonthKey, 18), '20:00'),
  })

  /* ---------------- 落库 ---------------- */
  saveSysConfig(sysConfig)
  saveEmployees(employees)
  saveWorkTypes(workTypes)
  savePriceRules(priceRules)
  saveRecords(records)
  markSeeded()

  // 触发一次读取，确保数据可解析
  void loadEmployees()
  void loadWorkTypes()
  void loadPriceRules()
  void loadRecords()
  void loadSysConfig()
  void now
  void toDateKey
}
