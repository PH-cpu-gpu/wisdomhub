<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

import { ping } from '@/api/system'
import { TECH_CATEGORIES, readToken } from '@/config/design-tokens'
import { env } from '@/config/env'
import { useAppStore, type ThemeMode } from '@/stores/app'

const app = useAppStore()

// ---------------------------------------------------------------------------
// F0.5 的联调冒烟：点一下走完 dev-server → 网关 → knowledge-service 整条链路
// ---------------------------------------------------------------------------
const pingState = ref<'idle' | 'loading' | 'ok' | 'fail'>('idle')
const pingResult = ref('')

async function runPing() {
  pingState.value = 'loading'
  pingResult.value = ''
  try {
    // 这个请求经过：vite dev server 的 /api 代理 → 网关 → 网关按路由转发到
    // knowledge-service。能拿到 "pong" 就说明路由、跨域、响应包装三条都通了。
    pingResult.value = await ping()
    pingState.value = 'ok'
  } catch (e) {
    // 失败提示由请求拦截器统一弹过了，这里只需要把结果留在页面上
    pingResult.value = e instanceof Error ? e.message : String(e)
    pingState.value = 'fail'
  }
}

// 演示用的本地状态
const keyword = ref('')
const category = ref('')
const page = ref(3)

function notify() {
  // ElMessage 由 unplugin-auto-import 的 ElementPlusResolver 自动引入，
  // 连同它的样式一起 —— 这里能跑通就说明按需引入配置正确。
  ElMessage({ message: '已发布', type: 'success' })
}

// 用 :model-value + @change 而不是 v-model：v-model 会直接写 store 里的 ref，
// 绕过 setThemeMode 的持久化，刷新后又变回旧主题。
function onThemeChange(value: string | number | boolean | undefined) {
  app.setThemeMode(value as ThemeMode)
}

// ---------------------------------------------------------------------------
// 色板
// ---------------------------------------------------------------------------

const surfaceTokens = [
  { token: '--wh-bg-base', use: '页面底' },
  { token: '--wh-bg-surface', use: '卡片 / 面板' },
  { token: '--wh-bg-raised', use: '悬浮 / 输入框 / 代码块' },
  { token: '--wh-bg-hover', use: '行悬停' },
  { token: '--wh-border', use: '分隔线' },
  { token: '--wh-border-strong', use: '强分隔线 / 输入框描边' },
]

const textTokens = [
  { token: '--wh-text-1', use: '正文、标题' },
  { token: '--wh-text-2', use: '次要说明' },
  { token: '--wh-text-3', use: '时间、计数、占位符' },
]

const accentTokens = [
  { token: '--wh-accent', use: '动作与焦点：按钮、链接、激活态' },
  { token: '--wh-accent-hover', use: '悬停' },
]

const semanticTokens = [
  { token: '--wh-danger', use: '危险 / 错误' },
  { token: '--wh-warning', use: '警告' },
  { token: '--wh-info', use: '信息' },
]

const allTokens = [
  ...surfaceTokens,
  ...textTokens,
  ...accentTokens,
  ...semanticTokens,
  ...TECH_CATEGORIES.map((c) => ({ token: c.token, use: c.label })),
].map((t) => t.token)

/**
 * CSS 变量的实际取值。
 *
 * 用 readToken() 现读而不是在这里再抄一份 hex —— 抄一份就有两份事实来源，
 * 改色时必然漏掉一边。副作用是换主题后要重新读：getComputedStyle 返回的是
 * 快照，缓存下来的旧值不会自己更新。
 */
const resolved = ref<Record<string, string>>({})

function refreshResolved() {
  const map: Record<string, string> = {}
  for (const token of allTokens) map[token] = readToken(token)
  resolved.value = map
}

onMounted(refreshResolved)

// 等 class 切完再读，否则拿到的是切换前的值
watch(
  () => app.isDark,
  () => nextTick(refreshResolved),
)

// ---------------------------------------------------------------------------
// 字阶 / 间距 / 圆角
// ---------------------------------------------------------------------------

