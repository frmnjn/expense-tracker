import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const dir = path.dirname(fileURLToPath(import.meta.url))

// Ganti placeholder __BUILD_VERSION__ di dist/sw.js dengan stempel build,
// supaya cache service worker otomatis berganti tiap deploy (tanpa bump manual).
function serviceWorkerVersionPlugin() {
  return {
    name: 'sw-version',
    apply: 'build' as const,
    closeBundle() {
      const file = path.join(dir, 'dist', 'sw.js')
      if (!fs.existsSync(file)) return
      const version = Date.now().toString()
      fs.writeFileSync(file, fs.readFileSync(file, 'utf8').replace('__BUILD_VERSION__', version))
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), serviceWorkerVersionPlugin()],
})
