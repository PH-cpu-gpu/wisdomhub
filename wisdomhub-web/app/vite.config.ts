import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, type Connect, type Plugin } from 'vite'

/** dev / preview 下把 ../landing 作为 `/` 的落地页，其余路径交给 SPA */
function landingPlugin(): Plugin {
  const landingDir = fileURLToPath(new URL('../landing', import.meta.url))
  const files: Record<string, { file: string; type: string }> = {
    '/': { file: 'index.html', type: 'text/html; charset=utf-8' },
    '/styles.css': { file: 'styles.css', type: 'text/css; charset=utf-8' },
    '/main.js': { file: 'main.js', type: 'text/javascript; charset=utf-8' },
  }
  const middleware: Connect.NextHandleFunction = (req, res, next) => {
    const pathname = (req.url ?? '/').split('?')[0]
    const entry = files[pathname]
    if (!entry) {
      next()
      return
    }

    try {
      const body = readFileSync(join(landingDir, entry.file))
      res.setHeader('Content-Type', entry.type)
      res.end(body)
    } catch {
      next()
    }
  }

  return {
    name: 'wisdomhub-landing',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware)
    },
  }
}

export default defineConfig({
  plugins: [landingPlugin(), vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
})
