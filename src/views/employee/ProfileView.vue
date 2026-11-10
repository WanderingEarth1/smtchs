<script setup lang="ts">
/**
 * 员工「我的」页 —— 账号信息、修改密码、主题、退出
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppSheet from '@/components/AppSheet.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import { BizError, changeOwnPassword, buildMyMonthlySummary } from '@/data/service'
import { currentMonthKey, formatMoneyShort } from '@/core/date'

const router = useRouter()
const { currentUser, logout, dataVersion } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const monthSummary = computed(() => {
  void dataVersion.value
  const user = currentUser.value
  if (!user) return null
  return buildMyMonthlySummary(user.id, currentMonthKey())
})

const pwdOpen = ref(false)
const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const pwdError = ref('')
const pwdSubmitting = ref(false)

function openPwd(): void {
  oldPwd.value = ''
  newPwd.value = ''
  confirmPwd.value = ''
  pwdError.value = ''
  pwdOpen.value = true
}

async function submitPwd(): Promise<void> {
  const user = currentUser.value
  if (!user || pwdSubmitting.value) return

  pwdError.value = ''
  if (!oldPwd.value) {
    pwdError.value = '请输入原密码'
    return
  }
  if (newPwd.value.length < 6) {
    pwdError.value = '新密码至少 6 位'
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }

  pwdSubmitting.value = true
  try {
    await changeOwnPassword(user.id, oldPwd.value, newPwd.value)
    toast.success('密码修改成功')
    pwdOpen.value = false
  } catch (err) {
    pwdError.value = err instanceof BizError ? err.message : '修改失败，请稍后重试'
  } finally {
    pwdSubmitting.value = false
  }
}

function doLogout(): void {
  logout()
  void router.replace('/login')
}
</script>

<template>
  <div class="profile-view">
    <header class="app-header">
      <h1 class="app-header__title">我的</h1>
    </header>

    <div class="page">
      <!-- 账号卡片 -->
      <section class="profile-card">
        <div class="profile-card__avatar">{{ currentUser?.name.slice(0, 1) }}</div>
        <div class="grow">
          <p class="profile-card__name">{{ currentUser?.name }}</p>
          <p class="profile-card__account mono">@{{ currentUser?.account }}</p>
        </div>
        <span class="tag tag--neutral">工程师</span>
      </section>

      <!-- 本月概览 -->
      <section v-if="monthSummary" class="profile-summary">
        <div class="profile-summary__cell">
          <span class="profile-summary__value tabular">{{
            formatMoneyShort(monthSummary.summary.totalAmount)
          }}</span>
          <span class="profile-summary__label">本月提成（元）</span>
        </div>
        <div class="profile-summary__cell">
          <span class="profile-summary__value tabular">{{ monthSummary.recordCount }}</span>
          <span class="profile-summary__label">本月记录（条）</span>
        </div>
        <div class="profile-summary__cell">
          <span class="profile-summary__value tabular">{{ monthSummary.pendingCount }}</span>
          <span class="profile-summary__label">待确认（条）</span>
        </div>
      </section>

      <!-- 设置列表 -->
      <section class="profile-list">
        <button type="button" class="profile-item" @click="openPwd">
          <span class="profile-item__icon"><AppIcon name="lock" :size="18" /></span>
          <span class="grow">
            <span class="profile-item__title">修改登录密码</span>
            <span class="profile-item__desc">建议定期更换，至少 6 位</span>
          </span>
          <AppIcon name="chevron-right" :size="18" class="profile-item__arrow" />
        </button>

        <button type="button" class="profile-item" @click="toggle">
          <span class="profile-item__icon">
            <AppIcon :name="theme === 'dark' ? 'moon' : 'sun'" :size="18" />
          </span>
          <span class="grow">
            <span class="profile-item__title">外观主题</span>
            <span class="profile-item__desc">{{ theme === 'dark' ? '深色模式' : '浅色模式' }}</span>
          </span>
          <span class="switch" :aria-checked="theme === 'dark'" role="switch" aria-label="切换深色模式" />
        </button>
      </section>

      <section class="profile-list">
        <button type="button" class="profile-item profile-item--danger" @click="doLogout">
          <span class="profile-item__icon"><AppIcon name="logout" :size="18" /></span>
          <span class="grow">
            <span class="profile-item__title">退出登录</span>
          </span>
        </button>
      </section>

      <p class="profile-foot">
        工程师上门服务记录与提成核算系统 · v1.0
        <br />
        记录留痕 · 审核把关 · 自动核算
      </p>
    </div>

    <!-- 修改密码 -->
    <AppSheet :open="pwdOpen" title="修改登录密码" @close="pwdOpen = false">
      <form class="pwd-form" novalidate @submit.prevent="submitPwd">
        <div v-if="pwdError" class="alert alert--danger" role="alert">
          <AppIcon name="warning" :size="16" />
          <span>{{ pwdError }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="pwd-old">原密码</label>
          <input
            id="pwd-old"
            v-model="oldPwd"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入当前密码"
          />
        </div>

        <div class="field">
          <label class="field__label" for="pwd-new">新密码</label>
          <input
            id="pwd-new"
            v-model="newPwd"
            class="input"
            type="password"
            autocomplete="new-password"
            placeholder="至少 6 位"
          />
        </div>

        <div class="field">
          <label class="field__label" for="pwd-confirm">确认新密码</label>
          <input
            id="pwd-confirm"
            v-model="confirmPwd"
            class="input"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入新密码"
          />
        </div>
      </form>

      <template #footer>
        <button type="button" class="btn btn--secondary" @click="pwdOpen = false">取消</button>
        <button type="button" class="btn btn--primary" :disabled="pwdSubmitting" @click="submitPwd">
          {{ pwdSubmitting ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </AppSheet>
  </div>
</template>

<style scoped>
.profile-view {
  min-height: 100dvh;
  background: var(--color-bg);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.profile-card__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  border-radius: 50%;
  background: linear-gradient(150deg, var(--brand-600), var(--brand-900));
  color: #fff;
  font-size: var(--text-lg);
  font-weight: 700;
}

.profile-card__name {
  font-size: var(--text-md);
  font-weight: 600;
}

.profile-card__account {
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.profile-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-sm);
  padding: var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.profile-summary__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.profile-summary__value {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-fg);
}

.profile-summary__label {
  font-size: 11px;
  color: var(--color-fg-tertiary);
  text-align: center;
}

.profile-list {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.profile-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  width: 100%;
  min-height: 60px;
  padding: var(--space-sm) var(--space-md);
  border: 0;
  border-bottom: 1px solid var(--color-divider);
  background: transparent;
  text-align: left;
  color: var(--color-fg);
  transition: background-color var(--duration-fast) var(--ease-in-out);
}

.profile-item:last-child {
  border-bottom: 0;
}

.profile-item:hover {
  background: var(--color-surface-muted);
}

.profile-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  color: var(--color-fg-secondary);
}

.profile-item__title {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
}

.profile-item__desc {
  display: block;
  font-size: 11px;
  color: var(--color-fg-tertiary);
}

.profile-item__arrow {
  color: var(--color-fg-tertiary);
  flex: 0 0 auto;
}

.profile-item--danger .profile-item__title {
  color: var(--color-danger);
}

.profile-item--danger .profile-item__icon {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.profile-foot {
  text-align: center;
  font-size: 11px;
  color: var(--color-fg-tertiary);
  line-height: var(--leading-relaxed);
  padding: var(--space-md) 0;
}

.pwd-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
</style>
