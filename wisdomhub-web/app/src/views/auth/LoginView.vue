<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { login as loginRequest } from '@/api/modules/auth'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import { toast } from '@/components/base/toast'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { LoginPayload } from '@/types/user'
import { normalizeRedirect } from '@/utils/redirect'
import { EMAIL_PATTERN, PHONE_PATTERN } from '@/utils/validation'

import '@/styles/auth.css'

type LoginMode = 'email' | 'phone'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<LoginMode>('email')
const modeModel = computed({
  get: () => mode.value,
  set: (value: string) => {
    mode.value = value === 'phone' ? 'phone' : 'email'
  },
})
const modeTabs = [
  { label: '邮箱登录', value: 'email' },
  { label: '手机号登录', value: 'phone' },
]

const account = ref('')
const password = ref('')
const accountError = ref('')
const passwordError = ref('')
const formError = ref('')
const submitting = ref(false)
const errorBanner = ref<HTMLElement | null>(null)

const isEmail = computed(() => mode.value === 'email')
const accountLabel = computed(() => (isEmail.value ? '邮箱' : '手机号'))
const accountPlaceholder = computed(() => (isEmail.value ? 'you@example.com' : '13800000000'))
const accountType = computed(() => (isEmail.value ? 'email' : 'tel'))
const accountAutocomplete = computed(() => (isEmail.value ? 'email' : 'tel'))
const accountMaxlength = computed(() => (isEmail.value ? 128 : 11))

const redirect = computed(() =>
  typeof route.query.redirect === 'string' ? route.query.redirect : '',
)
const safeRedirect = computed(() => normalizeRedirect(redirect.value))
const registerTarget = computed(() => ({
  name: 'register',
  query: safeRedirect.value === '/home' ? {} : { redirect: safeRedirect.value },
}))

function validateAccount(): string {
  const value = account.value.trim()
  if (!value) return isEmail.value ? '请输入邮箱' : '请输入手机号'

  if (isEmail.value) {
    if (value.length > 128) return '邮箱长度不能超过 128 位'
    if (!EMAIL_PATTERN.test(value)) return '邮箱格式不正确'
    return ''
  }

  return PHONE_PATTERN.test(value) ? '' : '手机号格式不正确'
}

function validatePassword(): string {
  return password.value.trim() ? '' : '请输入密码'
}

watch(mode, () => {
  account.value = ''
  accountError.value = ''
  passwordError.value = ''
  formError.value = ''
})

watch(account, () => {
  accountError.value = ''
  formError.value = ''
})

watch(password, () => {
  passwordError.value = ''
  formError.value = ''
})

async function submit(): Promise<void> {
  if (submitting.value) return

  accountError.value = validateAccount()
  passwordError.value = validatePassword()
  formError.value = ''

  if (accountError.value || passwordError.value) {
    await nextTick()
    if (accountError.value) document.getElementById('login-account')?.focus()
    else document.getElementById('login-password')?.focus()
    return
  }

  submitting.value = true

  try {
    const payload: LoginPayload = isEmail.value
      ? { email: account.value.trim(), password: password.value }
      : { phone: account.value.trim(), password: password.value }

    const session = await loginRequest(payload, { skipErrorToast: true })
    auth.setSession(session)
    toast.success('登录成功，欢迎回来')
    await router.replace(safeRedirect.value)
  } catch (error) {
    formError.value = isApiError(error) ? error.message : '登录失败，请稍后重试'
    await nextTick()
    errorBanner.value?.focus()
  } finally {
    submitting.value = false
  }
}

useSeo({
  title: '登录',
  description: '登录 WisdomHub，继续你的技术知识网络。',
})
</script>

<template>
  <section class="auth-page" aria-labelledby="login-title">
    <div class="wh-container auth-wrap">
      <div class="auth-card">
        <aside class="auth-aside">
          <p class="auth-eyebrow">WISDOMHUB / AUTH</p>
          <p class="auth-display">把散落的知识，<em>连成网络。</em></p>
          <p class="auth-lead">
            文章、问答、图谱与 AI 助手，共用同一张知识网络。登录后，从你上次停下的地方继续。
          </p>

          <ol class="auth-points">
            <li class="auth-point">
              <span class="auth-point__index">01</span>
              <span class="auth-point__text">读完一篇，顺着图谱继续。</span>
            </li>
            <li class="auth-point">
              <span class="auth-point__index">02</span>
              <span class="auth-point__text">提一个问题，让答案可被检索。</span>
            </li>
            <li class="auth-point">
              <span class="auth-point__index">03</span>
              <span class="auth-point__text">让 AI 引用原文，而不是凭空生成。</span>
            </li>
          </ol>

          <p class="auth-aside__meta">EST. 2026 · BUILT FOR DEVELOPERS</p>
        </aside>

        <div class="auth-panel">
          <div class="auth-panel__head">
            <p class="auth-kicker">01 / SIGN IN</p>
            <h1 id="login-title" class="auth-title">登录</h1>
            <p class="auth-sub">继续你的知识网络。</p>
          </div>

          <WhTabs v-model="modeModel" :items="modeTabs" />

          <form class="auth-form" novalidate @submit.prevent="submit">
            <div class="auth-field">
              <label class="auth-label" for="login-account">{{ accountLabel }}</label>
              <WhInput
                id="login-account"
                v-model="account"
                :type="accountType"
                :name="isEmail ? 'email' : 'phone'"
                :autocomplete="accountAutocomplete"
                :placeholder="accountPlaceholder"
                :maxlength="accountMaxlength"
                :invalid="Boolean(accountError)"
                :error="accountError"
                size="lg"
              />
            </div>

            <div class="auth-field">
              <label class="auth-label" for="login-password">密码</label>
              <WhInput
                id="login-password"
                v-model="password"
                type="password"
                name="password"
                autocomplete="current-password"
                placeholder="输入密码"
                :invalid="Boolean(passwordError)"
                :error="passwordError"
                size="lg"
              />
            </div>

            <div v-if="formError" ref="errorBanner" class="auth-error" role="alert" tabindex="-1">
              <strong>登录失败</strong>
              <span>{{ formError }}</span>
            </div>

            <WhButton class="auth-submit" type="submit" size="lg" block :loading="submitting">
              登录
            </WhButton>
          </form>

          <p class="auth-switch">
            还没有账号？
            <RouterLink :to="registerTarget">注册</RouterLink>
          </p>
          <p class="auth-note">SECURE SESSION · ACCESS TOKEN / REFRESH TOKEN</p>
        </div>
      </div>
    </div>
  </section>
</template>
