<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { toast } from '@/components/base/toast'
import { useNotificationChannel } from '@/composables/useNotificationChannel'
import { useAuthStore, useNotificationStore } from '@/stores'

import BrandMark from './BrandMark.vue'
import GlobalSearch from './GlobalSearch.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()
useNotificationChannel()

const menuOpen = ref(false)
const scrolled = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)
const mobilePanel = ref<HTMLElement | null>(null)
const loggingOut = ref(false)

const showUnread = computed(() => auth.isLoggedIn() && notification.unreadCount > 0)
const unreadText = computed(() =>
  notification.unreadCount > 99 ? '99+' : String(notification.unreadCount),
)
const notificationLabel = computed(() =>
  showUnread.value ? `通知，${notification.unreadCount} 条未读` : '通知',
)

const navItems = [
  { key: 'home', label: '首页', to: '/home' },
  { key: 'articles', label: '文章', to: '/articles' },
  { key: 'questions', label: '问答', to: '/questions' },
  { key: 'tech', label: '技术', to: '/tags' },
  { key: 'graph', label: '图谱', to: '/graph' },
  { key: 'ai', label: 'AI', to: '/ai' },
] as const

const activeNav = computed(() => route.meta.nav)
const isLoginPage = computed(() => route.name === 'login')
const isRegisterPage = computed(() => route.name === 'register')

const redirectTarget = computed(() => {
  const existing = route.query.redirect
  return typeof existing === 'string' && existing ? existing : route.fullPath
})

const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: redirectTarget.value },
}))
const registerTarget = computed(() => ({
  name: 'register',
  query: { redirect: redirectTarget.value },
}))

const accountTarget = computed(() => {
  if (!auth.userId || !auth.isLoggedIn()) return null
  return { name: 'user-profile', params: { userId: auth.userId } }
})

async function handleLogout(): Promise<void> {
  if (loggingOut.value) return

  loggingOut.value = true
  try {
    await auth.logout()
    notification.reset()
    toast.success('已退出登录')
    closeMenu()
    if (route.meta.requiresAuth) await router.replace({ name: 'home' })
  } finally {
    loggingOut.value = false
  }
}

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function closeMenu(): void {
  menuOpen.value = false
}

watch(menuOpen, async (open) => {
  document.body.classList.toggle('menu-open', open)
  if (open) {
    await nextTick()
    mobilePanel.value?.querySelector<HTMLElement>('a[href], button:not([disabled])')?.focus()
  } else {
    menuButton.value?.focus()
  }
})

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)

function onKeydown(event: KeyboardEvent): void {
  if (!menuOpen.value) return

  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu()
    return
  }

  if (event.key !== 'Tab') return

  const focusables = mobilePanel.value?.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled])',
  )
  if (!focusables?.length) return

  const first = focusables[0]
  const last = focusables[focusables.length - 1]

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function onResize(): void {
  if (window.innerWidth >= 960) menuOpen.value = false
}

function onScroll(): void {
  scrolled.value = window.scrollY > 8
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onResize)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('scroll', onScroll)
  document.body.classList.remove('menu-open')
})
</script>

