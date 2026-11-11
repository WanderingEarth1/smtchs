<script setup lang="ts">
/**
 * 管理端移动 · 个人中心
 *
 * 交互要点：
 * - 顶部账号卡：头像首字 + 姓名 + 账号 + 角色标签
 * - 本月概览：提成总额 / 待审核 / 已确认（复用汇总接口）
 * - 功能列表：修改密码、主题切换、关于
 * - 退出登录：二次确认，避免误触
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppSheet from '@/components/AppSheet.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import { BizError, buildMonthlyReport, changeOwnPassword } from '@/data/service'
import type { MonthlyReport } from '@/api/report'
import { currentMonthKey, formatMoneyShort, formatMonthLabel } from '@/core/date'

const router = useRouter()
const { currentUser, logout, dataVersion } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const monthKey = currentMonthKey()

const emptyReport: MonthlyReport = {
  monthKey,
  summary: {
    recordCount: 0,
    countTotal: 0,
    normalHoursTotal: 0,
    overtimeHoursTotal: 0,
    byCountAmount: 0,
    byHourAmount: 0,
    totalAmount: 0,
  },
  byEmployee: [],
  byWorkType: [],
  approvedRecords: [],
  pendingCount: 0,
  rejectedCount: 0,
}

const report = ref<MonthlyReport>(emptyReport)

const roleLabel = computed(() =>
  currentUser.value?.role === 'ADMIN' ? '管理员' : '工程师',
)

const avatarText = computed(() => currentUser.value?.name?.slice(0, 1) ?? '?')

/** 加载本月概览 */
async function loadSummary(): Promise<void> {
  try {
    report.value = await buildMonthlyReport({ monthKey })
  } catch {
    report.value = emptyReport
  }
}

watch(dataVersion, () => {
  void loadSummary()
})

onMounted(() => {
  void loadSummary()
})

/* ---------------- 修改密码 ---------------- */
const pwdOpen = ref(false)
const pwdOld = ref('')
const pwdNew = ref('')
const pwdConfirm = ref('')
const pwdError = ref('')
const pwdSubmitting = ref(false)

function openPwd(): void {
  pwdOld.value = ''
  pwdNew.value = ''
  pwdConfirm.value = ''
  pwdError.value = ''
  pwdOpen.value = true
}

async function submitPwd(): Promise<void> {
  pwdError.value = ''
  if (!pwdOld.value) {
    pwdError.value = '请输入当前密码'
    return
  }
  if (pwdNew.value.length < 6) {
    pwdError.value = '新密码至少 6 位'
    return
  }
  if (pwdNew.value !== pwdConfirm.value) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }
  if (pwdNew.value === pwdOld.value) {
    pwdError.value = '新密码不能与当前密码相同'
    return
  }

  pwdSubmitting.value = true
  try {
    await changeOwnPassword(currentUser.value?.id ?? '', pwdOld.value, pwdNew.value)
    toast.success('密码已修改，请重新登录')
    pwdOpen.value = false
    await logout()
    void router.replace('/login')
  } catch (err) {
    pwdError.value = err instanceof BizError ? err.message : '修改失败'
  } finally {
    pwdSubmitting.value = false
  }
}

/* ---------------- 退出登录 ---------------- */
const logoutOpen = ref(false)

function openLogout(): void {
  logoutOpen.value = true
}

async function doLogout(): Promise<void> {
  logoutOpen.value = false
  await logout()
  toast.success('已退出登录')
  void router.replace('/login')
}

/* ---------------- 关于 ---------------- */
const aboutOpen = ref(false)
</script>

