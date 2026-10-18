<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// Message 是函数不是组件，不走 SFC 的按需注入，样式得显式引（同 http.ts）
import 'element-plus/es/components/message/style/css'
import type { FormInstance, FormRules } from 'element-plus'

import { sendCodeByEmail, sendCodeByPhone } from '@/api/auth'
import { ApiError } from '@/api/http'
import { ApiCode } from '@/api/types'
import { useAuth } from '@/composables/useAuth'
import { env } from '@/config/env'
import { detectAccount, EMAIL_PATTERN, toAccountPayload } from '@/utils/account'

/**
 * F1.1 登录 / 首次登录引导。
 *
 * 三条产品约定（任务表 F1.1）：
 *   1. **登录框只收手机号或邮箱**，没有用户名登录入口 —— 所以只有一个「账号」输入框，
 *      由 utils/account 按格式判断该传 `email` 还是 `phone`。
 *   2. **首次登录走同一个入口**：登录返回 404（账号不存在）就切到引导态，
 *      在原框里设置用户名与密码建号（后端建号即登录）。
 *   3. **登录成功后若未绑邮箱，提示补绑**（B1.10 的 PUT /api/user/email）。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuth()

type Mode = 'login' | 'register'

const mode = ref<Mode>('login')
const submitting = ref(false)
const sendingCode = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  account: '',
  password: '',
  username: '',
  code: '',
})

const accountKind = computed(() => detectAccount(form.account))
const isRegister = computed(() => mode.value === 'register')

const title = computed(() => (isRegister.value ? '创建账号' : '登录'))
const submitText = computed(() => (isRegister.value ? '创建并登录' : '登录'))

/** 登录只要账号 + 密码；引导态才要用户名与验证码 */
const rules = computed<FormRules>(() => {
  const base: FormRules = {
    account: [
      { required: true, message: '请输入手机号或邮箱', trigger: 'blur' },
      {
        // 参数类型写全：tsconfig 是 strict，留隐式 any 过不了 vue-tsc
        validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
          if (detectAccount(value) === 'unknown') {
            callback(new Error('请输入 11 位手机号，或形如 a@b.com 的邮箱'))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, message: '密码至少 6 位', trigger: 'blur' },
    ],
  }

  if (!isRegister.value) return base

  return {
    ...base,
    username: [
      { required: true, message: '请设置用户名', trigger: 'blur' },
      { max: 20, message: '用户名长度不能超过 20', trigger: 'blur' },
    ],
    code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  }
})

// ---------------------------------------------------------------------------
// 验证码
// ---------------------------------------------------------------------------

const countdown = ref(0)
let timer: number | undefined

function startCountdown() {
  countdown.value = 60
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
})

const canSendCode = computed(
  () => countdown.value === 0 && !sendingCode.value && accountKind.value !== 'unknown',
)