const typeScale = [
  { token: '--wh-text-3xl', name: '3xl', px: '32px', use: '首页主标题' },
  { token: '--wh-text-2xl', name: '2xl', px: '24px', use: '页面标题' },
  { token: '--wh-text-xl', name: 'xl', px: '20px', use: '区块标题' },
  { token: '--wh-text-lg', name: 'lg', px: '17px', use: '卡片标题' },
  { token: '--wh-text-base', name: 'base', px: '15px', use: '正文（行高 1.7）' },
  { token: '--wh-text-sm', name: 'sm', px: '13px', use: '元信息' },
  { token: '--wh-text-xs', name: 'xs', px: '12px', use: '徽章、类别标签' },
]

const spacingScale = [
  '--wh-space-1',
  '--wh-space-2',
  '--wh-space-3',
  '--wh-space-4',
  '--wh-space-5',
  '--wh-space-6',
  '--wh-space-7',
  '--wh-space-8',
  '--wh-space-9',
]

const radiusScale = [
  { token: '--wh-radius-xs', use: '徽章、类别标签' },
  { token: '--wh-radius-md', use: '卡片、按钮、输入框' },
  { token: '--wh-radius-full', use: '只给头像' },
]

const roles = [
  { key: '框架', value: 'Vue 3 + Vite + TypeScript' },
  { key: '路由', value: 'Vue Router 4（history 模式，页面懒加载）' },
  { key: '状态', value: 'Pinia' },
  { key: '组件库', value: 'Element Plus（按需引入）' },
  { key: '字体', value: 'IBM Plex Sans + IBM Plex Mono（自托管 latin 子集）' },
  { key: '主题', value: 'CSS 变量 + html.dark，默认跟随系统' },
]
</script>

