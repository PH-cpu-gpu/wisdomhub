<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { FormInstance, FormRules } from 'element-plus'

import { ApiError } from '@/api/http'
import { updateProfile, uploadAvatar } from '@/api/user'
import UserAvatar from '@/components/UserAvatar.vue'
import { useProfile } from '@/composables/useProfile'
import { useUserStore } from '@/stores/user'

/**
 * 我的资料（B1.10 的前端落点）。
 *
 * ⚠️ 这一页是**补出来的**：B1.10 的「改资料 + 传头像」后端早就有了，但前端任务表里
 * F1.6 是公开主页、F1.8 是内容列表，谁都没承载它 —— 于是"个人中心"点进去只能看到列表，
 * 看不到自己的头像与简介。现在它是「个人中心」的默认落点。
 *
 * 两条接口特性决定了这里的写法：
 *   - `PUT /user/profile` 的 `username` **必填**（后端 `@NotBlank`），所以表单不能只提交简介
 *   - 头像**上传即落库**（后端写 `user.avatar`、删旧文件、返回新 URL），所以选完文件就传，
 *     不用再点一次保存；拿到新 URL 后只更新本地缓存，省一次请求
 */
const user = useUserStore()
const { profile, load, setProfile } = useProfile()

const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', bio: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { max: 20, message: '用户名长度不能超过 20', trigger: 'blur' },
  ],
  bio: [{ max: 255, message: '简介长度不能超过 255', trigger: 'blur' }],
}

async function loadProfile() {
  loading.value = true
  // force：进「我的资料」就是要看最新的，不吃别处留下的缓存
  const fresh = await load(true)
  if (fresh) {
    form.username = fresh.username
    form.bio = fresh.bio ?? ''
  }
  loading.value = false
}

async function onSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await updateProfile({ username: form.username.trim(), bio: form.bio.trim() })
    ElMessage.success('资料已更新')
    await loadProfile()
  } catch (e) {
    // 用户名被占用时后端给 409，message 已经说清楚了
    ElMessage.error(e instanceof ApiError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清掉 value：不清的话，再选同一个文件不会触发 change
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片不要超过 2MB')
    return
  }

  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    // 后端已经落库了，前端只要同步本地缓存
    if (profile.value) setProfile({ ...profile.value, avatar: url })
    ElMessage.success('头像已更新')
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '头像上传失败')
  } finally {
    uploading.value = false
  }
}

onMounted(() => void loadProfile())
</script>

<template>
  <div class="mp">
    <header class="mp__head">
      <h1 class="mp__title">我的资料</h1>
      <p class="mp__sub">头像与简介会展示在你的公开主页上。</p>
    </header>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else>
      <div class="mp__card">
        <!-- 头像：点一下就能换。后端上传即落库，所以没有额外的"保存头像"步骤 -->
        <button
          class="mp__avatar"
          type="button"
          :disabled="uploading"
          aria-label="更换头像"
          @click="fileInput?.click()"
        >
          <UserAvatar :src="profile?.avatar" :name="form.username" :size="96" />

          <span class="mp__avatar-mask">{{ uploading ? '上传中…' : '更换头像' }}</span>
        </button>
        <input
          ref="fileInput"
          class="mp__file"
          type="file"
          accept="image/*"
          @change="onFileChange"
        />

        <el-form
          ref="formRef"
          class="mp__form"
          :model="form"
          :rules="rules"
          label-position="top"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" maxlength="20" show-word-limit />
          </el-form-item>

          <el-form-item label="简介" prop="bio">
            <el-input
              v-model="form.bio"
              type="textarea"
              :rows="4"
              maxlength="255"
              show-word-limit
              placeholder="一句话介绍你自己"
            />
          </el-form-item>

          <div class="mp__actions">
            <el-button type="primary" :loading="saving" @click="onSave">保存资料</el-button>
            <RouterLink v-if="user.userId" class="mp__link" :to="`/user/${user.userId}`">
              查看我的公开主页
            </RouterLink>
          </div>
        </el-form>
      </div>

      <dl class="mp__stats wh-mono">
        <div><dt>文章</dt><dd>{{ profile?.articleCount ?? 0 }}</dd></div>
        <div><dt>粉丝</dt><dd>{{ profile?.fansCount ?? 0 }}</dd></div>
        <div><dt>关注</dt><dd>{{ profile?.followCount ?? 0 }}</dd></div>
      </dl>
    </template>
  </div>
</template>

<style scoped>
.mp {
  padding-block: var(--wh-space-6);
}

.mp__head {
  margin-bottom: var(--wh-space-5);
}

.mp__title {
  font-size: var(--wh-text-2xl);
}

.mp__sub {
  margin-top: var(--wh-space-2);
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.mp__card {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-6);
  align-items: flex-start;

  padding: var(--wh-space-5);

  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
}

/* ---------- 头像 ---------- */
/* 头像尺寸由 UserAvatar 组件决定，这里只负责把它包成可点击的按钮 */
.mp__avatar {
  position: relative;

  display: block;
  flex: none;

  padding: 0;

  background: none;
  border: none;
  border-radius: var(--wh-radius-full);
  cursor: pointer;
}

/* 悬停才浮出「更换头像」，平时不打扰 */
.mp__avatar-mask {
  position: absolute;
  inset: 0;

  display: grid;
  place-items: center;

  font-size: var(--wh-text-xs);
  color: var(--wh-text-inverse);
  background-color: rgb(0 0 0 / 55%);
  border-radius: var(--wh-radius-full);
  opacity: 0;

  transition: opacity var(--wh-duration-fast) var(--wh-ease);
}

.mp__avatar:hover .mp__avatar-mask,
.mp__avatar:focus-visible .mp__avatar-mask {
  opacity: 1;
}

.mp__avatar:disabled {
  cursor: default;
  opacity: 0.7;
}

.mp__file {
  display: none;
}

/* ---------- 表单 ---------- */
.mp__form {
  flex: 1 1 16rem;
  min-width: 0;
}

.mp__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-4);
  align-items: center;
}

.mp__link {
  font-size: var(--wh-text-sm);
}

/* ---------- 统计 ---------- */
.mp__stats {
  display: flex;
  gap: var(--wh-space-6);
  margin-top: var(--wh-space-5);

  font-size: var(--wh-text-sm);
}

.mp__stats div {
  display: flex;
  gap: var(--wh-space-1);
  align-items: baseline;
}

.mp__stats dt {
  color: var(--wh-text-3);
}

.mp__stats dd {
  color: var(--wh-text-1);
}
</style>
