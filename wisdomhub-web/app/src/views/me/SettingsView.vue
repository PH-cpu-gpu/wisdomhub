<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { sendEmailCode as requestEmailCode } from '@/api/modules/auth'
import {
  bindEmail,
  getProfile as fetchProfile,
  updateProfile,
  uploadAvatar,
} from '@/api/modules/user'
import WhAvatar from '@/components/base/WhAvatar.vue'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { UserProfileVO } from '@/types/user'
import { CODE_PATTERN, EMAIL_PATTERN } from '@/utils/validation'

const AVATAR_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const AVATAR_EXTENSIONS = /\.(jpe?g|png|gif|webp)$/i
const MAX_AVATAR_SIZE = 2 * 1024 * 1024

const auth = useAuthStore()

const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)
const loadError = ref('')

const username = ref('')
const bio = ref('')
const avatarSrc = ref('')
const usernameError = ref('')
const bioError = ref('')
const profileError = ref('')
const profileSaving = ref(false)

const avatarUploading = ref(false)
const avatarError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
let previewUrl: string | null = null

const email = ref('')
const emailCode = ref('')
const emailError = ref('')
const emailCodeError = ref('')
const emailFormError = ref('')
const emailSending = ref(false)
const emailSubmitting = ref(false)
const emailCountdown = ref(0)
const emailCodeSent = ref(false)
const boundEmail = ref('')
let emailTimer: number | null = null

const profileLink = computed(() =>
  auth.userId ? { name: 'user-profile', params: { userId: auth.userId } } : '/',
)
const emailButtonText = computed(() => {
  if (emailSending.value) return '发送中…'
  if (emailCountdown.value > 0) return `${emailCountdown.value}s 后重试`
  return emailCodeSent.value ? '重新获取' : '获取验证码'
})
const emailDescription = computed(() => {
  if (!auth.hasEmail) return '绑定邮箱后可用于登录与找回。'
  return boundEmail.value
    ? `当前邮箱：${boundEmail.value}。可绑定新邮箱。`
    : '邮箱已绑定，可绑定新邮箱。'
})

watch(username, () => {
  usernameError.value = ''
  profileError.value = ''
})

watch(bio, () => {
  bioError.value = ''
  profileError.value = ''
})

watch(email, () => {
  emailError.value = ''
  emailFormError.value = ''
})

watch(emailCode, () => {
  emailCodeError.value = ''
  emailFormError.value = ''
})

function focus(id: string): void {
  document.getElementById(id)?.focus()
}

async function loadProfile(): Promise<void> {
  if (!auth.userId) {
    loading.value = false
    loadError.value = '登录状态无效，请重新登录。'
    return
  }

  loading.value = true
  loadError.value = ''

  try {
    const data = await fetchProfile(auth.userId, { skipErrorToast: true })
    profile.value = data
    username.value = data.username
    bio.value = data.bio ?? ''
    avatarSrc.value = data.avatar ?? ''
  } catch (error) {
    loadError.value = isApiError(error) ? error.message : '资料加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  if (profileSaving.value) return

  const nextUsername = username.value.trim()
  const nextBio = bio.value.trim()

  usernameError.value = nextUsername ? '' : '请输入用户名'
  if (nextUsername.length > 20) usernameError.value = '用户名长度不能超过 20 位'
  bioError.value = nextBio.length > 255 ? '简介长度不能超过 255 字' : ''
  profileError.value = ''

  if (usernameError.value || bioError.value) {
    focus(usernameError.value ? 'settings-username' : 'settings-bio')
    return
  }

  profileSaving.value = true

  try {
    await updateProfile({ username: nextUsername, bio: nextBio }, { skipErrorToast: true })
    if (profile.value) {
      profile.value = { ...profile.value, username: nextUsername, bio: nextBio }
    }
    toast.success('资料已保存')
  } catch (error) {
    profileError.value = isApiError(error) ? error.message : '保存失败，请稍后重试。'
  } finally {
    profileSaving.value = false
  }
}

function clearPreview(): void {
  if (previewUrl) {
    URL.revokeObjectURL(previewUrl)
    previewUrl = null
  }
}

function chooseAvatar(): void {
  if (avatarUploading.value) return
  avatarError.value = ''
  fileInput.value?.click()
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file) return

  avatarError.value = ''

  if (!AVATAR_TYPES.has(file.type) && !AVATAR_EXTENSIONS.test(file.name)) {
    avatarError.value = '头像仅支持 jpg / jpeg / png / gif / webp 格式。'
    return
  }

  if (file.size > MAX_AVATAR_SIZE) {
    avatarError.value = '头像不能超过 2MB。'
    return
  }

  const previous = profile.value?.avatar ?? ''
  clearPreview()
  previewUrl = URL.createObjectURL(file)
  avatarSrc.value = previewUrl
  avatarUploading.value = true

  try {
    const url = await uploadAvatar(file, { skipErrorToast: true })
    clearPreview()
    avatarSrc.value = url
    if (profile.value) profile.value = { ...profile.value, avatar: url }
    toast.success('头像已更新')
  } catch (error) {
    clearPreview()
    avatarSrc.value = previous
    avatarError.value = isApiError(error) ? error.message : '头像上传失败，请稍后重试。'
  } finally {
    avatarUploading.value = false
  }
}

