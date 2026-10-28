<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { register as registerRequest, sendEmailCode, sendPhoneCode } from '@/api/modules/auth'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import { toast } from '@/components/base/toast'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { RegisterPayload } from '@/types/user'
import { normalizeRedirect } from '@/utils/redirect'
import { CODE_PATTERN, EMAIL_PATTERN, PHONE_PATTERN } from '@/utils/validation'

import '@/styles/auth.css'

type RegisterMode = 'email' | 'phone'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<RegisterMode>('email')
const modeModel = computed({
  get: () => mode.value,
  set: (value: string) => {
    mode.value = value === 'phone' ? 'phone' : 'email'
  },
})
const modeTabs = [
  { label: '邮箱注册', value: 'email' },
  { label: '手机号注册', value: 'phone' },
]

const account = ref('')
const code = ref('')
const username = ref('')
const password = ref('')
const accountError = ref('')
const codeError = ref('')
const usernameError = ref('')
const passwordError = ref('')
const formError = ref('')
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const codeSent = ref(false)
const errorBanner = ref<HTMLElement | null>(null)

let countdownTimer: number | null = null

const isEmail = computed(() => mode.value === 'email')
const accountLabel = computed(() => (isEmail.value ? '邮箱' : '手机号'))
const accountPlaceholder = computed(() => (isEmail.value ? 'you@example.com' : '13800000000'))
const accountType = computed(() => (isEmail.value ? 'email' : 'tel'))
const accountAutocomplete = computed(() => (isEmail.value ? 'email' : 'tel'))
const accountMaxlength = computed(() => (isEmail.value ? 128 : 11))

const codeButtonText = computed(() => {
  if (sendingCode.value) return '发送中…'
  if (countdown.value > 0) return `${countdown.value}s 后重试`
  return codeSent.value ? '重新获取' : '获取验证码'
})

const redirect = computed(() =>
  typeof route.query.redirect === 'string' ? route.query.redirect : '',
)
const safeRedirect = computed(() => normalizeRedirect(redirect.value))
const loginTarget = computed(() => ({
  name: 'login',
  query: safeRedirect.value === '/home' ? {} : { redirect: safeRedirect.value },
}))

function stopCountdown(): void {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown(): void {
  stopCountdown()
  countdown.value = 60
  codeSent.value = true
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopCountdown()
  }, 1000)
}

function resetVerification(): void {
  code.value = ''
  codeError.value = ''
  countdown.value = 0
  codeSent.value = false
  stopCountdown()
}

onBeforeUnmount(stopCountdown)

watch(mode, () => {
  account.value = ''
  accountError.value = ''
  usernameError.value = ''
  passwordError.value = ''
  formError.value = ''
  resetVerification()
})

watch(account, () => {
  accountError.value = ''
  formError.value = ''
})

watch(code, () => {
  codeError.value = ''
  formError.value = ''
})

watch(username, () => {
  usernameError.value = ''
  formError.value = ''
})

watch(password, () => {
  passwordError.value = ''
  formError.value = ''
})

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

function validateCode(): string {
  const value = code.value.trim()
  if (!value) return '请输入验证码'
  return CODE_PATTERN.test(value) ? '' : '请输入 6 位验证码'
}

function validateUsername(): string {
  const value = username.value.trim()
  if (!value) return '请输入用户名'
  return value.length > 20 ? '用户名长度不能超过 20 位' : ''
}

function validatePassword(): string {
  return password.value.trim() ? '' : '请输入密码'
}

async function sendCode(): Promise<void> {
  if (sendingCode.value || countdown.value > 0) return

  accountError.value = validateAccount()
  formError.value = ''
  if (accountError.value) {
    await nextTick()
    document.getElementById('register-account')?.focus()
    return
  }

  sendingCode.value = true

  try {
    const value = account.value.trim()
    if (isEmail.value) await sendEmailCode(value, { skipErrorToast: true })
    else await sendPhoneCode(value, { skipErrorToast: true })

    startCountdown()
    toast.info('验证码已发送，请查看 user-service 日志')
  } catch (error) {
    formError.value = isApiError(error) ? error.message : '验证码发送失败，请稍后重试'
    await nextTick()
    errorBanner.value?.focus()
  } finally {
    sendingCode.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) return

  accountError.value = validateAccount()
  codeError.value = validateCode()
  usernameError.value = validateUsername()
  passwordError.value = validatePassword()
  formError.value = ''

  if (accountError.value || codeError.value || usernameError.value || passwordError.value) {
    await nextTick()
    if (accountError.value) document.getElementById('register-account')?.focus()
    else if (codeError.value) document.getElementById('register-code')?.focus()
    else if (usernameError.value) document.getElementById('register-username')?.focus()
    else document.getElementById('register-password')?.focus()
    return
  }

  submitting.value = true

  try {
    const payload: RegisterPayload = {
      ...(isEmail.value ? { email: account.value.trim() } : { phone: account.value.trim() }),
      code: code.value.trim(),
      username: username.value.trim(),
      password: password.value,
    }

    const session = await registerRequest(payload, { skipErrorToast: true })
    auth.setSession(session)
    toast.success('注册成功，已自动登录')
    await router.replace(safeRedirect.value)
  } catch (error) {
    formError.value = isApiError(error) ? error.message : '注册失败，请稍后重试'
    await nextTick()
    errorBanner.value?.focus()
  } finally {
    submitting.value = false
  }
}

