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
          path: 'profile',
          name: 'admin-m-profile',
          component: () => import('@/views/admin-mobile/ProfileView.vue'),
          meta: { title: '我的', tab: 'profile' },
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
    // 旧路径兼容：管理端 PC 已由若依前端独立提供，此处统一指向移动端
    { path: '/a', redirect: '/a/m/summary' },
    { path: '/a/dashboard', redirect: '/a/m/summary' },
    { path: '/a/review', redirect: '/a/m/review' },
    { path: '/a/reports', redirect: '/a/m/summary' },
    { path: '/a/config', redirect: '/a/m/summary' },
    { path: '/a/config/:pathMatch(.*)*', redirect: '/a/m/summary' },
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

/**
 * 管理员首页
 *
 * 管理端 PC 由若依前端（frontend-admin）独立提供，
 * 本工程只承载员工端 H5 与管理端移动端，故统一指向移动端汇总页。
 */
function adminHome(): string {
  return '/a/m/summary'
}

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 服务记录与提成核算` : '服务记录与提成核算'
})

export default router
