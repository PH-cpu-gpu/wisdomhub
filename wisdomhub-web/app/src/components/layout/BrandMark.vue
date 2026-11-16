<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(
  defineProps<{
    to?: string
    tone?: 'default' | 'inverse'
  }>(),
  {
    to: '/',
    tone: 'default',
  },
)
</script>

<template>
  <RouterLink class="brand" :class="`brand--${tone}`" :to="to" aria-label="WisdomHub 首页">
    <span class="brand__mark" aria-hidden="true">
      <svg class="brand__glyph" viewBox="0 0 64 64" focusable="false">
        <path d="M9 14 L22 50 L32 26 L42 50 L55 14" fill="none" stroke="currentColor" stroke-width="5.4" stroke-linecap="round" stroke-linejoin="round" />
        <circle cx="9" cy="14" r="7.2" fill="var(--mark-end)" />
        <circle cx="22" cy="50" r="7.2" fill="currentColor" />
        <circle cx="32" cy="26" r="7.2" fill="var(--mark-core)" />
        <circle cx="42" cy="50" r="7.2" fill="currentColor" />
        <circle cx="55" cy="14" r="7.2" fill="var(--mark-end)" />
      </svg>
    </span>
    <span class="brand__name">Wisdom<b>Hub</b></span>
  </RouterLink>
</template>

<style scoped>
.brand {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.brand__mark {
  --mark-end: var(--acid);
  --mark-core: var(--verm);
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: var(--ink);
  color: var(--bone);
  transform: rotate(-2deg);
}

.brand__glyph {
  display: block;
  width: 22px;
  height: 22px;
}

.brand__name {
  color: var(--ink);
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.03em;
}

.brand__name b {
  color: var(--verm-deep);
  font-weight: 900;
}

.brand--inverse .brand__mark {
  background: var(--acid);
  color: var(--ink);
  --mark-end: var(--ink);
  --mark-core: var(--verm-deep);
}

.brand--inverse .brand__name {
  color: var(--bone);
}

.brand--inverse .brand__name b {
  color: var(--acid);
}
</style>
