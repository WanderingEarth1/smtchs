import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '@/stores/app'

const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },

    /* ================= 员工端 ================= */
    {
      path: '/e',
      component: () => import('@/layouts/EmployeeLayout.vue'),
      meta: { role: 'EMPLOYEE' },
      children: [
        {
          path: 'home',
          name: 'employee-home',
          component: () => import('@/views/employee/HomeView.vue'),
          meta: { title: '首页', tab: 'home' },
        },
        {
          path: 'records',
          name: 'employee-records',
          component: () => import('@/views/employee/RecordsView.vue'),
          meta: { title: '我的记录', tab: 'records' },
        },
        {
          path: 'profile',
          name: 'employee-profile',
          component: () => import('@/views/employee/ProfileView.vue'),
          meta: { title: '我的', tab: 'profile' },
        },
      ],
    },
    {
      path: '/e/records/new',
      name: 'employee-record-new',
      component: () => import('@/views/employee/RecordFormView.vue'),
      meta: { role: 'EMPLOYEE', title: '新增记录' },
    },
    {
      path: '/e/records/:id/edit',
      name: 'employee-record-edit',
      component: () => import('@/views/employee/RecordFormView.vue'),
      meta: { role: 'EMPLOYEE', title: '修改记录' },
    },
    {
      path: '/e/records/:id',
      name: 'employee-record-detail',
      component: () => import('@/views/employee/RecordDetailView.vue'),
      meta: { role: 'EMPLOYEE', title: '记录详情' },
    },

    /* ================= 管理端 · PC（若依风格，全功能） ================= */
    {
      path: '/a/pc',
      component: () => import('@/layouts/AdminPcLayout.vue'),
      meta: { role: 'ADMIN', platform: 'pc' },
      children: [
        {
          path: 'dashboard',
          name: 'admin-pc-dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
          meta: { title: '经营概览' },
        },
        {
          path: 'review',
          name: 'admin-pc-review',
          component: () => import('@/views/admin/ReviewView.vue'),
          meta: { title: '记录审核' },
        },
        {
          path: 'reports',
          name: 'admin-pc-reports',
          component: () => import('@/views/admin/ReportsView.vue'),
          meta: { title: '月度报表' },
        },
        {
          path: 'config/employees',
          name: 'admin-pc-config-employees',
          component: () => import('@/views/admin/ConfigEmployeesView.vue'),
          meta: { title: '员工管理' },
        },
        {
          path: 'config/work-types',
          name: 'admin-pc-config-worktypes',
          component: () => import('@/views/admin/ConfigWorkTypesView.vue'),
          meta: { title: '工作类型' },
        },
        {
          path: 'config/prices',
          name: 'admin-pc-config-prices',
          component: () => import('@/views/admin/ConfigPricesView.vue'),
          meta: { title: '单价矩阵' },
        },
        {
          path: 'config/rules',
          name: 'admin-pc-config-rules',
          component: () => import('@/views/admin/ConfigRulesView.vue'),
          meta: { title: '计费规则' },
        },
      ],
    },

    /* ================= 管理端 · 移动（只读 + 审核 + 汇总） ================= */
    {
      path: '/a/m',
      component: () => import('@/layouts/AdminMobileLayout.vue'),
      meta: { role: 'ADMIN', platform: 'mobile' },
      children: [
        {
          path: 'summary',
          name: 'admin-m-summary',
          component: () => import('@/views/admin-mobile/SummaryView.vue'),
          meta: { title: '金额汇总', tab: 'summary' },
        },
        {
          path: 'records',
          name: 'admin-m-records',
          component: () => import('@/views/admin-mobile/RecordsView.vue'),
          meta: { title: '详细记录', tab: 'records' },
        },
        {
          path: 'review',
          name: 'admin-m-review',
          component: () => import('@/views/admin-mobile/ReviewView.vue'),
          meta: { title: '记录审核', tab: 'review' },
        },
      ],
    },

    /* ================= 入口与兜底 ================= */
    { path: '/', redirect: '/e/home' },
    // 旧路径兼容：/a/* 按设备类型自动分流
    { path: '/a', redirect: () => (isMobileDevice() ? '/a/m/summary' : '/a/pc/dashboard') },
    {
      path: '/a/dashboard',
      redirect: () => (isMobileDevice() ? '/a/m/summary' : '/a/pc/dashboard'),
    },
    { path: '/a/review', redirect: () => (isMobileDevice() ? '/a/m/review' : '/a/pc/review') },
    { path: '/a/reports', redirect: () => (isMobileDevice() ? '/a/m/summary' : '/a/pc/reports') },
    { path: '/a/config', redirect: '/a/pc/config/employees' },
    { path: '/a/config/:pathMatch(.*)*', redirect: '/a/pc/config/employees' },
    { path: '/:pathMatch(.*)*', redirect: '/e/home' },
  ],
})

/**
 * 设备类型判断：用于管理端入口自动分流
 *
 * 判定顺序（任一命中即为移动端）：
 * 1. UA 明确是手机/平板
 * 2. 视口宽度 < 768px（窄屏一律走移动端，避免 PC 布局被挤压）
 * 3. 触摸设备且视口 < 1024px
 */
export function isMobileDevice(): boolean {
  if (typeof window === 'undefined') return false

  const ua = navigator.userAgent
  if (/Android|iPhone|iPad|iPod|Mobile|HarmonyOS|MicroMessenger/i.test(ua)) return true

  const width = window.innerWidth
  if (width < 768) return true

  const isTouch = window.matchMedia?.('(pointer: coarse)').matches ?? false
  return isTouch && width < 1024
}

/** 路由守卫：登录态 + 角色路由（LOG-02） */
router.beforeEach((to) => {
  const { currentUser, isAdmin } = useAuth()
  const user = currentUser.value

  if (to.meta.public) {
    if (user) {
      return isAdmin.value ? adminHome() : '/e/home'
    }
    return true
  }

  if (!user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 角色不匹配时跳到对应首页
  if (to.meta.role === 'ADMIN' && user.role !== 'ADMIN') return '/e/home'
  if (to.meta.role === 'EMPLOYEE' && user.role !== 'EMPLOYEE') return adminHome()

  return true
})

/** 管理员首页：按设备分流 */
function adminHome(): string {
  return isMobileDevice() ? '/a/m/summary' : '/a/pc/dashboard'
}

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 服务记录与提成核算` : '服务记录与提成核算'
})

export default router
