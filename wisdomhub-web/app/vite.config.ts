import { readFileSync, statSync } from 'node:fs'
import { extname, join } from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, type Connect, type Plugin } from 'vite'

/** dev / preview 下把 ../landing 作为 `/` 的落地页，其余路径交给 SPA */
function landingPlugin(): Plugin {
  const landingDir = fileURLToPath(new URL('../landing', import.meta.url))
  const mime: Record<string, string> = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.mjs': 'text/javascript; charset=utf-8',
    '.webp': 'image/webp',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.mp4': 'video/mp4',
  }
  const middleware: Connect.NextHandleFunction = (req, res, next) => {
    const pathname = decodeURIComponent((req.url ?? '/').split('?')[0])
    const file = join(landingDir, pathname === '/' ? 'index.html' : pathname)
    if (!file.startsWith(landingDir)) {
      next()
      return
    }

    try {
      if (!statSync(file).isFile()) throw new Error('not a file')
      res.setHeader('Content-Type', mime[extname(file).toLowerCase()] ?? 'application/octet-stream')
      res.end(readFileSync(file))
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
