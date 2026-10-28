<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { tagDetail } from '@/api/modules/knowledge'
import WhButton from '@/components/base/WhButton.vue'
import WhDrawer from '@/components/base/WhDrawer.vue'
import type { TagDetailVO } from '@/types/knowledge'

const RELATION_LABELS: Record<string, string> = {
  BASED_ON: '基于',
  USES: '使用',
  PREREQUISITE: '前置',
  SIMILAR_TO: '相似',
  BELONGS_TO: '属于',
  ABOUT: '相关',
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    tagId: string
    articlesExpanded?: boolean
  }>(),
  {
    articlesExpanded: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  neighborhood: [tagId: string]
  expandArticles: [tagId: string]
}>()

const detail = ref<TagDetailVO | null>(null)
const loading = ref(false)
const error = ref('')

const relationGroups = computed(() =>
  Object.entries(RELATION_LABELS)
    .map(([key, label]) => {
      const group = detail.value?.relations[key] ?? { out: [], in: [] }
      return { key, label, out: group.out, in: group.in }
    })
    .filter((group) => group.out.length > 0 || group.in.length > 0),
)

async function load(): Promise<void> {
  const tagId = props.tagId
  if (!tagId) return

  loading.value = true
  error.value = ''
  detail.value = null

  try {
    detail.value = await tagDetail(tagId, { skipErrorToast: true })
  } catch (err) {
    error.value = isApiError(err) ? err.message : '节点详情加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function close(): void {
  emit('update:modelValue', false)
}

function goNeighborhood(): void {
  emit('neighborhood', props.tagId)
}

function expandArticles(): void {
  emit('expandArticles', props.tagId)
}

watch(
  [() => props.modelValue, () => props.tagId],
  ([open]) => {
    if (open) void load()
    else {
      detail.value = null
      error.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <WhDrawer
    :model-value="modelValue"
    :title="detail?.name ?? '节点详情'"
    width="min(460px, 100vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" class="node-panel__state" aria-live="polite">正在加载节点详情…</div>

    <div v-else-if="error" class="node-panel__state node-panel__state--error" role="alert">
      <p>{{ error }}</p>
      <WhButton size="sm" variant="ghost" @click="load">重试</WhButton>
    </div>

    <div v-else-if="detail" class="node-panel">
      <p class="node-panel__category">[ {{ detail.category }} ]</p>
      <p class="node-panel__description">{{ detail.description }}</p>

      <section v-if="relationGroups.length" class="node-panel__section">
        <h3>关系</h3>
        <div class="node-panel__relations">
          <div v-for="group in relationGroups" :key="group.key" class="node-panel__relation">
            <div class="node-panel__relation-head">
              <span>{{ group.label }}</span>
              <code>{{ group.key }}</code>
            </div>
            <div v-if="group.out.length" class="node-panel__relation-row">
              <span aria-label="出向">→</span>
              <RouterLink
                v-for="tag in group.out"
                :key="`out-${tag.tagId}`"
                :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
              >
                {{ tag.name }}
              </RouterLink>
            </div>
            <div v-if="group.in.length" class="node-panel__relation-row">
              <span aria-label="入向">←</span>
              <RouterLink
                v-for="tag in group.in"
                :key="`in-${tag.tagId}`"
                :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
              >
                {{ tag.name }}
              </RouterLink>
            </div>
          </div>
        </div>
      </section>

      <div class="node-panel__actions">
        <RouterLink
          class="node-panel__tech"
          :to="{ name: 'tech-detail', params: { tagId: detail.tagId } }"
        >
          查看技术页 →
        </RouterLink>
        <WhButton size="sm" variant="ghost" @click="goNeighborhood"> 展开邻域 </WhButton>
        <WhButton size="sm" variant="ghost" :disabled="articlesExpanded" @click="expandArticles">
          {{ articlesExpanded ? '文章已展开' : '展开文章' }}
        </WhButton>
      </div>
    </div>

    <div v-else class="node-panel__state">
      <p>选择图中的技术节点查看详情。</p>
      <button class="node-panel__close" type="button" @click="close">关闭</button>
    </div>
  </WhDrawer>
</template>

<style scoped>
.node-panel {
  display: grid;
  gap: var(--space-4);
}

.node-panel__category {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.node-panel__description {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.node-panel__section {
  display: grid;
  gap: var(--space-2);
}

.node-panel__section h3 {
  padding-bottom: var(--space-2);
  border-bottom: var(--border);
  font-family: var(--serif);
  font-size: var(--text-base);
  font-weight: var(--weight-bold);
}

.node-panel__relations {
  display: grid;
  gap: var(--space-3);
}

.node-panel__relation {
  display: grid;
  gap: var(--space-1);
}

.node-panel__relation-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--text-sm);
}

.node-panel__relation-head code {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.node-panel__relation-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.node-panel__relation-row a {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--ink);
  text-decoration: none;
}

.node-panel__relation-row a:hover {
  border-color: var(--ink);
  background: var(--acid);
}

.node-panel__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: var(--border);
}

.node-panel__tech {
  margin-right: auto;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.node-panel__state {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  padding: var(--space-3);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.node-panel__state--error {
  color: var(--verm-deep);
}

.node-panel__close {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
</style>