<template>
  <header class="app-header" :class="{ 'is-scrolled': scrolled }">
    <div class="wh-container app-header__inner">
      <BrandMark to="/home" @click="closeMenu" />

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.key"
          class="nav__link"
          :class="{ 'is-active': activeNav === item.key }"
          :to="item.to"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <GlobalSearch />

        <RouterLink
          class="icon-button icon-button--badge"
          to="/notifications"
          :aria-label="notificationLabel"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.7 21a2 2 0 0 1-3.4 0" />
          </svg>
          <span v-if="showUnread" class="icon-badge" aria-hidden="true">{{ unreadText }}</span>
        </RouterLink>

        <RouterLink class="cta" to="/editor/new">写文章</RouterLink>

        <RouterLink v-if="!accountTarget && !isLoginPage" class="login" :to="loginTarget">
          登录
        </RouterLink>
        <RouterLink v-if="!accountTarget && !isRegisterPage" class="register" :to="registerTarget">
          注册
        </RouterLink>
        <RouterLink v-if="accountTarget" class="account" :to="accountTarget || '/'">
          我的
        </RouterLink>
        <button
          v-if="accountTarget"
          class="logout"
          type="button"
          :disabled="loggingOut"
          @click="handleLogout"
        >
          {{ loggingOut ? '退出中…' : '退出' }}
        </button>
      </div>

      <div class="mobile-actions">
        <RouterLink class="icon-button" to="/search" aria-label="搜索">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            aria-hidden="true"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" stroke-linecap="round" />
          </svg>
        </RouterLink>

        <button
          ref="menuButton"
          class="menu-button"
          type="button"
          :aria-expanded="menuOpen"
          aria-controls="mobile-menu"
          :aria-label="menuOpen ? '关闭菜单' : '打开菜单'"
          @click="toggleMenu"
        >
          <span v-if="menuOpen" class="menu-button__close" aria-hidden="true">×</span>
          <span v-else class="menu-button__lines" aria-hidden="true">
            <span></span>
            <span></span>
          </span>
        </button>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="menu">
        <div
          v-if="menuOpen"
          id="mobile-menu"
          ref="mobilePanel"
          class="mobile-menu"
          role="dialog"
          aria-modal="true"
          aria-label="站点导航"
        >
          <nav class="mobile-menu__nav" aria-label="移动端主导航">
            <RouterLink
              v-for="(item, index) in navItems"
              :key="item.key"
              class="mobile-menu__link"
              :class="{ 'is-active': activeNav === item.key }"
              :to="item.to"
              @click="closeMenu"
            >
              <span class="mobile-menu__index">0{{ index + 1 }}</span>
              <span>{{ item.label }}</span>
              <span class="mobile-menu__arrow" aria-hidden="true">↗</span>
            </RouterLink>
          </nav>

          <div class="mobile-menu__actions">
            <RouterLink class="mobile-menu__cta" to="/editor/new" @click="closeMenu">
              写文章
            </RouterLink>
            <RouterLink
              class="mobile-menu__ghost"
              to="/notifications"
              :aria-label="notificationLabel"
              @click="closeMenu"
            >
              通知
              <span v-if="showUnread" class="mobile-menu__badge" aria-hidden="true">
                {{ unreadText }}
              </span>
            </RouterLink>
            <RouterLink
              v-if="!accountTarget && !isLoginPage"
              class="mobile-menu__ghost"
              :to="loginTarget"
              @click="closeMenu"
            >
              登录
            </RouterLink>
            <RouterLink
              v-if="!accountTarget && !isRegisterPage"
              class="mobile-menu__ghost"
              :to="registerTarget"
              @click="closeMenu"
            >
              注册
            </RouterLink>
            <RouterLink
              v-if="accountTarget"
              class="mobile-menu__ghost"
              :to="accountTarget || '/'"
              @click="closeMenu"
            >
              我的
            </RouterLink>
            <button
              v-if="accountTarget"
              class="mobile-menu__ghost mobile-menu__logout"
              type="button"
              :disabled="loggingOut"
              @click="handleLogout"
            >
              {{ loggingOut ? '退出中…' : '退出登录' }}
            </button>
          </div>

          <p class="mobile-menu__meta">WISDOMHUB · KNOWLEDGE IS A NETWORK</p>
        </div>
      </Transition>
    </Teleport>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  background: color-mix(in srgb, var(--bone) 93%, transparent);
  border-bottom: 1px solid var(--line);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  transition: box-shadow var(--dur) var(--ease);
}

.app-header.is-scrolled {
  box-shadow: var(--shadow-sm);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: clamp(16px, 2.4vw, 32px);
  height: var(--header-h);
}

.nav {
  display: flex;
  align-items: center;
  gap: clamp(14px, 2vw, 28px);
  margin-left: auto;
}

.nav__link {
  position: relative;
  padding-block: 6px;
  color: color-mix(in srgb, var(--ink) 68%, transparent);
  font-size: var(--text-sm);
  letter-spacing: 0.05em;
  transition: color var(--dur) var(--ease);
}

.nav__link::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--verm);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--dur) var(--ease);
}

.nav__link:hover,
.nav__link.is-active {
  color: var(--ink);
}