<template>
  <main class="ds wh-container">
    <header class="ds__head">
      <p class="ds__eyebrow wh-mono">设计系统</p>
      <h1 class="ds__title">WisdomHub 设计系统</h1>
      <p class="ds__lede">
        本页既是规范文档，也是规范的实例 —— 页面上每一个颜色、字号、间距都是从下面这些令牌取的。
      </p>
    </header>

    <!-- ================= 主题切换 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">主题</h2>
      <p class="ds__note">
        默认跟随系统：用户没表过态时读 <code>prefers-color-scheme</code>。<code>html</code> 上的
        <code>dark</code> 类由 index.html 的内联脚本在首屏绘制前定好（否则会先渲染一帧再切），
        之后由 Pinia 的 app store 接管。Element Plus 的暗色变量集也挂在同一个类上，两套主题因此同步切换。
      </p>

      <div class="ds__theme-bar">
        <el-radio-group :model-value="app.themeMode" @change="onThemeChange">
          <el-radio-button value="dark">暗色</el-radio-button>
          <el-radio-button value="light">亮色</el-radio-button>
          <el-radio-button value="system">跟随系统</el-radio-button>
        </el-radio-group>
        <span class="ds__theme-state wh-mono">
          当前：{{ app.themeLabel }} → {{ app.isDark ? 'dark' : 'light' }}
        </span>
      </div>

      <p class="ds__note ds__note--sub">
        选择存在 <code>localStorage['wisdomhub:theme']</code>，
        刷新后保持；<code>prefers-reduced-motion</code> 与键盘焦点环均已适配。
      </p>
    </section>

    <!-- ================= 选型结论 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">技术选型</h2>
      <dl class="ds__roles">
        <div v-for="r in roles" :key="r.key" class="ds__role">
          <dt class="wh-mono">{{ r.key }}</dt>
          <dd>{{ r.value }}</dd>
        </div>
      </dl>
      <p class="ds__note ds__note--sub">
        Element Plus 按需引入（<code>unplugin-vue-components</code>），只把用到的组件与样式打进产物。
        它的 <code>--el-*</code> 变量在 <code>src/styles/element-theme.css</code> 里整体映射到本项目的令牌，
        所以组件库的外观跟着设计系统走，而不是反过来。
      </p>
    </section>

    <!-- ================= 色彩 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">色彩</h2>
      <p class="ds__note">
        底色分四档，全部带一点蓝调 —— 纯黑上放卡片只能靠加亮，层级会挤成一团。
        投影在暗色底上几乎不可见，所以分区一律用 1px 描边，两个主题因此能共用同一套布局规则。
      </p>

      <h3 class="ds__sub-title">底色与描边</h3>
      <ul class="ds__swatches">
        <li v-for="s in surfaceTokens" :key="s.token" class="ds__swatch">
          <span class="ds__chip" :style="{ background: `var(${s.token})` }" />
          <span class="ds__swatch-use">{{ s.use }}</span>
          <span class="ds__swatch-value wh-mono">{{ resolved[s.token] }}</span>
          <span class="ds__swatch-token wh-mono">{{ s.token }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">文字</h3>
      <p class="ds__note ds__note--sub">
        只有三级。再细分会出现「区别看不出来、维护成本却翻倍」的中间态。
        暗色下正文用 <code>#c0caf5</code> 而不是纯白 —— 大面积纯白文字在暗底上会发光刺眼。
      </p>
      <ul class="ds__swatches">
        <li v-for="s in textTokens" :key="s.token" class="ds__swatch">
          <span
            class="ds__chip ds__chip--letter"
            :style="{ color: `var(${s.token})`, background: 'var(--wh-bg-surface)' }"
            >Aa</span
          >
          <span class="ds__swatch-use">{{ s.use }}</span>
          <span class="ds__swatch-value wh-mono">{{ resolved[s.token] }}</span>
          <span class="ds__swatch-token wh-mono">{{ s.token }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">主色：只用于动作与焦点</h3>
      <p class="ds__note ds__note--sub">
        主色<strong>不</strong>用来给信息着色。按钮、链接、激活态、焦点环是它的全部职责 ——
        让主色铺满每一处强调，等于每条信息都在喊，下面那套类别配色也就没有位置了。
      </p>
      <ul class="ds__swatches">
        <li v-for="s in accentTokens" :key="s.token" class="ds__swatch">
          <span class="ds__chip" :style="{ background: `var(${s.token})` }" />
          <span class="ds__swatch-use">{{ s.use }}</span>
          <span class="ds__swatch-value wh-mono">{{ resolved[s.token] }}</span>
          <span class="ds__swatch-token wh-mono">{{ s.token }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">语义色</h3>
      <ul class="ds__swatches">
        <li v-for="s in semanticTokens" :key="s.token" class="ds__swatch">
          <span class="ds__chip" :style="{ background: `var(${s.token})` }" />
          <span class="ds__swatch-use">{{ s.use }}</span>
          <span class="ds__swatch-value wh-mono">{{ resolved[s.token] }}</span>
          <span class="ds__swatch-token wh-mono">{{ s.token }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">技术类别配色</h3>
      <p class="ds__note ds__note--sub">
        这是整套设计系统的识别性元素。五类技术取自技术文档 7.13 的 category 枚举，
        全站共用一套颜色：图谱节点、技术标签、文章徽章。
        颜色在这里<strong>编码信息</strong>，不是装饰 —— 同一个类别在任何页面都是同一个颜色，
        用户扫一眼就能分辨内容属于语言、框架还是中间件。
        颜色只是补充线索：徽章里始终带类别文字，色盲用户不靠颜色也能读。
      </p>
      <ul class="ds__swatches">
        <li v-for="c in TECH_CATEGORIES" :key="c.value" class="ds__swatch">
          <span class="ds__chip" :style="{ background: `var(${c.token})` }" />
          <span class="ds__swatch-use">{{ c.label }}</span>
          <span class="ds__swatch-value wh-mono">{{ resolved[c.token] }}</span>
          <span class="ds__swatch-token wh-mono">{{ c.value }}</span>
        </li>
      </ul>

      <div class="ds__demo">
        <span class="ds__demo-label wh-mono">徽章</span>
        <span v-for="c in TECH_CATEGORIES" :key="c.value" class="wh-cat" :data-cat="c.value">
          {{ c.value }}
        </span>
      </div>
    </section>

    <!-- ================= 字体 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">字体</h2>
      <p class="ds__note">
        一套无衬线家族承担全部界面与标题，靠字阶与字重区分层级，不再引第二套显示字体。
        中文走系统字体栈：IBM Plex 没有中文字形，而 CJK 网络字体动辄数 MB，不做自托管。
        正文 15px / 1.7 —— 这个行高是为中文长文调的，纯拉丁界面常见的 14px / 1.5 在这里偏挤。
      </p>

      <ul class="ds__type">
        <li v-for="t in typeScale" :key="t.token" class="ds__type-row">
          <span class="ds__type-meta wh-mono">{{ t.name }} · {{ t.px }}</span>
          <span class="ds__type-sample" :style="{ fontSize: `var(${t.token})` }">
            Redis 为什么使用跳表
          </span>
          <span class="ds__type-use">{{ t.use }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">等宽字体</h3>
      <p class="ds__note ds__note--sub">
        只给三类东西用：雪花 ID（19 位数字，等宽才对得齐）、代码、计数。
        不当作通用标签装饰 —— 到处都用等宽，哪里都没有重点。
      </p>
      <div class="ds__demo ds__demo--block">
        <span class="ds__demo-label wh-mono">ID</span>
        <span class="wh-mono ds__id">1834567890123456789</span>
        <span class="ds__demo-label wh-mono">时间</span>
        <span class="wh-mono">2026-09-24T13:08:14Z</span>
        <span class="ds__demo-label wh-mono">代码</span>
        <code>IdWorker.getId()</code>
      </div>
    </section>

    <!-- ================= 间距与圆角 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">间距与圆角</h2>
      <p class="ds__note">4px 基准的等比刻度，从 4 到 96 共九档。</p>

      <ul class="ds__spacing">
        <li v-for="s in spacingScale" :key="s" class="ds__spacing-row">
          <span class="wh-mono ds__spacing-token">{{ s }}</span>
          <span class="ds__spacing-bar" :style="{ width: `var(${s})` }" />
          <span class="wh-mono ds__spacing-value">{{ resolved[s] }}</span>
        </li>
      </ul>

      <h3 class="ds__sub-title">圆角刻意偏小</h3>
      <p class="ds__note ds__note--sub">
        终端 / IDE 的视觉语言是框线分区，不是一片圆润的卡片。全站只用三档，
        避免任何元素都套同一个圆角值 —— 那会让层级信息消失。
      </p>
      <ul class="ds__radius">
        <li v-for="r in radiusScale" :key="r.token" class="ds__radius-item">
          <span class="ds__radius-box" :style="{ borderRadius: `var(${r.token})` }" />
          <span class="ds__swatch-token wh-mono">{{ r.token }}</span>
          <span class="ds__swatch-use">{{ r.use }}</span>
        </li>
      </ul>
    </section>

    <!-- ================= Element Plus ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">Element Plus 组件</h2>
      <p class="ds__note">
        下面这些组件的配色、圆角、字体全部来自本页上面的令牌，没有逐组件写覆盖样式。
      </p>

      <div class="ds__components">
        <div class="ds__component">
          <span class="ds__demo-label wh-mono">按钮</span>
          <div class="ds__demo">
            <el-button type="primary">发布文章</el-button>
            <el-button>存草稿</el-button>
            <el-button type="danger" plain>删除</el-button>
            <el-button type="primary" disabled>已发布</el-button>
          </div>
        </div>

        <div class="ds__component">
          <span class="ds__demo-label wh-mono">表单</span>
          <div class="ds__demo ds__demo--block">
            <el-input v-model="keyword" placeholder="搜索技术、文章、问题" clearable />
            <el-select v-model="category" placeholder="按类别筛选">
              <el-option
                v-for="c in TECH_CATEGORIES"
                :key="c.value"
                :label="c.label"
                :value="c.value"
              />
            </el-select>
          </div>
        </div>

        <div class="ds__component">
          <span class="ds__demo-label wh-mono">标签</span>
          <div class="ds__demo">
            <el-tag>Java</el-tag>
            <el-tag type="success">已发布</el-tag>
            <el-tag type="warning">草稿</el-tag>
            <el-tag type="danger">已删除</el-tag>
            <el-tag type="info">审核中</el-tag>
            <el-tag effect="dark">实心</el-tag>
          </div>
        </div>

        <div class="ds__component">
          <span class="ds__demo-label wh-mono">填充色上的文字</span>
          <p class="ds__note ds__note--sub">
            暗色主题的填充都很亮（主色 <code>#6ee7b7</code>、危险色 <code>#f87171</code>），
            文字必须压深。Element Plus 默认在这些位置压白字，对比度只有约 1.8:1 ——
            这几处是整套主题里最容易漏掉、也最先被看出来的一类问题。
          </p>
          <div class="ds__demo">
            <el-badge :value="12" :max="99">
              <el-button>未读通知</el-button>
            </el-badge>
            <el-pagination
              v-model:current-page="page"
              background
              :total="60"
              :page-size="10"
            />
          </div>
        </div>

        <div class="ds__component">
          <span class="ds__demo-label wh-mono">反馈</span>
          <div class="ds__demo">
            <el-button @click="notify">触发消息提示</el-button>
            <el-alert
              title="依赖服务暂不可用"
              type="error"
              description="AI 服务当前无响应，文章、搜索、问答等核心功能不受影响。"
              show-icon
              :closable="false"
            />
          </div>
        </div>

        <div class="ds__component">
          <span class="ds__demo-label wh-mono">卡片</span>
          <el-card shadow="never">
            <h3 class="ds__card-title">Redis 为什么使用跳表</h3>
            <p class="ds__card-body">
              跳表在范围查询上接近平衡树的性能，实现却简单得多，并发场景下也更容易做无锁化。
            </p>
            <div class="ds__card-meta wh-mono">
              <span class="wh-cat" data-cat="MIDDLEWARE">MIDDLEWARE</span>
              <span>♥ 128</span>
              <span>☆ 42</span>
              <span>1.2k 阅读</span>
              <span>09-24</span>
            </div>
          </el-card>
        </div>
      </div>
    </section>

    <!-- ================= 工程信息 ================= -->
    <section class="ds__section">
      <h2 class="ds__section-title">工程信息</h2>
      <p class="ds__note">
        实际解析到的环境变量。缺任何一项都会在启动时报错而不是静默拿到 undefined
        —— 校验在 <code>src/config/env.ts</code>。
      </p>
      <dl class="ds__roles">
        <div class="ds__role">
          <dt class="wh-mono">构建模式</dt>
          <dd class="wh-mono">{{ env.mode }}</dd>
        </div>
        <div class="ds__role">
          <dt class="wh-mono">接口前缀</dt>
          <dd class="wh-mono">{{ env.apiBaseUrl }}</dd>
        </div>
        <div class="ds__role">
          <dt class="wh-mono">应用标题</dt>
          <dd class="wh-mono">{{ env.appTitle }}</dd>
        </div>
      </dl>
      <p class="ds__note ds__note--sub">
        <code>/api</code> 是相对路径：开发时由 dev server 转发到网关（9090），
        生产由 Nginx 反代。业务代码里永远只写 <code>/api</code>，不需要按环境分支。
        接口契约见技术文档 17.1。
      </p>

      <h3 class="ds__sub-title">连通性检查</h3>
      <p class="ds__note ds__note--sub">
        打一次 <code>GET /api/knowledge/ping</code>。这个请求会走完
        dev server 代理 → 网关路由 → knowledge-service 整条链路，
        拿到 <code>pong</code> 就说明前端到后端是通的。
        <br />
        ⚠️ 需要本机已 <code>docker compose up</code> 且网关与 knowledge-service 已启动，
        否则会看到网络错误——那是后端没起，不是前端的问题。
      </p>
      <div class="ds__demo">
        <el-button type="primary" :loading="pingState === 'loading'" @click="runPing">
          测试网关连通
        </el-button>
        <span v-if="pingState === 'ok'" class="ds__ping ds__ping--ok wh-mono">
          ✓ 200 · {{ pingResult }}
        </span>
        <span v-else-if="pingState === 'fail'" class="ds__ping ds__ping--fail wh-mono">
          ✕ {{ pingResult }}
        </span>
      </div>
    </section>
  </main>
</template>

<style scoped>
.ds {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-8);
  max-width: 56rem;
  padding-block: var(--wh-space-8) var(--wh-space-9);
}

.ds__head {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-3);
}

.ds__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}

.ds__title {
  font-size: var(--wh-text-3xl);
}

.ds__lede {
  font-size: var(--wh-text-lg);
  color: var(--wh-text-2);
}

.ds__section {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-4);

  padding-top: var(--wh-space-6);
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.ds__section-title {
  font-size: var(--wh-text-2xl);
}

.ds__sub-title {
  margin-top: var(--wh-space-2);
  font-size: var(--wh-text-base);
  color: var(--wh-text-2);
}

.ds__note {
  max-width: var(--wh-reading);
  color: var(--wh-text-2);
}

.ds__note--sub {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.ds__note code {
  padding: 1px var(--wh-space-1);
  border-radius: var(--wh-radius-xs);
  background-color: var(--wh-bg-raised);
  color: var(--wh-text-2);
}

/* ---------- 主题切换 ---------- */
.ds__theme-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--wh-space-4);
}

.ds__theme-state {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

/* ---------- 选型 / 工程信息 ---------- */
.ds__roles {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  overflow: hidden;
}

.ds__role {
  display: grid;
  grid-template-columns: 7rem 1fr;
  gap: var(--wh-space-4);

  padding: var(--wh-space-3) var(--wh-space-4);
  background-color: var(--wh-bg-surface);
}

.ds__role + .ds__role {
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.ds__role dt {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.ds__role dd {
  margin: 0;
  font-size: var(--wh-text-sm);
  color: var(--wh-text-1);
}

/* ---------- 色板 ---------- */
.ds__swatches {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr));
  gap: var(--wh-space-2) var(--wh-space-4);

  margin: 0;
  padding: 0;
  list-style: none;
}

.ds__swatch {
  display: grid;
  grid-template-columns: 2rem 1fr auto;
  grid-template-areas:
    'chip use value'
    'chip token token';
  align-items: center;
  gap: 0 var(--wh-space-3);

  padding: var(--wh-space-2) 0;
}

.ds__chip {
  grid-area: chip;
  width: 2rem;
  height: 2rem;
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-xs);
}

/* 文字色板：用字母本身当样本，比色块更直观 */
.ds__chip--letter {
  display: grid;
  place-items: center;
  font-weight: var(--wh-weight-semibold);
}

.ds__swatch-use {
  grid-area: use;
  font-size: var(--wh-text-sm);
}

.ds__swatch-value {
  grid-area: value;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-2);
}

