/**
 * 全局状态：登录会话 / 主题 / 数据版本号
 *
 * 采用轻量响应式 store（不引入 Pinia，保持依赖精简）。
 */
import { computed, reactive, ref } from 'vue'
import type { Employee } from '@/core/types'
import { login as serviceLogin, logout as serviceLogout, restoreSession } from '@/data/service'

const currentUser = ref<Employee | null>(null)
const ready = ref(false)

/** 数据版本号：任意写操作后自增，用于驱动页面重新读取 */
const dataVersion = ref(0)

export function bumpDataVersion(): void {
  dataVersion.value++
}

export function useAuth() {
  const isLoggedIn = computed(() => currentUser.value !== null)
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
  const isEmployee = computed(() => currentUser.value?.role === 'EMPLOYEE')

  async function login(account: string, password: string): Promise<Employee> {
    const result = await serviceLogin(account, password)
    currentUser.value = result.employee
    bumpDataVersion()
    return result.employee
  }

  async function logout(): Promise<void> {
    await serviceLogout()
    currentUser.value = null
    bumpDataVersion()
  }

  /** 应用启动时恢复会话（LOG-03 无感进入） */
  async function restore(): Promise<void> {
    try {
      currentUser.value = await restoreSession()
    } catch {
      currentUser.value = null
    } finally {
      ready.value = true
    }
  }

  /** 刷新当前用户信息（改密、改名后） */
  function refreshUser(employee: Employee | null): void {
    if (employee) currentUser.value = employee
  }

  return {
    currentUser,
    ready,
    dataVersion,
    bumpDataVersion,
    isLoggedIn,
    isAdmin,
    isEmployee,
    login,
    logout,
    restore,
    refreshUser,
  }
}

/* ---------------- 主题（模块级单例，保证各组件共享同一状态） ---------------- */
const theme = ref<'light' | 'dark'>('light')

export function useTheme() {
  function apply(next: 'light' | 'dark'): void {
    theme.value = next
    document.documentElement.setAttribute('data-theme', next)
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', next === 'dark' ? '#0b1220' : '#0F172A')
  }

  function toggle(): void {
    const next = theme.value === 'dark' ? 'light' : 'dark'
    apply(next)
    localStorage.setItem('smtchs.v1.theme', next)
  }

  function init(): void {
    const saved = localStorage.getItem('smtchs.v1.theme')
    if (saved === 'dark' || saved === 'light') {
      apply(saved)
      return
    }
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
    apply(prefersDark ? 'dark' : 'light')
  }

  return { theme, apply, toggle, init }
}

/* ---------------- 全局 UI 状态 ---------------- */
const globalBusy = reactive({ count: 0 })

export function useBusy() {
  const busy = computed(() => globalBusy.count > 0)
  async function withBusy<T>(fn: () => Promise<T>): Promise<T> {
    globalBusy.count++
    try {
      return await fn()
    } finally {
      globalBusy.count--
    }
  }
  return { busy, withBusy }
}
