<script setup lang="ts">
/**
 * 页脚。
 *
 * ⚠️ 面向用户，只放「这个站点是什么」：**不写开发进度、阶段编号、任务号**
 * —— 那些是开发期信息，访问者不关心，也不该看到。
 */
</script>

<template>
  <footer class="ftr">
    <div class="ftr__inner">
      <div class="ftr__main">
        <p class="ftr__brand"><span class="ftr__mark">▍</span>WisdomHub</p>
        <p class="ftr__lede">
          WisdomHub 是一个面向开发者的技术知识社区：在这里写文章、存草稿，发布后按标签归档；评论、点赞、收藏关注的技术内容；提问与回答，采纳最有用的那一条。内容背后的技术实体还会连成一张知识图谱，让「这项技术跟什么有关」看得见。
        </p>
      </div>

      <div class="ftr__meta">
        <span>面向开发者的技术知识社区</span>
        <span class="wh-mono">内容 · 图谱 · AI</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.ftr {
  margin-top: var(--wh-space-8);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.ftr__inner {
  max-width: var(--wh-container);
  margin-inline: auto;
  padding: var(--wh-space-7) var(--wh-space-4) var(--wh-space-6);
}

.ftr__main {
  display: grid;
  grid-template-columns: 12rem minmax(0, 1fr);
  gap: var(--wh-space-6);
  align-items: start;
}

.ftr__brand {
  font-size: var(--wh-text-base);
  font-weight: var(--wh-weight-semibold);
  color: var(--wh-text-1);
}

.ftr__mark {
  color: var(--wh-accent);
}

.ftr__lede {
  max-width: 60rem;

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-reading);
  color: var(--wh-text-2);
}

.ftr__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3) var(--wh-space-6);
  justify-content: space-between;

  margin-top: var(--wh-space-6);
  padding-top: var(--wh-space-4);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

@media (max-width: 700px) {
  .ftr__main {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--wh-space-3);
  }
}
</style>
