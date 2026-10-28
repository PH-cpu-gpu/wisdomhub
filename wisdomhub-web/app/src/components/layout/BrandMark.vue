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
    <span class="brand__mark" aria-hidden="true">64</span>
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
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: var(--ink);
  color: var(--acid);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  transform: rotate(-2deg);
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
}

.brand--inverse .brand__name {
  color: var(--bone);
}

.brand--inverse .brand__name b {
  color: var(--acid);
}
</style>
