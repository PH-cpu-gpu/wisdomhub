import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  // 第三个参数是前缀白名单：只把 VITE_ 开头的变量交给下面的 proxy，
  // 避免仓库根目录 .env 里的后端中间件凭据（MYSQL_ROOT_PASSWORD 等）被读进来。
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  return {
    plugins: [
      vue(),
      // Element Plus 按需引入：只把真正用到的组件与其样式打进产物。
      // 两个插件都只用 ElementPlusResolver，不额外开启 Vue / vue-router 的自动导入 ——
      // ref / computed 这类 API 保持显式 import，读代码时不用猜它们从哪来。
      AutoImport({
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: 'src/types/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: 'src/types/components.d.ts',
      }),
    ],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    server: {
      port: 5173,
      // 浏览器只跟 dev server 说话，/api 由 Vite 转发到网关。
      // 这样开发与生产（Nginx 反代 /api 到网关，技术文档 16 章）同构，
      // 业务代码里永远只写 /api，不用按环境分支，也不会撞上跨域。
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:9090',
          changeOrigin: true,
        },
      },
    },
  }
})