async function onSendCode() {
  sendingCode.value = true
  try {
    const account = form.account.trim()
    // silent：错误由这里自己提示，免得 http.ts 再弹一次
    if (accountKind.value === 'phone') {
      await sendCodeByPhone(account, { silent: true })
    } else {
      await sendCodeByEmail(account, { silent: true })
    }
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

// ---------------------------------------------------------------------------
// 登录 / 注册
// ---------------------------------------------------------------------------

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isRegister.value) {
      await auth.register(
        {
          ...toAccountPayload(form.account),
          code: form.code.trim(),
          username: form.username.trim(),
          password: form.password,
        },
        { silent: true },
      )
      ElMessage.success('账号已创建，欢迎')
    } else {
      await auth.login(form.account, form.password, { silent: true })
      ElMessage.success('登录成功')
    }
    afterAuthenticated()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e

    if (e.code === ApiCode.NOT_FOUND) {
      // 预留分支：技术文档 17.3 原设计是「404 账号不存在 → 前端切注册引导」，
      // 但后端现在统一返回 400「账号或密码错误」（不暴露账号是否存在），
      // 所以**主路径是下面那个显式的「创建账号」入口**。这段留着不占事 —— 契约若变回来即可生效
      mode.value = 'register'
      ElMessage.info('这个账号还没有注册，设置用户名和密码即可创建')
      return
    }
    // 401 密码错误 / 409 用户名与邮箱被占用 / 400 校验失败：后端的 message 已经说得很清楚
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

function switchMode() {
  mode.value = isRegister.value ? 'login' : 'register'
}

/** 登录成功后的落点：优先回 401 拦截器记下的 redirect，否则首页 */
function redirectTarget(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/'
}

function afterAuthenticated() {
  if (auth.hasEmail.value === false) {
    showBind.value = true
    return
  }
  void router.replace(redirectTarget())
}

// ---------------------------------------------------------------------------
// 补绑邮箱（F1.1：登录成功后提示）
// ---------------------------------------------------------------------------

const showBind = ref(false)
const binding = ref(false)
const bindFormRef = ref<FormInstance>()
const bindSendingCode = ref(false)
const bindCountdown = ref(0)
const bindForm = reactive({ email: '', code: '' })
let bindTimer: number | undefined

const bindRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { pattern: EMAIL_PATTERN, message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function onBindSendCode() {
  const email = bindForm.email.trim()
  if (!EMAIL_PATTERN.test(email)) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  bindSendingCode.value = true
  try {
    await sendCodeByEmail(email, { silent: true })
    ElMessage.success('验证码已发送')
    bindCountdown.value = 60
    bindTimer = window.setInterval(() => {
      bindCountdown.value -= 1
      if (bindCountdown.value <= 0 && bindTimer !== undefined) {
        window.clearInterval(bindTimer)
        bindTimer = undefined
      }
    }, 1000)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '验证码发送失败')
  } finally {
    bindSendingCode.value = false
  }
}

async function onBindSubmit() {
  const valid = await bindFormRef.value?.validate().catch(() => false)
  if (!valid) return

  binding.value = true
  try {
    await auth.bindEmail(bindForm.email.trim(), bindForm.code.trim())
    ElMessage.success('邮箱已绑定')
    finishBind()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '绑定失败')
  } finally {
    binding.value = false
  }
}

function finishBind() {
  showBind.value = false
  if (bindTimer !== undefined) {
    window.clearInterval(bindTimer)
    bindTimer = undefined
  }
  void router.replace(redirectTarget())
}
</script>

