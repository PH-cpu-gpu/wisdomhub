<script setup lang="ts">
import { RouterLink } from 'vue-router'

/**
 * 页脚。
 *
 * 内容按「这个项目现在是什么」来写，不编造公司/备案之类的信息 ——
 * 它现在是一个分四阶段开发的个人项目，页脚就照实说，
 * 顺便让访问者知道哪些能力已经有了、哪些还是占位页。
 */
const stages = [
  { id: '阶段 0', label: '工程骨架与基础设施', done: true },
  { id: '阶段 1', label: '核心社区（文章 / 问答 / 标签）', done: false },
  { id: '阶段 2', label: '搜索 / 知识图谱 / 推荐 / 付费', done: false },
  { id: '阶段 3', label: 'AI 问答 / 学习路线 / 文章分析', done: false },
]
</script>

<template>
  <footer class="ftr">
    <div class="ftr__inner">
      <div class="ftr__col">
        <p class="ftr__brand"><span class="ftr__mark">▍</span>WisdomHub</p>
        <p class="ftr__lede">
          面向开发者的技术知识社区与知识图谱平台。把技术内容变成结构化的知识网络，
          而不只是一堆文章。
        </p>
        <RouterLink class="ftr__link" to="/design">设计系统</RouterLink>
      </div>

      <div class="ftr__col">
        <h2 class="ftr__title">技术栈</h2>
        <p class="ftr__text">
          Spring Cloud Alibaba 微服务 · MySQL · Redis · MongoDB · Elasticsearch ·
          Neo4j · RabbitMQ · LangChain / LangGraph
        </p>
      </div>

      <div class="ftr__col">
        <h2 class="ftr__title">开发进度</h2>
        <ul class="ftr__stages">
          <li v-for="s in stages" :key="s.id" class="ftr__stage">
            <span class="ftr__stage-id wh-mono">{{ s.id }}</span>
            <span :class="['ftr__stage-label', { 'is-done': s.done }]">{{ s.label }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="ftr__bottom">
      <span class="wh-mono">WisdomHub · 2026 · 开发中</span>
    </div>
  </footer>
</template>

<style scoped>
.ftr {
  margin-top: var(--wh-space-8);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.ftr__inner {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1.2fr;
  gap: var(--wh-space-6);

  max-width: var(--wh-container);
  margin-inline: auto;
  padding: var(--wh-space-7) var(--wh-space-4) var(--wh-space-6);
}

.ftr__col {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
}

.ftr__brand {
  font-size: var(--wh-text-base);
  font-weight: var(--wh-weight-semibold);
  color: var(--wh-text-1);
}

.ftr__mark {
  color: var(--wh-accent);
}

.ftr__lede,
.ftr__text {
  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-3);
}

.ftr__title {
  font-size: var(--wh-text-xs);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-2);
}

.ftr__link {
  align-self: flex-start;
  font-size: var(--wh-text-sm);
}

.ftr__stages {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ftr__stage {
  display: grid;
  grid-template-columns: 3.5rem 1fr;
  gap: var(--wh-space-2);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.ftr__stage-id {
  font-size: var(--wh-text-xs);
}

/* 已完成的阶段用主色标出来，未完成的保持弱化 —— 进度是这里唯一要传达的信息 */
.ftr__stage-label.is-done {
  color: var(--wh-accent);
}

.ftr__bottom {
  max-width: var(--wh-container);
  margin-inline: auto;
  padding: var(--wh-space-4);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

@media (max-width: 900px) {
  .ftr__inner {
    grid-template-columns: 1fr;
    gap: var(--wh-space-5);
  }
}
</style>
