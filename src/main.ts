import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { useAuth, useTheme } from './stores/app'
import { seedIfNeeded } from './data/seed'

import './styles/tokens.css'
import './styles/base.css'
import './styles/typography.css'
import './styles/components.css'
import './styles/app.css'

async function bootstrap(): Promise<void> {
  // 初始化主题
  useTheme().init()

  // 初始化演示数据（幂等）
  await seedIfNeeded()

  // 恢复登录态（LOG-03 无感进入）
  useAuth().restore()

  const app = createApp(App)
  app.use(router)
  app.mount('#app')
}

void bootstrap()
