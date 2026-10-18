<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'

/** 开始页：站点入口，点「快速开始」进入主界面。 */
const router = useRouter()

function launch() {
  void router.push('/home')
}
</script>

<template>
  <div class="start">
    <div class="start__inner">
      <p class="start__brand">
        <span class="start__mark" aria-hidden="true">▍</span>WisdomHub
        <span class="start__cursor" aria-hidden="true" />
      </p>

      <h1 class="start__title">
        技术知识，连成<span class="start__accent">一张图</span>。
      </h1>

      <p class="start__lede">
        写文章、提问题，内容自动连成知识图谱；<span class="start__nowrap">AI 导师</span>
        沿着图谱回答你的问题。
      </p>

      <div class="start__actions">
        <el-button class="start__launch" type="primary" size="large" @click="launch">
          快速开始
          <svg class="start__arrow" viewBox="0 0 16 16" aria-hidden="true">
            <path
              d="M3 8h9M8.5 4.5 12 8l-3.5 3.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </el-button>

        <p class="start__login">已有账号？<RouterLink to="/login">直接登录</RouterLink></p>
      </div>
    </div>

    <p class="start__foot">内容社区 · 知识图谱 · AI 导师</p>
  </div>
</template>

<style scoped>
.start {
  position: relative;
  display: flex;
  flex-direction: column;

  min-height: 100dvh;
  padding: var(--wh-space-7) var(--wh-space-4) var(--wh-space-5);
  overflow: hidden;
}

/* 点阵网格，呼应「暗色终端」的定位；用 mask 让它在边缘淡出、不压内容 */
.start::before {
  content: '';
  position: absolute;
  inset: 0;

  background-image: radial-gradient(var(--wh-border) 1px, transparent 1px);
  background-size: 26px 26px;
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 40%, black 0%, transparent 75%);
  mask-image: radial-gradient(ellipse 70% 60% at 50% 40%, black 0%, transparent 75%);
  pointer-events: none;
}

.start__inner {
  position: relative;
  margin: auto;

  max-width: 44rem;
  text-align: center;
}

.start__brand {
  font-family: var(--wh-font-mono);
  font-size: var(--wh-text-lg);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-1);
}

.start__mark {
  margin-right: 2px;
  color: var(--wh-accent);
}

.start__cursor {
  display: inline-block;
  width: 0.6em;
  height: 1.1em;
  margin-left: 4px;
  vertical-align: -0.15em;

  background-color: var(--wh-accent);
  animation: start-blink 1.1s steps(1) infinite;
}

@keyframes start-blink {
  50% {
    opacity: 0;
  }
}

.start__title {
  margin-top: var(--wh-space-6);

  font-size: var(--wh-text-3xl);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-tight);
  letter-spacing: var(--wh-tracking-tight);
}

.start__accent {
  color: var(--wh-accent);
}

.start__lede {
  max-width: 40rem;
  margin-top: var(--wh-space-4);

  font-size: var(--wh-text-reading);
  line-height: var(--wh-leading-reading);
  color: var(--wh-text-2);
  text-wrap: balance;
}

.start__nowrap {
  white-space: nowrap;
}

.start__actions {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-4);
  align-items: center;

  margin-top: var(--wh-space-7);
}

.start__launch {
  height: 48px;
  padding-inline: var(--wh-space-7);

  font-size: var(--wh-text-lg);
}

.start__arrow {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-left: var(--wh-space-2);
  vertical-align: -0.2em;

  transition: transform var(--wh-duration-fast) var(--wh-ease);
}

.start__launch:hover .start__arrow {
  transform: translateX(2px);
}

.start__login {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.start__foot {
  text-align: center;

  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

@media (max-width: 600px) {
  .start__title {
    font-size: var(--wh-text-page);
  }

  .start__launch {
    width: 100%;
  }
}
</style>
