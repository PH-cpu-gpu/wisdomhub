<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { listTags, type Tag } from '@/api/tag'
import ArticleList from '@/components/article/ArticleList.vue'

/**
 * F1.5 标签详情：某个标签下的文章列表。
 *
 * 路由用的是**标签名**（`/tag/Spring Boot`，可读、可分享），而文章过滤接口要的是 `tagId` ——
 * 所以这里先取全量标签（后端有两级缓存，很便宜）做一次名字→id 的映射。
 *
 * ⚠️ 名字在路由里是 URL 编码过的，vue-router 会解码，所以直接和 `tag.name` 比较即可
 * —— 别自己 decodeURIComponent，会把「Spring%20Boot」这种再解一次。
 */
const route = useRoute()

const tags = ref<Tag[]>([])
const loading = ref(true)

const tagName = computed(() => String(route.params.name ?? ''))
const tag = computed(() => tags.value.find((item) => item.name === tagName.value) ?? null)

async function loadTags() {
  loading.value = true
  try {
    tags.value = await listTags()
  } catch {
    /* 拉不到就落到下面的「标签不存在」空态，不再叠一层错误提示 */
    tags.value = []
  } finally {
    loading.value = false
  }
}

watch(() => route.params.name, () => {
  // 标签列表基本不变，只有第一次需要真的拉一次
  if (tags.value.length === 0) void loadTags()
})

onMounted(() => void loadTags())
</script>

<template>
  <div class="tag">
    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty v-else-if="!tag" description="没有这个标签">
      <el-button type="primary" @click="$router.push('/tags')">看看全部标签</el-button>
    </el-empty>

    <template v-else>
      <ArticleList :tag-id="tag.tagId" :empty-text="`「${tag.name}」下还没有已发布的文章`">
        <template #heading>
          <div>
            <h1 class="tag__title">{{ tag.name }}</h1>
            <p v-if="tag.description" class="tag__desc">{{ tag.description }}</p>
          </div>
        </template>
      </ArticleList>
    </template>
  </div>
</template>

<style scoped>
.tag {
  padding-block: var(--wh-space-5) var(--wh-space-8);
}

.tag__title {
  font-size: var(--wh-text-page);
}

.tag__desc {
  max-width: 36rem;
  margin-top: var(--wh-space-2);

  font-size: var(--wh-text-base);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-2);
}
</style>
