<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * 头像。**图片加载失败一律退化成昵称首字母。**
 *
 * 为什么必须有这个兜底：对象存储的桶默认私有读，图片会 403，而浏览器遇到加载失败的 `<img>`
 * 会渲染一个「破图」小图标 **外加 alt 文字** —— 如果 alt 写的是昵称，用户看到的就是
 * 「破图 + 昵称」两样东西，非常怪。这里 onerror 之后直接换成首字母色块。
 *
 * 三处都在用：顶部导航、我的资料、公开主页。
 */
const props = withDefaults(
  defineProps<{
    src?: string | null
    /** 昵称，用来取首字母兜底 */
    name?: string | null
    size?: number
  }>(),
  { src: null, name: null, size: 32 },
)

const failed = ref(false)

// 换地址就重新试一次（例如刚上传完新头像）
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)

const showImage = computed(() => Boolean(props.src) && !failed.value)
const initial = computed(() => (props.name?.trim().slice(0, 1) || 'U').toUpperCase())
</script>

<template>
  <span
    class="ava"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${Math.max(11, Math.round(size * 0.42))}px`,
    }"
  >
    <img v-if="showImage" :src="src ?? undefined" :alt="name ?? ''" @error="failed = true" />
    <span v-else class="ava__initial wh-mono">{{ initial }}</span>
  </span>
</template>

<style scoped>
.ava {
  display: grid;
  place-items: center;
  overflow: hidden;

  flex: none;

  font-weight: var(--wh-weight-semibold);
  color: var(--wh-text-on-fill);
  background-color: var(--wh-accent);
  border-radius: var(--wh-radius-full);
}

.ava img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
