import { createApp } from 'vue'

import { AUTH_TOKEN_KEY, REFRESH_TOKEN_KEY } from '@/api/auth-token'
import { setTokensUpdatedHandler, setUnauthorizedHandler } from '@/api/http'
import App from '@/App.vue'
import router from '@/router'
import { pinia, useAuthStore } from '@/stores'
import { storageKey } from '@/utils/storage'
import '@/styles/tokens.css'
import '@/styles/base.css'
import '@/styles/utilities.css'

const auth = useAuthStore(pinia)

setTokensUpdatedHandler((tokens) => {
  auth.updateTokens(tokens)
})

setUnauthorizedHandler(() => {
  auth.clearSession()

  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

const SESSION_STORAGE_KEYS = new Set([
  AUTH_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  storageKey('auth.hasEmail'),
])

/** 另一个标签页登录 / 退出 / 刷新 token 时同步当前页 */
window.addEventListener('storage', (event) => {
  if (!event.key || !SESSION_STORAGE_KEYS.has(event.key)) return

  const hadSession = Boolean(auth.accessToken || auth.refreshToken)
  auth.hydrate()
  const current = router.currentRoute.value

  if (!auth.isLoggedIn()) {
    if (hadSession && current.meta.requiresAuth) {
      void router.replace({ name: 'login', query: { redirect: current.fullPath } })
    }
    return
  }

  if (current.name === 'login' || current.name === 'register') {
    void router.replace({ name: 'home' })
  }
})

createApp(App).use(pinia).use(router).mount('#app')
