<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { FormInstance, FormRules } from 'element-plus'

import { ApiError } from '@/api/http'
import { createQuestion } from '@/api/question'

/**
 * F1.7 提问。
 *
 * ⚠️ **提问是「创建类」，不幂等**（技术文档 17.3）—— 重复提交真的会建两个问题。
 * 所以按钮在提交期间必须禁用（`loading` 就是禁用），不要指望后端去重。
 *
 * 正文是**纯文本**（TEXT），不走 Markdown —— 后端的问答正文没有渲染器，
 * 详情页也是按 `pre-wrap` 原样显示。所以这里只是多行输入框，不是编辑器。
 */
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({ title: '', content: '' })

const rules: FormRules = {
  title: [
    { required: true, message: '请输入问题标题', trigger: 'blur' },
    { max: 100, message: '标题长度不能超过 100', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const questionId = await createQuestion({
      title: form.title.trim(),
      content: form.content.trim(),
    })
    ElMessage.success('提问成功')
    void router.replace(`/question/${questionId}`)
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '提问失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="ask">
    <header class="ask__head">
      <p class="ask__eyebrow wh-mono">技术问答</p>
      <h1 class="ask__title">提问</h1>
      <p class="ask__sub">把问题描述清楚，注意到什么、试过什么、卡在哪里。</p>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话说清问题" />
      </el-form-item>

      <el-form-item label="描述" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="10"
          maxlength="10000"
          show-word-limit
          placeholder="复现步骤、报错信息、你已经排除的可能……&#10;&#10;（纯文本，不上 Markdown）"
        />
      </el-form-item>

      <div class="ask__actions">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">发布问题</el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
/* 表单页例外：窄一点更好填，沿用阅读页的宽度令牌 */
.ask {
  max-width: var(--wh-reading);
  padding-block: var(--wh-space-6);
}

.ask__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}

.ask__title {
  font-size: var(--wh-text-2xl);
}

.ask__sub {
  margin: var(--wh-space-2) 0 var(--wh-space-6);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.ask__actions {
  display: flex;
  gap: var(--wh-space-3);
  justify-content: flex-end;
}
</style>