.ds__swatch-token {
  grid-area: token;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

/* ---------- 演示块 ---------- */
.ds__demo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--wh-space-3);

  padding: var(--wh-space-4);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  background-color: var(--wh-bg-surface);
}

.ds__demo--block {
  display: grid;
  grid-template-columns: 4rem 1fr;
  align-items: center;
  gap: var(--wh-space-3);
}

.ds__demo--block > :not(.ds__demo-label) {
  grid-column: 2;
}

.ds__demo-label {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.ds__id {
  color: var(--wh-accent);
}

/* ---------- 字阶 ---------- */
.ds__type {
  display: flex;
  flex-direction: column;
  gap: 0;

  margin: 0;
  padding: 0;
  list-style: none;

  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  overflow: hidden;
}

.ds__type-row {
  display: grid;
  grid-template-columns: 8rem 1fr 10rem;
  align-items: baseline;
  gap: var(--wh-space-4);

  padding: var(--wh-space-3) var(--wh-space-4);
  background-color: var(--wh-bg-surface);
}

.ds__type-row + .ds__type-row {
  border-top: var(--wh-border-width) solid var(--wh-border);
}

.ds__type-meta,
.ds__type-use {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.ds__type-sample {
  line-height: var(--wh-leading-snug);
}

/* ---------- 间距 ---------- */
.ds__spacing {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);

  margin: 0;
  padding: 0;
  list-style: none;
}

.ds__spacing-row {
  display: grid;
  grid-template-columns: 7rem 1fr 4rem;
  align-items: center;
  gap: var(--wh-space-4);
}

.ds__spacing-token,
.ds__spacing-value {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.ds__spacing-bar {
  height: 0.5rem;
  border-radius: var(--wh-radius-full);
  background-color: var(--wh-accent);
}

/* ---------- 圆角 ---------- */
.ds__radius {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-5);

  margin: 0;
  padding: 0;
  list-style: none;
}

.ds__radius-item {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
}

.ds__radius-box {
  width: 4rem;
  height: 4rem;
  border: var(--wh-border-width) solid var(--wh-border-strong);
  background-color: var(--wh-bg-raised);
}

/* ---------- 组件样例 ---------- */
.ds__components {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-5);
}

.ds__component {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-2);
}

.ds__card-title {
  font-size: var(--wh-text-lg);
}

.ds__card-body {
  margin-top: var(--wh-space-2);
  color: var(--wh-text-2);
}

.ds__card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--wh-space-4);

  margin-top: var(--wh-space-4);
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

/* ---------- 联调冒烟结果 ---------- */
.ds__ping {
  font-size: var(--wh-text-sm);
}

.ds__ping--ok {
  color: var(--wh-accent);
}

.ds__ping--fail {
  color: var(--wh-danger);
}

/* 窄屏：字阶与间距的对照表折成一列，避免数值列被压没 */
@media (max-width: 640px) {
  .ds__type-row,
  .ds__role,
  .ds__demo--block {
    grid-template-columns: 1fr;
  }

  .ds__demo--block > :not(.ds__demo-label) {
    grid-column: 1;
  }

  .ds__type-use {
    display: none;
  }
}
</style>