function stopEmailCountdown(): void {
  if (emailTimer !== null) {
    window.clearInterval(emailTimer)
    emailTimer = null
  }
}

function startEmailCountdown(): void {
  stopEmailCountdown()
  emailCountdown.value = 60
  emailCodeSent.value = true
  emailTimer = window.setInterval(() => {
    emailCountdown.value -= 1
    if (emailCountdown.value <= 0) stopEmailCountdown()
  }, 1000)
}

function validateEmail(): string {
  const value = email.value.trim()
  if (!value) return '请输入邮箱'
  if (value.length > 128) return '邮箱长度不能超过 128 位'
  return EMAIL_PATTERN.test(value) ? '' : '邮箱格式不正确'
}

function validateEmailCode(): string {
  const value = emailCode.value.trim()
  if (!value) return '请输入验证码'
  return CODE_PATTERN.test(value) ? '' : '请输入 6 位验证码'
}

async function sendEmailCode(): Promise<void> {
  if (emailSending.value || emailCountdown.value > 0) return

  emailError.value = validateEmail()
  emailFormError.value = ''

  if (emailError.value) {
    focus('settings-email')
    return
  }

  emailSending.value = true

  try {
    await requestEmailCode(email.value.trim(), { skipErrorToast: true })
    startEmailCountdown()
    toast.info('验证码已发送，请查看 user-service 日志')
  } catch (error) {
    emailFormError.value = isApiError(error) ? error.message : '验证码发送失败，请稍后重试。'
  } finally {
    emailSending.value = false
  }
}

async function bindUserEmail(): Promise<void> {
  if (emailSubmitting.value) return

  emailError.value = validateEmail()
  emailCodeError.value = validateEmailCode()
  emailFormError.value = ''

  if (emailError.value || emailCodeError.value) {
    focus(emailError.value ? 'settings-email' : 'settings-email-code')
    return
  }

  emailSubmitting.value = true

  try {
    const value = email.value.trim()
    await bindEmail({ email: value, code: emailCode.value.trim() }, { skipErrorToast: true })
    auth.updateHasEmail(true)
    boundEmail.value = value
    emailCode.value = ''
    toast.success('邮箱已绑定')
  } catch (error) {
    emailFormError.value = isApiError(error) ? error.message : '邮箱绑定失败，请稍后重试。'
  } finally {
    emailSubmitting.value = false
  }
}

onMounted(loadProfile)

onBeforeUnmount(() => {
  clearPreview()
  stopEmailCountdown()
})

useSeo({
  title: '账号设置',
  description: '更新 WisdomHub 的用户名、简介、头像与登录邮箱。',
})
</script>

