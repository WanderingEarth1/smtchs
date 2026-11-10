<script setup lang="ts">
/**
 * 登录页 —— 统一入口，按角色自动路由（LOG-02）
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useAuth, useTheme } from '@/stores/app'
import { useToast } from '@/stores/toast'
import { BizError } from '@/data/service'
import { isMobileDevice } from '@/router'

/** 演示账号（与后端 smtchs_data.sql 的初始化数据一致） */
const DEMO_ACCOUNTS = {
  admin: { account: 'admin', password: 'admin123' },
  employee: { account: 'zhangsan', password: '123456' },
} as const

const router = useRouter()
const route = useRoute()
const { login } = useAuth()
const { theme, toggle } = useTheme()
const toast = useToast()

const account = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const accountError = ref('')
const passwordError = ref('')

const accountInput = ref<HTMLInputElement | null>(null)

const canSubmit = computed(
  () => account.value.trim().length > 0 && password.value.length > 0 && !submitting.value,
)

onMounted(() => {
  // 自动聚焦账号输入框（桌面端）
  if (window.matchMedia('(pointer: fine)').matches) {
    accountInput.value?.focus()
  }
})

function fillDemo(kind: 'admin' | 'employee') {
  if (kind === 'admin') {
    account.value = DEMO_ACCOUNTS.admin.account
    password.value = DEMO_ACCOUNTS.admin.password
  } else {
    account.value = DEMO_ACCOUNTS.employee.account
    password.value = DEMO_ACCOUNTS.employee.password
  }
  errorMessage.value = ''
  accountError.value = ''
  passwordError.value = ''
}

async function submit(): Promise<void> {
  if (submitting.value) return

  accountError.value = ''
  passwordError.value = ''
  errorMessage.value = ''

  if (!account.value.trim()) {
    accountError.value = '请输入登录账号'
    return
  }
  if (!password.value) {
    passwordError.value = '请输入密码'
    return
  }

  submitting.value = true
  try {
    const user = await login(account.value, password.value)
    toast.success(`欢迎回来，${user.name}`)
    const redirect = route.query.redirect as string | undefined
    const fallback =
      user.role === 'ADMIN' ? (isMobileDevice() ? '/a/m/summary' : '/a/pc/dashboard') : '/e/home'
    await router.replace(redirect && redirect.startsWith('/') ? redirect : fallback)
  } catch (err) {
    const message = err instanceof BizError ? err.message : '登录失败，请稍后重试'
    errorMessage.value = message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-topbar">
      <button
        type="button"
        class="btn btn--ghost btn--icon"
        :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggle"
      >
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="18" />
      </button>
    </div>

    <div class="login-card">
      <div class="login-brand">
        <span class="login-brand__mark">
          <AppIcon name="briefcase" :size="26" :stroke-width="1.9" />
        </span>
        <h1 class="login-brand__title">工程师上门服务记录</h1>
        <p class="login-brand__sub">记录留痕 · 审核把关 · 自动核算提成</p>
      </div>

      <form class="login-form" novalidate @submit.prevent="submit">
        <div v-if="errorMessage" class="alert alert--danger" role="alert">
          <AppIcon name="warning" :size="16" />
          <span>{{ errorMessage }}</span>
        </div>

        <div class="field">
          <label class="field__label" for="login-account">
            登录账号
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <input
            id="login-account"
            ref="accountInput"
            v-model="account"
            class="input"
            type="text"
            name="username"
            autocomplete="username"
            inputmode="text"
            autocapitalize="off"
            autocorrect="off"
            spellcheck="false"
            placeholder="请输入账号"
            :aria-invalid="!!accountError"
            :aria-describedby="accountError ? 'login-account-error' : undefined"
            @input="accountError = ''"
          />
          <p v-if="accountError" id="login-account-error" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ accountError }}
          </p>
        </div>

        <div class="field">
          <label class="field__label" for="login-password">
            密码
            <span class="field__required" aria-hidden="true">*</span>
          </label>
          <div class="login-password">
            <input
              id="login-password"
              v-model="password"
              class="input"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              :aria-invalid="!!passwordError"
              :aria-describedby="passwordError ? 'login-password-error' : undefined"
              @input="passwordError = ''"
            />
            <button
              type="button"
              class="login-password__toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              <AppIcon :name="showPassword ? 'eye' : 'lock'" :size="18" />
            </button>
          </div>
          <p v-if="passwordError" id="login-password-error" class="field__error">
            <AppIcon name="warning" :size="12" :stroke-width="2.4" />
            {{ passwordError }}
          </p>
        </div>

        <button type="submit" class="btn btn--primary btn--lg btn--block" :disabled="!canSubmit">
          <AppIcon v-if="submitting" name="spinner" :size="18" class="spin" />
          <span>{{ submitting ? '登录中…' : '登录' }}</span>
        </button>

        <p class="login-hint">
          <AppIcon name="shield" :size="13" />
          登录状态保持 30 天，二次打开免登录
        </p>
      </form>

      <div class="login-demo">
        <p class="login-demo__title">演示账号（点击快速填入）</p>
        <div class="login-demo__row">
          <button type="button" class="login-demo__btn" @click="fillDemo('employee')">
            <span class="login-demo__role">员工端</span>
            <span class="login-demo__cred">zhangsan / 123456</span>
          </button>
          <button type="button" class="login-demo__btn" @click="fillDemo('admin')">
            <span class="login-demo__role">管理端</span>
            <span class="login-demo__cred">admin / admin123</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-topbar {
  position: absolute;
  top: calc(var(--safe-top) + 8px);
  right: 12px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.login-password {
  position: relative;
}

.login-password .input {
  padding-right: 46px;
}

.login-password__toggle {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-fg-tertiary);
}

.login-password__toggle:hover {
  color: var(--color-fg);
  background: var(--color-surface-muted);
}

.login-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: var(--text-xs);
  color: var(--color-fg-tertiary);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