<template>
  <div class="m-profile">
    <header class="m-head">
      <div class="m-head__row">
        <div class="grow">
          <p class="t-eyebrow">Account</p>
          <h1 class="m-head__title">我的</h1>
        </div>
        <button
          type="button"
          class="icon-btn"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggle"
        >
          <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="18" />
        </button>
      </div>
    </header>

    <!-- 账号卡 -->
    <section class="acct-card">
      <div class="acct-card__avatar">{{ avatarText }}</div>
      <div class="acct-card__main">
        <p class="acct-card__name">{{ currentUser?.name }}</p>
        <p class="acct-card__account mono">@{{ currentUser?.account }}</p>
      </div>
      <span class="acct-card__role">{{ roleLabel }}</span>
    </section>

    <!-- 本月概览 -->
    <section class="acct-summary" aria-label="本月概览">
      <div class="acct-summary__cell">
        <span class="acct-summary__value tabular">
          ¥{{ formatMoneyShort(report.summary.totalAmount) }}
        </span>
        <span class="acct-summary__label">本月提成</span>
      </div>
      <div class="acct-summary__cell">
        <span class="acct-summary__value tabular">{{ report.pendingCount }}</span>
        <span class="acct-summary__label">待审核</span>
      </div>
      <div class="acct-summary__cell">
        <span class="acct-summary__value tabular">{{ report.approvedRecords.length }}</span>
        <span class="acct-summary__label">已确认</span>
      </div>
    </section>

    <p class="acct-month">{{ formatMonthLabel(monthKey) }} 数据</p>

    <!-- 功能列表 -->
    <section class="acct-list">
      <button type="button" class="acct-item" @click="openPwd">
        <span class="acct-item__icon"><AppIcon name="lock" :size="18" /></span>
        <span class="acct-item__label">修改密码</span>
        <AppIcon name="chevron-right" :size="16" class="acct-item__arrow" />
      </button>

      <button type="button" class="acct-item" @click="toggle">
        <span class="acct-item__icon"><AppIcon name="moon" :size="18" /></span>
        <span class="acct-item__label">深色模式</span>
        <span class="acct-item__value">{{ theme === 'dark' ? '已开启' : '已关闭' }}</span>
        <AppIcon name="chevron-right" :size="16" class="acct-item__arrow" />
      </button>

      <button type="button" class="acct-item" @click="aboutOpen = true">
        <span class="acct-item__icon"><AppIcon name="info" :size="18" /></span>
        <span class="acct-item__label">关于系统</span>
        <AppIcon name="chevron-right" :size="16" class="acct-item__arrow" />
      </button>
    </section>

    <!-- 退出登录 -->
    <button type="button" class="acct-logout" @click="openLogout">
      <AppIcon name="logout" :size="18" />
      退出登录
    </button>

    <p class="acct-version">上门服务记录与提成核算 · v2.0</p>

    <!-- 修改密码弹层 -->
    <AppSheet :open="pwdOpen" title="修改密码" @close="pwdOpen = false">
      <div class="pwd-form">
        <label class="pwd-field">
          <span class="pwd-field__label">当前密码</span>
          <input
            v-model="pwdOld"
            type="password"
            class="pwd-field__input"
            placeholder="请输入当前密码"
            autocomplete="current-password"
          />
        </label>
        <label class="pwd-field">
          <span class="pwd-field__label">新密码</span>
          <input
            v-model="pwdNew"
            type="password"
            class="pwd-field__input"
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </label>
        <label class="pwd-field">
          <span class="pwd-field__label">确认新密码</span>
          <input
            v-model="pwdConfirm"
            type="password"
            class="pwd-field__input"
            placeholder="再次输入新密码"
            autocomplete="new-password"
          />
        </label>
        <p v-if="pwdError" class="pwd-error">{{ pwdError }}</p>
      </div>
      <template #footer>
        <button type="button" class="btn btn--ghost" @click="pwdOpen = false">取消</button>
        <button
          type="button"
          class="btn btn--primary"
          :disabled="pwdSubmitting"
          @click="submitPwd"
        >
          {{ pwdSubmitting ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </AppSheet>

    <!-- 退出确认 -->
    <AppSheet :open="logoutOpen" title="退出登录" @close="logoutOpen = false">
      <p class="confirm-text">退出后需要重新输入账号密码才能继续使用，确定退出吗？</p>
      <template #footer>
        <button type="button" class="btn btn--ghost" @click="logoutOpen = false">取消</button>
        <button type="button" class="btn btn--danger" @click="doLogout">确认退出</button>
      </template>
    </AppSheet>

    <!-- 关于 -->
    <AppSheet :open="aboutOpen" title="关于系统" @close="aboutOpen = false">
      <div class="about-body">
        <p class="about-body__title">上门服务记录与提成核算</p>
        <p class="about-body__ver">版本 v2.0</p>
        <ul class="about-body__list">
          <li>记录留痕：每条服务记录固化计费快照，配置变更不影响历史核算</li>
          <li>审核把关：管理员确认后锁定并计入提成</li>
          <li>自动核算：按次 / 按小时分别计费，加班按倍率</li>
          <li>修正留痕：已确认记录修正保留前后快照</li>
        </ul>
      </div>
      <template #footer>
        <button type="button" class="btn btn--primary" @click="aboutOpen = false">知道了</button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.m-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding-bottom: calc(var(--tabbar-height) + var(--space-lg));
}

/* ---------- 页头 ---------- */
.m-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: calc(var(--safe-top) + var(--space-md)) var(--space-md) var(--space-sm);
  background: linear-gradient(180deg, var(--color-surface) 0%, var(--color-bg) 100%);
}

