<script setup lang="ts">
import { Bell, Close, Menu, Search } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { primaryNav } from '@/config/navigation'
import { useAuth } from '@/composables/useAuth'
import { useProfile } from '@/composables/useProfile'
import { useUserStore } from '@/stores/user'

import UserAvatar from '@/components/UserAvatar.vue'

import ThemeToggle from './ThemeToggle.vue'

defineProps<{
  /** 侧边栏是否展开。桌面端控制那一列的显隐，窄屏控制抽屉开合。 */
  sidebarOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void
}>()

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const auth = useAuth()
// 资料是共享缓存（App.vue 启动时拉过一次，登录后 useAuth 会再拉一次）
const { profile } = useProfile()

const keyword = ref('')

/** 通知未读数。B2.16 会接真实接口与 WebSocket/SSE，现在恒为占位值。 */
const unreadCount = ref(0)

function isActive(to: string): boolean {
  return route.path === to || route.path.startsWith(`${to}/`)
}

function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  // 搜索页是 F2.1 的产出物，现在跳过去只会看到占位页 —— 但链路是真的
  void router.push({ path: '/search', query: { kw } })
}

async function onUserCommand(command: string) {
  if (command === 'logout') {
    // 真正注销：调后端把两个 token 拉黑（B1.9），后端失败也会清本地（见 useAuth）
    await auth.logout()
    void router.push('/')
    return
  }
  void router.push(command)
}

</script>

<template>
  <header class="hdr">
    <div class="hdr__inner">
      <!-- 窄屏：抽屉开关。宽屏时它变成侧边栏的折叠开关 -->
      <button
        class="hdr__icon-btn hdr__burger"
        type="button"
        :aria-label="sidebarOpen ? '收起侧边栏' : '展开侧边栏'"
        :aria-expanded="sidebarOpen"
        @click="emit('toggle-sidebar')"
      >
        <el-icon><component :is="sidebarOpen ? Close : Menu" /></el-icon>
      </button>

      <RouterLink class="hdr__brand" to="/home">
        <span class="hdr__brand-mark">▍</span>WisdomHub
      </RouterLink>

      <nav class="hdr__nav" aria-label="主导航">
        <RouterLink
          v-for="item in primaryNav"
          :key="item.to"
          class="hdr__nav-link"
          :class="{ 'is-active': isActive(item.to) }"
          :to="item.to"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="hdr__actions">
        <!-- 搜索框：Enter 跳搜索页。搜索结果是 F2.1，这里先把入口和参数打通 -->
        <form class="hdr__search" role="search" @submit.prevent="onSearch">
          <el-input
            v-model="keyword"
            placeholder="搜索技术、文章、问题"
            :prefix-icon="Search"
            clearable
            aria-label="站内搜索"
          />
        </form>

        <!-- 通知入口：未读红点接 B2.16 的未读数 -->
        <RouterLink class="hdr__icon-btn" to="/notifications" aria-label="通知">
          <el-icon><Bell /></el-icon>
          <span v-if="unreadCount > 0" class="hdr__dot" />
        </RouterLink>

        <ThemeToggle />

        <!-- 用户区 -->
        <el-dropdown v-if="user.isLoggedIn" trigger="click" @command="onUserCommand">
          <button class="hdr__avatar" type="button" aria-label="用户菜单">
            <UserAvatar :src="profile?.avatar" :name="profile?.username" :size="30" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/me/profile">个人资料</el-dropdown-item>
              <el-dropdown-item command="/me/articles">我的文章</el-dropdown-item>
              <el-dropdown-item command="/me/favorites">我的收藏</el-dropdown-item>
              <el-dropdown-item
                v-if="user.userId"
                :command="`/user/${user.userId}`"
                divided
              >
                我的公开主页
              </el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button v-else class="hdr__login" type="primary" @click="router.push('/login')">
          登录
        </el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.hdr {
  position: sticky;
  top: 0;
  z-index: var(--wh-z-nav);

  border-bottom: var(--wh-border-width) solid var(--wh-border);
  background-color: var(--wh-bg-base);
}

