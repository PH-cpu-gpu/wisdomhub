<script setup lang="ts">
const apiBase = import.meta.env.VITE_API_BASE_URL || '同源 /api'

const swatches = [
  { name: 'Bone', token: '--bone', value: '#F2EDDF', dark: true },
  { name: 'Ink', token: '--ink', value: '#0B0B0E', dark: false },
  { name: 'Acid', token: '--acid', value: '#C9FF2E', dark: true },
  { name: 'Vermilion', token: '--verm', value: '#FF3B1F', dark: true },
  { name: 'Violet', token: '--violet', value: '#3B1DFF', dark: false },
]

const typeScale = [
  {
    label: 'display',
    token: '--text-display',
    sample: '知识连成网',
    family: 'var(--serif)',
    leading: 'var(--leading-none)',
    tracking: 'var(--tracking-tight)',
  },
  {
    label: 'h1 / 3xl',
    token: '--text-3xl',
    sample: '技术知识社区',
    family: 'var(--serif)',
    leading: 'var(--leading-tight)',
    tracking: 'var(--tracking-tight)',
  },
  {
    label: 'h2 / 2xl',
    token: '--text-2xl',
    sample: '文章与问答',
    family: 'var(--serif)',
    leading: 'var(--leading-tight)',
    tracking: 'var(--tracking-tight)',
  },
  {
    label: 'body / md',
    token: '--text-md',
    sample: '文章、问答、搜索、图谱与 AI，共用同一张知识网络。',
    family: 'var(--sans)',
    leading: 'var(--leading-normal)',
    tracking: 'var(--tracking-normal)',
  },
  {
    label: 'label / xs',
    token: '--text-xs',
    sample: 'KNOWLEDGE / NETWORK / 2026',
    family: 'var(--mono)',
    leading: 'var(--leading-snug)',
    tracking: 'var(--tracking-wide)',
  },
]

const spaces = [1, 2, 3, 4, 6, 8, 12, 16]
</script>

<template>
  <div class="specimen">
    <div class="wh-container">
      <header class="specimen__head">
        <p class="specimen__eyebrow">[ F0.2 / DESIGN TOKENS ]</p>
        <h1 class="specimen__title">WisdomHub<span>.</span></h1>
        <p class="specimen__lede">
          颜色、字体、间距与断点已收敛为一份令牌。F0.4 会把这里扩展为组件库预览。
        </p>
      </header>

      <section class="specimen__section" aria-labelledby="specimen-color">
        <div class="specimen__section-head">
          <h2 id="specimen-color">颜色</h2>
          <span class="specimen__meta">raw palette → semantic aliases</span>
        </div>
        <ul class="swatches">
          <li
            v-for="swatch in swatches"
            :key="swatch.token"
            class="swatch"
            :style="{
              background: `var(${swatch.token})`,
              color: swatch.dark ? 'var(--ink)' : 'var(--bone)',
            }"
          >
            <span class="swatch__name">{{ swatch.name }}</span>
            <span class="swatch__meta">{{ swatch.token }}</span>
            <span class="swatch__value">{{ swatch.value }}</span>
          </li>
        </ul>
      </section>

      <section class="specimen__section" aria-labelledby="specimen-type">
        <div class="specimen__section-head">
          <h2 id="specimen-type">字体</h2>
          <span class="specimen__meta">serif · sans · mono</span>
        </div>
        <dl class="type-scale">
          <div v-for="item in typeScale" :key="item.token" class="type-scale__row">
            <dt class="type-scale__label">
              {{ item.label }}
              <span>{{ item.token }}</span>
            </dt>
            <dd
              class="type-scale__sample"
              :style="{
                fontSize: `var(${item.token})`,
                fontFamily: item.family,
                lineHeight: item.leading,
                letterSpacing: item.tracking,
              }"
            >
              {{ item.sample }}
            </dd>
          </div>
        </dl>
      </section>

      <section class="specimen__section" aria-labelledby="specimen-space">
        <div class="specimen__section-head">
          <h2 id="specimen-space">尺度与节奏</h2>
          <span class="specimen__meta">4px base · 640 / 960 / 1280</span>
        </div>
        <div class="scale">
          <div v-for="n in spaces" :key="n" class="scale__item">
            <span class="scale__bar" :style="{ width: `var(--space-${n})` }"></span>
            <span class="scale__label">--space-{{ n }}</span>
          </div>
        </div>
      </section>

      <footer class="specimen__foot">
        <span>API · {{ apiBase }}</span>
        <span>375 / 768 / 1280 冒烟</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.specimen__head {
  padding-block: clamp(24px, 5vw, 56px);
  border-bottom: var(--border);
}

.specimen__eyebrow {
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-wider);
  color: var(--verm);
}

.specimen__title {
  margin-top: var(--space-3);
  font-size: var(--text-display);
  line-height: 0.88;
  letter-spacing: -0.04em;
  overflow-wrap: break-word;
}

.specimen__title span {
  color: var(--verm);
}

.specimen__lede {
  max-width: 52ch;
  margin-top: var(--space-5);
  color: var(--text-muted);
}

.specimen__section {
  padding-block: clamp(28px, 4vw, 48px);
  border-bottom: var(--border);
}

.specimen__section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  margin-bottom: var(--space-5);
}

.specimen__section-head h2 {
  font-size: var(--text-xl);
}

.specimen__meta {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  color: var(--text-muted);
}

.swatches {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(160px, 100%), 1fr));
  gap: var(--space-3);
}

.swatch {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-6);
  min-height: 132px;
  padding: var(--space-4);
  border: var(--border);
}

.swatch__name {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
}

.swatch__meta,
.swatch__value {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.swatch__value {
  opacity: 0.72;
}

.type-scale {
  margin: 0;
}

.type-scale__row {
  display: grid;
  grid-template-columns: 9rem minmax(0, 1fr);
  gap: var(--space-4);
  padding-block: var(--space-4);
  border-top: var(--border);
}

.type-scale__row:first-child {
  border-top: 0;
}

.type-scale__label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-family: var(--mono);
  font-size: var(--text-xs);
  color: var(--text);
}

.type-scale__label span {
  color: var(--text-muted);
  font-size: var(--text-2xs);
}

.type-scale__sample {
  margin: 0;
  overflow-wrap: anywhere;
}

.scale {
  display: grid;
  gap: var(--space-2);
}

.scale__item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.scale__bar {
  flex: 0 0 auto;
  height: 10px;
  border: 1px solid var(--ink);
  background: var(--acid);
}

.scale__label {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  color: var(--text-muted);
}

.specimen__foot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-6);
  padding-block: var(--space-6);
  font-family: var(--mono);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

@media (max-width: 639px) {
  .type-scale__row {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }
}
</style>
