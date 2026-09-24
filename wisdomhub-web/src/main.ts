import { createApp } from 'vue'

// ---------------------------------------------------------------------------
// 字体：自托管（@fontsource）而不是走 Google Fonts CDN。
// 一是国内访问 Google Fonts 不稳定，二是产物不依赖外部域名、离线也能跑。
// 只引 latin 子集：中文由系统字体栈承担（见 tokens.css 的 --wh-font-sans），
// 引完整字符集会让产物膨胀好几 MB。
// ---------------------------------------------------------------------------
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/ibm-plex-sans/latin-700.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-500.css'

// ---------------------------------------------------------------------------
// Element Plus 的变量基础。这里只引「变量 + 基础样式」两份，
// 具体组件样式由 unplugin-vue-components 按需注入（见 vite.config.ts）。
// 项目自己的主题覆盖在 @/styles/index.css 里。
// ---------------------------------------------------------------------------
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import '@/styles/index.css'

import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'

createApp(App).use(pinia).use(router).mount('#app')