.hdr__inner {
  display: flex;
  align-items: center;
  gap: var(--wh-space-4);

  max-width: var(--wh-container);
  min-height: var(--wh-nav-height);
  margin-inline: auto;
  padding-inline: var(--wh-space-4);
}

/* ---------- 品牌 ---------- */
.hdr__brand {
  flex: none;

  font-size: var(--wh-text-xl);
  font-weight: var(--wh-weight-semibold);
  letter-spacing: var(--wh-tracking-tight);
  color: var(--wh-text-1);
  text-decoration: none;
}

.hdr__brand:hover {
  color: var(--wh-text-1);
  text-decoration: none;
}

.hdr__brand-mark {
  color: var(--wh-accent);
}

/* ---------- 主导航 ---------- */
.hdr__nav {
  display: flex;
  align-items: center;
  gap: var(--wh-space-1);

  flex: none;
}

.hdr__nav-link {
  padding: var(--wh-space-2) var(--wh-space-3);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-2);
  text-decoration: none;
  border-radius: var(--wh-radius-sm);
  white-space: nowrap;

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    background-color var(--wh-duration-fast) var(--wh-ease);
}

.hdr__nav-link:hover {
  color: var(--wh-text-1);
  background-color: var(--wh-bg-raised);
  text-decoration: none;
}

/* 当前页：底部一条 2px 的主色下划线。用颜色 + 形状双重线索，
   只靠颜色的话色觉障碍用户分不出来 */
.hdr__nav-link.is-active {
  color: var(--wh-accent);
  box-shadow: inset 0 -2px 0 var(--wh-accent);
  border-radius: 0;
}

/* ---------- 右侧动作区 ---------- */
.hdr__actions {
  display: flex;
  align-items: center;
  gap: var(--wh-space-2);

  margin-left: auto; /* 把右侧动作推到最右，搜索框因此贴合内容 */
}

.hdr__search {
  width: 15rem;
}

/* 搜索框默认无边框、用抬升面色；聚焦时才出现主色描边 */
.hdr__search :deep(.el-input__wrapper) {
  background-color: var(--wh-bg-raised);
  box-shadow: none;
}

.hdr__search :deep(.el-input__wrapper:hover) {
  background-color: var(--wh-bg-hover);
}

.hdr__search :deep(.el-input__wrapper.is-focus) {
  background-color: var(--wh-bg-surface);
  box-shadow: inset 0 0 0 1px var(--wh-accent);
}

.hdr__search :deep(.el-input__inner::placeholder) {
  color: var(--wh-text-2);
}

.hdr__icon-btn {
  position: relative;

  display: grid;
  place-items: center;

  width: 32px;
  height: 32px;

  color: var(--wh-text-2);
  background: none;
  border: var(--wh-border-width) solid transparent;
  border-radius: var(--wh-radius-md);
  cursor: pointer;
  text-decoration: none;

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    background-color var(--wh-duration-fast) var(--wh-ease);
}

.hdr__icon-btn:hover {
  color: var(--wh-text-1);
  background-color: var(--wh-bg-raised);
  text-decoration: none;
}

/* 未读红点。真未读数（B2.16）出来前恒不显示，结构先留着。 */
.hdr__dot {
  position: absolute;
  top: 5px;
  right: 5px;

  width: 6px;
  height: 6px;
  border-radius: var(--wh-radius-full);
  background-color: var(--wh-danger);
}

.hdr__avatar {
  display: block;

  margin-left: var(--wh-space-1);
  padding: 0;

  background: none;
  border: none;
  border-radius: var(--wh-radius-full);
  cursor: pointer;
}

/* 窄屏时才显示的抽屉开关 */
.hdr__burger {
  display: none;
}

/* ---------- 响应式 ----------
   900px 以下：主导航与搜索框收进抽屉/下一行，只留品牌 + 图标 + 用户区。
   导航项在移动端由 AppSidebar 的抽屉承载。 */
@media (max-width: 900px) {
  .hdr__burger {
    display: grid;
  }

  .hdr__nav {
    display: none;
  }

  .hdr__search {
    display: none;
  }
}

@media (max-width: 480px) {
  .hdr__login {
    padding-inline: var(--wh-space-2);
  }
}
</style>
