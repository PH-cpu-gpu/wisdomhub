<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { create } from '@/api/modules/question'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'

const router = useRouter()

const title = ref('')
const content = ref('')
const submitting = ref(false)

const canSubmit = computed(
  () => Boolean(title.value.trim() && content.value.trim()) && !submitting.value,
)

async function submitQuestion(): Promise<void> {
  if (submitting.value) return

  const trimmedTitle = title.value.trim()
  const trimmedContent = content.value.trim()
  if (!trimmedTitle) {
    toast.info('请填写标题')
    return
  }
  if (trimmedTitle.length > 100) {
    toast.info('标题不能超过 100 字')
    return
  }
  if (!trimmedContent) {
    toast.info('请填写问题描述')
    return
  }
  if (trimmedContent.length > 10000) {
    toast.info('问题描述不能超过 10000 字')
    return
  }

  submitting.value = true
  try {
    const questionId = await create(
      { title: trimmedTitle, content: trimmedContent },
      { skipErrorToast: true },
    )
    toast.success('问题已发布')
    await router.push({
      name: 'question-detail',
      params: { questionId },
    })
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '提问失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

useSeo({
  title: '提问',
  description: '填写标题与问题描述，发布后进入问题详情。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="ask-page">
      <header class="ask-head">
        <div>
          <p class="ask-eyebrow">[ Q&amp;A / ASK ]</p>
          <h1>提问</h1>
          <p class="ask-lede">补充背景、已尝试的方案与期望结果，更容易得到有效回答。</p>
        </div>
        <RouterLink class="ask-back" :to="{ name: 'question-list' }"> ← 返回问答 </RouterLink>
      </header>

      <form class="ask-form" @submit.prevent="submitQuestion">
        <label class="ask-field">
          <span class="ask-field__label">
            <span>标题</span>
            <span>{{ title.length }}/100</span>
          </span>
          <WhInput
            v-model="title"
            :maxlength="100"
            placeholder="一句话概括你的问题"
            aria-label="问题标题"
          />
        </label>

        <label class="ask-field">
          <span class="ask-field__label">
            <span>问题描述</span>
            <span>{{ content.length }}/10000</span>
          </span>
          <WhTextarea
            v-model="content"
            :maxlength="10000"
            :rows="10"
            placeholder="补充背景、已尝试的方案与期望结果…"
            aria-label="问题描述"
          />
        </label>

        <div class="ask-actions">
          <WhButton type="submit" :loading="submitting" :disabled="!canSubmit">
            {{ submitting ? '发布中…' : '发布问题' }}
          </WhButton>
          <span class="ask-hint">发布成功后跳转问题详情。</span>
        </div>
      </form>
    </div>
  </PageContainer>
</template>

<style scoped>
.ask-page {
  display: grid;
  gap: var(--space-5);
  max-width: 840px;
  margin: 0 auto;
}

.ask-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.ask-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.ask-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.ask-lede {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.ask-back {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.ask-back:hover {
  color: var(--verm-deep);
}

.ask-form {
  display: grid;
  gap: var(--space-5);
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-hard-sm);
}

.ask-field {
  display: grid;
  gap: var(--space-2);
}

.ask-field__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.ask-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.ask-hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .ask-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .ask-form {
    box-shadow: none;
  }

  .ask-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