.m-head__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
}

.m-head__title {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-snug);
  margin-top: 2px;
}

.grow {
  flex: 1;
  min-width: 0;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-surface);
  color: var(--color-fg-secondary);
  cursor: pointer;
}

/* 内容区左右内边距 */
.acct-card,
.acct-summary,
.acct-month,
.acct-list,
.acct-logout,
.acct-version {
  margin-left: var(--space-md);
  margin-right: var(--space-md);
}

/* ---------- 账号卡 ---------- */
.acct-card {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-lg);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(120% 100% at 0% 0%, rgba(56, 189, 248, 0.18), transparent 60%),
    linear-gradient(160deg, #0b1220 0%, #16294c 100%);
  color: #fff;
}

.acct-card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex: none;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.16);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
}

.acct-card__main {
  flex: 1;
  min-width: 0;
}

.acct-card__name {
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  line-height: 1.2;
}

.acct-card__account {
  font-size: var(--text-xs);
  color: rgba(255, 255, 255, 0.6);
  margin-top: 2px;
}

.acct-card__role {
  flex: none;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.16);
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
}

/* ---------- 本月概览 ---------- */
.acct-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.acct-summary__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--space-md) var(--space-sm);
  border-right: 1px solid var(--color-border);
}

.acct-summary__cell:last-child {
  border-right: none;
}

.acct-summary__value {
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--color-fg);
}

.acct-summary__label {
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

.acct-month {
  margin-top: calc(var(--space-xs) * -1);
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
  text-align: center;
}

/* ---------- 功能列表 ---------- */
.acct-list {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.acct-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-height: 52px;
  padding: var(--space-sm) var(--space-md);
  border: none;
  border-bottom: 1px solid var(--color-border);
  background: none;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.acct-item:last-child {
  border-bottom: none;
}

.acct-item:active {
  background: var(--color-surface-hover);
}

.acct-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: var(--radius-md);
  background: var(--color-surface-hover);
  color: var(--color-fg-secondary);
}

.acct-item__label {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--color-fg);
}

.acct-item__value {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.acct-item__arrow {
  flex: none;
  color: var(--color-fg-tertiary);
}

/* ---------- 退出 ---------- */
.acct-logout {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--color-danger-border, rgba(239, 68, 68, 0.3));
  border-radius: var(--radius-lg);
  background: var(--color-danger-soft, rgba(239, 68, 68, 0.08));
  color: var(--color-danger, #ef4444);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  cursor: pointer;
  transition: background 0.15s ease;
}

.acct-logout:active {
  background: var(--color-danger-soft-strong, rgba(239, 68, 68, 0.16));
}

.acct-version {
  text-align: center;
  font-size: var(--text-2xs);
  color: var(--color-fg-tertiary);
}

/* ---------- 修改密码表单 ---------- */
.pwd-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.pwd-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pwd-field__label {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--color-fg-secondary);
}

.pwd-field__input {
  width: 100%;
  min-height: 44px;
  padding: 0 var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-fg);
  font-size: var(--text-sm);
  outline: none;
  transition: border-color 0.15s ease;
}

.pwd-field__input:focus {
  border-color: var(--color-primary);
}

.pwd-error {
  font-size: var(--text-xs);
  color: var(--color-danger, #ef4444);
}

.confirm-text {
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--color-fg-secondary);
}

/* ---------- 关于 ---------- */
.about-body__title {
  font-size: var(--text-md);
  font-weight: var(--weight-bold);
  color: var(--color-fg);
}

.about-body__ver {
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.about-body__list {
  margin: var(--space-md) 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--color-fg-secondary);
}
</style>
