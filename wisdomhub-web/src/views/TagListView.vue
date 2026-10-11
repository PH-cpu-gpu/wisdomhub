<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { listHotTags, listTags, type HotTag, type Tag } from '@/api/tag'
import { ApiError } from '@/api/http'
import { groupTags } from '@/config/tag-categories'

/**
 * F1.5 标签页。
 *
 * 数据只有两个只读接口（B1.24）：全量与热门，**都走两级缓存**（Caffeine + Redis）。
 * 后端刻意关掉了标签的增/改/删（普通用户只能查，见 TagController 里的注释），
 * 所以这一页纯展示，没有任何写入口。
 *
 * ⚠️ 标签详情路由用的是**名字**（`/tag/:name`，好看且可分享），而文章过滤要的是 `tagId` ——
 * 那个 name→id 的转换在 TagDetailView 里用这份全量列表做（列表本身是缓存的，很便宜）。
 */
const tags = ref<Tag[]>([])
const hotTags = ref<HotTag[]>([])
const loading = ref(true)

/** 130 条平铺没人看得下去，按 tag.category 切组，组顺序见 config/tag-categories.ts */
const groups = computed(() => groupTags(tags.value))

async function load() {
  loading.value = true
  try {
    // 并行拉两个接口；热门挂了也不该让整页空白
    const [all, hot] = await Promise.all([
      listTags(),
      listHotTags(20).catch(() => [] as HotTag[]),
    ])
    tags.value = all
    hotTags.value = hot
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '标签加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="tags">
    <header class="tags__head">
      <h1 class="tags__title">标签</h1>
      <span class="tags__eyebrow wh-mono">技术主题</span>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <section v-if="hotTags.length" class="tags__section">
        <h2 class="tags__section-title">热门</h2>
        <ul class="tags__hot">
          <li v-for="tag in hotTags" :key="tag.tagId">
            <RouterLink class="tags__hot-item" :to="{ name: 'tag-detail', params: { name: tag.name } }">
              <span class="tags__hot-name">{{ tag.name }}</span>
              <span class="tags__hot-count wh-mono">{{ tag.articleCount }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section v-for="group in groups" :key="group.category" class="tags__section">
        <h2 class="tags__section-title">
          {{ group.category }}
          <span class="tags__count wh-mono">{{ group.tags.length }}</span>
        </h2>
        <ul class="tags__cloud">
          <li v-for="tag in group.tags" :key="tag.tagId">
            <RouterLink
              class="tags__chip"
              :to="{ name: 'tag-detail', params: { name: tag.name } }"
              :title="tag.description"
            >
              {{ tag.name }}
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.tags {
  max-width: var(--wh-container);
  padding-block: var(--wh-space-4);
}

/* 标题与副标签一行，底下一条分隔线 */
.tags__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  padding-bottom: var(--wh-space-3);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.tags__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}

.tags__title {
  font-size: var(--wh-text-xl);
}

.tags__section {
  margin-top: var(--wh-space-5);
}

.tags__section-title {
  display: flex;
  gap: var(--wh-space-2);
  align-items: baseline;

  padding-bottom: var(--wh-space-2);

  font-size: var(--wh-text-lg);
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

/* 分组标题右侧的条数，压到与「热门」那栏的数字同一档 */
.tags__count {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.tags__hot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);

  margin-top: var(--wh-space-4);
  list-style: none;
}

/* 热门项带条数，用等宽字体让数字对齐 */
.tags__hot-item {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: baseline;

  padding: var(--wh-space-2) var(--wh-space-3);

  color: var(--wh-text-1);
  text-decoration: none;
  background-color: var(--wh-bg-raised);
  border-radius: var(--wh-radius-sm);
}

.tags__hot-item:hover {
  color: var(--wh-accent);
}

.tags__hot-count {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.tags__cloud {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2);

  margin-top: var(--wh-space-4);
  list-style: none;
}

.tags__chip {
  display: inline-block;
  padding: var(--wh-space-1) var(--wh-space-3);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-2);
  text-decoration: none;
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-full);

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    border-color var(--wh-duration-fast) var(--wh-ease);
}

.tags__chip:hover {
  color: var(--wh-accent);
  border-color: var(--wh-accent);
}
</style>