<template>
  <PageContainer tag="section">
    <header class="settings-head">
      <div>
        <p class="settings-eyebrow">[ ME / SETTINGS ]</p>
        <h1>账号设置</h1>
        <p class="settings-sub">更新公开资料、头像与登录邮箱。</p>
      </div>
      <RouterLink class="settings-back" :to="profileLink">查看个人主页 →</RouterLink>
    </header>

    <div v-if="loading" class="settings-state" aria-live="polite">
      <span class="settings-state__mark">…</span>
      <p>正在加载资料</p>
    </div>

    <div v-else-if="loadError" class="settings-state settings-state--error" role="alert">
      <span class="settings-state__mark">!</span>
      <p>{{ loadError }}</p>
      <WhButton size="sm" variant="ghost" @click="loadProfile">重试</WhButton>
    </div>

    <div v-else class="settings-grid">
      <aside class="settings-profile">
        <div class="settings-profile__top">
          <WhAvatar
            :src="avatarSrc"
            :name="profile?.username || '用户'"
            size="xl"
            shape="circle"
            alt="当前头像"
          />
          <div>
            <p class="settings-profile__name">{{ profile?.username || '未命名' }}</p>
            <p class="settings-profile__meta">ID {{ auth.userId }}</p>
          </div>
        </div>

        <div class="settings-avatar-actions">
          <WhButton
            size="sm"
            variant="ghost"
            :loading="avatarUploading"
            :disabled="avatarUploading"
            @click="chooseAvatar"
          >
            更换头像
          </WhButton>
          <input
            ref="fileInput"
            class="settings-file"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            @change="onAvatarChange"
          />
        </div>
        <p class="settings-hint">JPG / PNG / GIF / WebP，≤ 2MB。</p>
        <p v-if="avatarError" class="settings-inline-error" role="alert">{{ avatarError }}</p>

        <dl class="settings-stats">
          <div>
            <dt>文章</dt>
            <dd>{{ profile?.articleCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>粉丝</dt>
            <dd>{{ profile?.fansCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>关注</dt>
            <dd>{{ profile?.followCount ?? 0 }}</dd>
          </div>
        </dl>

        <p class="settings-profile__bio">{{ profile?.bio || '还没有简介。' }}</p>
      </aside>

      <div class="settings-panels">
        <section class="settings-card">
          <header class="settings-card__head">
            <div>
              <p class="settings-kicker">01 / PROFILE</p>
              <h2>基本资料</h2>
            </div>
            <span class="settings-status">公开</span>
          </header>

          <form class="settings-form" novalidate @submit.prevent="saveProfile">
            <div class="settings-field">
              <label for="settings-username">用户名</label>
              <WhInput
                id="settings-username"
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

            <div class="settings-field">
              <label for="settings-bio">简介</label>
              <WhTextarea
                id="settings-bio"
                v-model="bio"
                placeholder="介绍一下你关注的技术方向"
                :rows="4"
                :maxlength="255"
                :invalid="Boolean(bioError)"
                :error="bioError"
                show-count
              />
            </div>

            <div v-if="profileError" class="settings-error" role="alert">
              {{ profileError }}
            </div>

            <div class="settings-actions">
              <WhButton type="submit" size="lg" :loading="profileSaving">保存资料</WhButton>
            </div>
          </form>
        </section>

        <section class="settings-card">
          <header class="settings-card__head">
            <div>
              <p class="settings-kicker">02 / EMAIL</p>
              <h2>登录邮箱</h2>
            </div>
            <span class="settings-status" :class="{ 'is-bound': auth.hasEmail }">
              {{ auth.hasEmail ? '已绑定' : '未绑定' }}
            </span>
          </header>

          <p class="settings-card__desc">{{ emailDescription }}</p>

          <form class="settings-form" novalidate @submit.prevent="bindUserEmail">
            <div class="settings-field">
              <label for="settings-email">{{ auth.hasEmail ? '新邮箱' : '邮箱' }}</label>
              <WhInput
                id="settings-email"
                v-model="email"
                type="email"
                name="email"
                autocomplete="email"
                placeholder="you@example.com"
                :maxlength="128"
                :invalid="Boolean(emailError)"
                :error="emailError"
                size="lg"
              />
            </div>

            <div class="settings-field">
              <label for="settings-email-code">验证码</label>
              <div class="settings-code-row">
                <WhInput
                  id="settings-email-code"
                  v-model="emailCode"
                  type="text"
                  name="email-code"
                  autocomplete="one-time-code"
                  placeholder="6 位验证码"
                  :maxlength="6"
                  :invalid="Boolean(emailCodeError)"
                  :error="emailCodeError"
                  size="lg"
                />
                <WhButton
                  class="settings-code-button"
                  type="button"
                  size="lg"
                  variant="ghost"
                  :loading="emailSending"
                  :disabled="emailCountdown > 0"
                  @click="sendEmailCode"
                >
                  {{ emailButtonText }}
                </WhButton>
              </div>
              <p class="settings-hint">
                验证码为模拟实现，请到 <code>user-service</code> 日志查看。
              </p>
            </div>

            <div v-if="emailFormError" class="settings-error" role="alert">
              {{ emailFormError }}
            </div>

            <div class="settings-actions">
              <WhButton type="submit" size="lg" :loading="emailSubmitting">
                {{ auth.hasEmail ? '保存新邮箱' : '绑定邮箱' }}
              </WhButton>
            </div>
          </form>
        </section>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.settings-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--space-4);
  max-width: 1080px;
  margin: 0 auto var(--space-6);
}

.settings-eyebrow,
.settings-kicker,
.settings-back,
.settings-hint,
.settings-status,
.settings-profile__meta,
.settings-stats dt,
.settings-field label {
  font-family: var(--mono);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.settings-eyebrow {
  color: var(--verm-deep);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.settings-head h1 {
  margin-top: var(--space-2);
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.settings-sub {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.settings-back {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-size: var(--text-2xs);
}

.settings-back:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.settings-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-6);
  border: var(--border);
  background: var(--surface);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.settings-state__mark {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-sm);
}

.settings-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.settings-state--error .settings-state__mark {
  background: var(--verm);
  color: var(--bone);
}

.settings-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  align-items: start;
  gap: clamp(20px, 3vw, 32px);
  max-width: 1080px;
  margin: 0 auto;
}

.settings-profile {
  position: sticky;
  top: calc(var(--header-h) + 24px);
  display: grid;
  gap: var(--space-4);
  padding: var(--space-6);
  border: 1px solid var(--ink);
  background: var(--surface);
}

.settings-profile__top {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.settings-profile__name {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-tight);
}

.settings-profile__meta {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: var(--text-2xs);
}

.settings-avatar-actions {
  display: flex;
  align-items: center;
}

.settings-file {
  display: none;
}

.settings-hint {
  color: var(--text-muted);
  font-size: var(--text-2xs);
  line-height: var(--leading-snug);
  text-transform: none;
  letter-spacing: 0;
}

.settings-hint code {
  color: var(--ink);
  font-family: var(--mono);
}

.settings-inline-error {
  color: var(--verm-deep);
  font-size: var(--text-xs);
  line-height: var(--leading-snug);
}

.settings-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
  padding-block: var(--space-4);
  border-block: var(--border);
}

.settings-stats div {
  display: grid;
  gap: 2px;
}

.settings-stats dt {
  color: var(--text-muted);
  font-size: var(--text-2xs);
}

.settings-stats dd {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-none);
}

.settings-profile__bio {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.settings-panels {
  display: grid;
  gap: var(--space-5);
}

.settings-card {
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--line-strong);
  background: var(--surface-strong);
}

.settings-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: var(--border);
}

.settings-kicker {
  color: var(--verm-deep);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.settings-card__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.settings-status {
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.settings-status.is-bound {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.settings-card__desc {
  margin-block: var(--space-3) var(--space-5);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.settings-form {
  display: grid;
  gap: var(--space-4);
}

.settings-field {
  display: grid;
  gap: 6px;
}

.settings-field label {
  color: var(--text-muted);
  font-size: var(--text-2xs);
}

.settings-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
  align-items: start;
}

.settings-code-button {
  min-width: 116px;
}

.settings-error {
  padding: var(--space-3) var(--space-4);
  border-left: 3px solid var(--verm);
  background: color-mix(in srgb, var(--verm) 8%, var(--paper));
  color: var(--verm-deep);
  font-size: var(--text-sm);
  line-height: var(--leading-snug);
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-2);
}

@media (max-width: 960px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .settings-profile {
    position: static;
  }
}

@media (max-width: 640px) {
  .settings-head {
    align-items: flex-start;
  }

  .settings-code-row {
    grid-template-columns: 1fr;
  }

  .settings-code-button {
    width: 100%;
  }

  .settings-actions {
    justify-content: stretch;
  }

  .settings-actions :deep(.wh-button) {
    width: 100%;
  }
}
</style>