<template>
  <div class="auth">
    <div class="auth__card">
      <p class="auth__brand"><span class="auth__mark" aria-hidden="true">▍</span>WisdomHub</p>
      <h1 class="auth__title">{{ title }}</h1>
      <p class="auth__sub">
        <template v-if="isRegister">
          这个账号还没有注册。设置用户名与密码即可创建，<strong>建号即登录</strong>。
        </template>
        <template v-else>用手机号或邮箱登录，没有用户名登录入口。</template>
      </p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="手机号 / 邮箱" prop="account">
          <el-input
            v-model="form.account"
            placeholder="13800000000 或 a@b.com"
            autocomplete="username"
            clearable
          />
        </el-form-item>

        <template v-if="isRegister">
          <el-form-item label="验证码" prop="code">
            <div class="auth__code-row">
              <el-input v-model="form.code" placeholder="6 位验证码" autocomplete="one-time-code" />
              <el-button :disabled="!canSendCode" :loading="sendingCode" @click="onSendCode">
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="登录后展示的名字，最长 20 字" />
          </el-form-item>
        </template>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 6 位"
            :autocomplete="isRegister ? 'new-password' : 'current-password'"
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button
          class="auth__submit"
          type="primary"
          :loading="submitting"
          native-type="submit"
          @click="onSubmit"
        >
          {{ submitText }}
        </el-button>
      </el-form>

      <p v-if="env.isDev && isRegister" class="auth__dev-hint">
        开发环境：验证码是<strong>模拟实现</strong>，只打进 user-service 日志（不会真发邮件/短信）。
      </p>

      <div class="auth__switch">
        <span>{{ isRegister ? '已有账号？' : '第一次来？' }}</span>
        <button class="auth__link" type="button" @click="switchMode">
          {{ isRegister ? '返回登录' : '创建账号' }}
        </button>
      </div>
    </div>

    <p class="auth__foot">内容社区 · 知识图谱 · AI 导师</p>

    <!-- 补绑邮箱：登录成功后若账号还没有邮箱，先问一次，可以跳过 -->
    <el-dialog
      v-model="showBind"
      title="补绑邮箱"
      width="420px"
      :close-on-click-modal="false"
      @closed="finishBind"
    >
      <p class="auth__bind-hint">
        绑上邮箱之后可以用邮箱登录，账号也更安全（手机号目前还不支持绑定）。这一步可以跳过。
      </p>
      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-position="top">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="bindForm.email" placeholder="a@b.com" />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="auth__code-row">
            <el-input v-model="bindForm.code" placeholder="6 位验证码" />
            <el-button
              :disabled="bindCountdown > 0 || bindSendingCode"
              :loading="bindSendingCode"
              @click="onBindSendCode"
            >
              {{ bindCountdown > 0 ? `${bindCountdown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishBind">稍后再说</el-button>
        <el-button type="primary" :loading="binding" @click="onBindSubmit">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.auth {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-5);
  align-items: center;
  justify-content: center;

  min-height: 100dvh;
  padding: var(--wh-space-6) var(--wh-space-4);
}

.auth__card {
  width: 100%;
  max-width: 26rem;
  padding: var(--wh-space-7) var(--wh-space-6);

  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  box-shadow: var(--wh-surface-highlight), var(--wh-shadow-float);
}

.auth__brand {
  font-size: var(--wh-text-base);
  font-weight: var(--wh-weight-semibold);
  letter-spacing: var(--wh-tracking-tight);
  color: var(--wh-text-1);
}

.auth__mark {
  margin-right: 2px;
  color: var(--wh-accent);
}

.auth__title {
  margin-top: var(--wh-space-5);
  font-size: var(--wh-text-page);
}

.auth__sub {
  margin: var(--wh-space-2) 0 var(--wh-space-6);

  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-2);
}

.auth__sub strong {
  color: var(--wh-text-1);
}

.auth__card :deep(.el-input__wrapper) {
  min-height: 40px;
}

.auth__code-row {
  display: flex;
  gap: var(--wh-space-2);
  width: 100%;
}

.auth__code-row :deep(.el-button) {
  height: 40px;
}

.auth__submit {
  width: 100%;
  height: 40px;
  margin-top: var(--wh-space-2);
}

.auth__dev-hint {
  margin-top: var(--wh-space-4);
  padding: var(--wh-space-3);

  font-size: var(--wh-text-xs);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-3);
  background-color: var(--wh-bg-raised);
  border-left: var(--wh-border-width) solid var(--wh-warning);
  border-radius: var(--wh-radius-xs);
}

.auth__dev-hint strong {
  color: var(--wh-warning);
}

.auth__switch {
  display: flex;
  gap: var(--wh-space-2);
  justify-content: center;

  margin-top: var(--wh-space-5);
  padding-top: var(--wh-space-4);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.auth__link {
  padding: 0;

  font-size: var(--wh-text-sm);
  color: var(--wh-accent);
  background: none;
  border: none;
  cursor: pointer;
}

.auth__link:hover {
  color: var(--wh-accent-hover);
  text-decoration: underline;
}

.auth__bind-hint {
  margin-bottom: var(--wh-space-4);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-3);
}

.auth__foot {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

@media (max-width: 480px) {
  .auth__card {
    padding: var(--wh-space-6) var(--wh-space-5);
  }
}
</style>