useSeo({
  title: '注册',
  description: '创建 WisdomHub 账号，开始构建你的技术知识网络。',
})
</script>

<template>
  <section class="auth-page" aria-labelledby="register-title">
    <div class="wh-container auth-wrap">
      <div class="auth-card">
        <aside class="auth-aside">
          <p class="auth-eyebrow">WISDOMHUB / JOIN</p>
          <p class="auth-display">从一篇好文章开始，<em>建立你的网络。</em></p>
          <p class="auth-lead">
            注册后可以收藏、关注、提问，并让 AI
            记住你的学习路径。你的每一次阅读都会成为网络的一部分。
          </p>

          <ol class="auth-points">
            <li class="auth-point">
              <span class="auth-point__index">01</span>
              <span class="auth-point__text">收藏文章，留下自己的技术路径。</span>
            </li>
            <li class="auth-point">
              <span class="auth-point__index">02</span>
              <span class="auth-point__text">关注作者，持续接收同一主题。</span>
            </li>
            <li class="auth-point">
              <span class="auth-point__index">03</span>
              <span class="auth-point__text">提问、回答，让知识可被检索。</span>
            </li>
          </ol>

          <p class="auth-aside__meta">EST. 2026 · BUILT FOR DEVELOPERS</p>
        </aside>

        <div class="auth-panel">
          <div class="auth-panel__head">
            <p class="auth-kicker">02 / CREATE ACCOUNT</p>
            <h1 id="register-title" class="auth-title">注册</h1>
            <p class="auth-sub">创建账号，开始构建你的知识网络。</p>
          </div>

          <WhTabs v-model="modeModel" :items="modeTabs" />

          <form class="auth-form" novalidate @submit.prevent="submit">
            <div class="auth-field">
              <label class="auth-label" for="register-account">{{ accountLabel }}</label>
              <WhInput
                id="register-account"
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
              <label class="auth-label" for="register-code">验证码</label>
              <div class="auth-code-row">
                <WhInput
                  id="register-code"
                  v-model="code"
                  type="text"
                  name="code"
                  autocomplete="one-time-code"
                  placeholder="6 位验证码"
                  :maxlength="6"
                  :invalid="Boolean(codeError)"
                  :error="codeError"
                  size="lg"
                />
                <WhButton
                  class="auth-code-button"
                  type="button"
                  size="lg"
                  variant="ghost"
                  :loading="sendingCode"
                  :disabled="countdown > 0"
                  @click="sendCode"
                >
                  {{ codeButtonText }}
                </WhButton>
              </div>
              <p class="auth-hint">验证码为模拟实现，请到 <code>user-service</code> 日志查看。</p>
            </div>

            <div class="auth-field">
              <label class="auth-label" for="register-username">用户名</label>
              <WhInput
                id="register-username"
                v-model="username"
                type="text"
                name="username"
                autocomplete="username"
                placeholder="给自己取个名字"
                :maxlength="20"
                :invalid="Boolean(usernameError)"
                :error="usernameError"
                size="lg"
              />
            </div>

            <div class="auth-field">
              <label class="auth-label" for="register-password">密码</label>
              <WhInput
                id="register-password"
                v-model="password"
                type="password"
                name="password"
                autocomplete="new-password"
                placeholder="设置密码"
                :invalid="Boolean(passwordError)"
                :error="passwordError"
                size="lg"
              />
            </div>

            <div v-if="formError" ref="errorBanner" class="auth-error" role="alert" tabindex="-1">
              <strong>注册失败</strong>
              <span>{{ formError }}</span>
            </div>

            <WhButton class="auth-submit" type="submit" size="lg" block :loading="submitting">
              注册并登录
            </WhButton>
          </form>

          <p class="auth-switch">
            已有账号？
            <RouterLink :to="loginTarget">登录</RouterLink>
          </p>
          <p class="auth-note">NEW MEMBER · ACCESS TOKEN / REFRESH TOKEN</p>
        </div>
      </div>
    </div>
  </section>
</template>
