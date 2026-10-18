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
      <div>
        <h1 class="tags__title">标签</h1>
        <p class="tags__lead">按技术主题浏览文章，或从图谱里看它们之间的关系。</p>
      </div>
      <span v-if="!loading" class="tags__total wh-mono">{{ tags.length }} 个标签</span>
    </header>

    <div v-if="loading" class="tags__skeleton" role="status" aria-live="polite">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <section v-if="hotTags.length" class="tags__hot-section">
        <h2 class="tags__hot-title">热门</h2>
        <ul class="tags__hot">
          <li v-for="tag in hotTags" :key="tag.tagId">
            <RouterLink
              class="tags__hot-link"
              :to="{ name: 'tag-detail', params: { name: tag.name } }"
            >
              <span class="tags__hash" aria-hidden="true">#</span>{{ tag.name }}
              <span class="tags__hot-count wh-mono">{{ tag.articleCount }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <div class="tags__groups">
        <section v-for="group in groups" :key="group.category" class="tags__group">
          <h2 class="tags__group-title">
            {{ group.category }}
            <span class="tags__count wh-mono">{{ group.tags.length }}</span>
          </h2>
          <ul class="tags__cloud">
            <li v-for="tag in group.tags" :key="tag.tagId">
              <RouterLink
                class="tags__link"
                :to="{ name: 'tag-detail', params: { name: tag.name } }"
                :title="tag.description"
              >
                <span class="tags__hash" aria-hidden="true">#</span>{{ tag.name }}
              </RouterLink>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tags {
  max-width: var(--wh-container);
  padding-block: var(--wh-space-5) var(--wh-space-8);
}

.tags__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3) var(--wh-space-6);
  align-items: flex-end;
  justify-content: space-between;

  padding-bottom: var(--wh-space-5);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.tags__title {
  font-size: var(--wh-text-page);
}

.tags__lead {
  max-width: 36rem;
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-2);
}

.tags__total {
  padding-bottom: 2px;
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.tags__skeleton {
  padding-top: var(--wh-space-6);
}

/* ---------- 热门 ---------- */
.tags__hot-section {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3) var(--wh-space-6);
  align-items: baseline;

  padding-block: var(--wh-space-5);
}

.tags__hot-title {
  flex: none;

  font-size: var(--wh-text-sm);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

.tags__hot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2) var(--wh-space-5);

  margin: 0;
  padding: 0;
  list-style: none;
}

.tags__hot-link {
  display: inline-flex;
  gap: 4px;
  align-items: baseline;

  font-size: var(--wh-text-base);
  font-weight: var(--wh-weight-medium);
  color: var(--wh-text-1);
  text-decoration: none;
}

.tags__hot-link:hover {
  color: var(--wh-accent);
  text-decoration: none;
}

.tags__hash {
  font-family: var(--wh-font-mono);
  font-size: 0.85em;
  color: var(--wh-text-3);
}

.tags__hot-link:hover .tags__hash {
  color: var(--wh-accent);
}

.tags__hot-count {
  font-size: var(--wh-text-xs);
  font-weight: var(--wh-weight-regular);
  color: var(--wh-text-3);
}

/* ---------- 分组目录 ---------- */
.tags__groups {
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.tags__group {
  display: grid;
  grid-template-columns: 11rem minmax(0, 1fr);
  gap: var(--wh-space-6);
  align-items: start;

  padding-block: var(--wh-space-5);
}

.tags__group + .tags__group {
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.tags__group-title {
  font-size: var(--wh-text-sm);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);
}

.tags__count {
  display: block;
  margin-top: var(--wh-space-1);

  font-size: var(--wh-text-xs);
  font-weight: var(--wh-weight-regular);
  letter-spacing: normal;
  color: var(--wh-text-3);
}

.tags__cloud {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2) var(--wh-space-5);

  margin: 0;
  padding: 0;
  list-style: none;
}

.tags__link {
  display: inline-flex;
  gap: 3px;
  align-items: baseline;

  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-snug);
  color: var(--wh-text-2);
  text-decoration: none;
  transition: color var(--wh-duration-fast) var(--wh-ease);
}

.tags__link:hover {
  color: var(--wh-accent);
  text-decoration: none;
}

.tags__link:hover .tags__hash {
  color: var(--wh-accent);
}

@media (max-width: 700px) {
  .tags__group {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--wh-space-3);
  }

  .tags__count {
    display: inline;
    margin-left: var(--wh-space-2);
  }
}
</style>