.nav__link.is-active::after {
  transform: scaleX(1);
}

.actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.icon-button {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid transparent;
  color: var(--ink);
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.icon-button:hover {
  border-color: var(--line-strong);
  background: var(--surface);
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

.icon-badge {
  position: absolute;
  top: 0;
  right: -1px;
  display: grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding-inline: 4px;
  border: 1px solid var(--bone);
  background: var(--verm);
  color: var(--text-inverse);
  font-family: var(--mono);
  font-size: 9px;
  line-height: 1;
  pointer-events: none;
}

.cta,
.register {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 13px;
  border: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  white-space: nowrap;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease),
    transform var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.cta {
  background: var(--ink);
  color: var(--acid);
}

.cta:hover {
  background: var(--acid);
  color: var(--ink);
  box-shadow: var(--shadow-hard-sm);
  transform: translate(-1px, -1px);
}

.register {
  background: var(--acid);
  color: var(--ink);
}

.register:hover {
  background: var(--ink);
  color: var(--acid);
}

.account {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 13px;
  border: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  white-space: nowrap;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.account:hover {
  background: var(--ink);
  color: var(--acid);
}

.logout {
  padding: 8px 6px;
  color: color-mix(in srgb, var(--ink) 65%, transparent);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.logout:hover:not(:disabled) {
  color: var(--verm-deep);
}

.logout:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.login {
  padding: 8px 6px;
  color: color-mix(in srgb, var(--ink) 65%, transparent);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.login:hover {
  color: var(--verm-deep);
}

.mobile-actions {
  display: none;
  align-items: center;
  gap: var(--space-1);
  margin-left: auto;
}

.menu-button {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--line-strong);
  color: var(--ink);
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.menu-button:hover {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.menu-button__lines {
  display: grid;
  gap: 5px;
}

.menu-button__lines span {
  display: block;
  width: 16px;
  height: 1.5px;
  background: currentColor;
}

.menu-button__close {
  font-family: var(--sans);
  font-size: 24px;
  line-height: 1;
}

.mobile-menu {
  position: fixed;
  inset: var(--header-h) 0 0;
  z-index: var(--z-drawer);
  display: flex;
  flex-direction: column;
  gap: var(--space-8);
  padding: var(--space-6) var(--pad) var(--space-8);
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--bg);
  border-top: 1px solid var(--line);
}

.mobile-menu__nav {
  display: flex;
  flex-direction: column;
}

.mobile-menu__link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) 0;
  border-bottom: var(--border);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.mobile-menu__link:hover {
  color: var(--verm-deep);
}

.mobile-menu__link:focus-visible {
  outline: none;
  box-shadow: inset 3px 0 0 var(--verm);
}

.mobile-menu__index {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-regular);
  letter-spacing: var(--tracking-wide);
  color: var(--verm);
}

.mobile-menu__link.is-active .mobile-menu__index {
  padding: 1px 4px;
  background: var(--acid);
  color: var(--ink);
}

.mobile-menu__arrow {
  margin-left: auto;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-sm);
  font-weight: var(--weight-regular);
}

.mobile-menu__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
}

.mobile-menu__cta,
.mobile-menu__ghost {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  border: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: 0.1em;
}

.mobile-menu__logout {
  grid-column: 1 / -1;
  width: 100%;
}

.mobile-menu__cta {
  grid-column: 1 / -1;
  background: var(--acid);
  color: var(--ink);
}

.mobile-menu__ghost {
  background: transparent;
  color: var(--ink);
}

.mobile-menu__badge {
  display: inline-grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  margin-left: 6px;
  padding-inline: 4px;
  background: var(--verm);
  color: var(--text-inverse);
  font-family: var(--mono);
  font-size: 9px;
  line-height: 1;
}

.mobile-menu__cta:hover,
.mobile-menu__ghost:hover {
  background: var(--ink);
  color: var(--acid);
}

.mobile-menu__meta {
  margin-top: auto;
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
  color: var(--text-muted);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 959px) {
  .nav,
  .actions {
    display: none;
  }

  .mobile-actions {
    display: flex;
  }
}

@media (min-width: 960px) {
  .mobile-menu {
    display: none;
  }
}
</style>
