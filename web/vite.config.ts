import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'HR_')
  const apiProxy = {
    '/api': {
      target: env.HR_API_PROXY_TARGET || 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  }

  return {
    plugins: [
      vue(),
      Components({
        dts: 'src/components.d.ts',
        resolvers: [ElementPlusResolver({ importStyle: 'css', directives: true })],
      }),
    ],
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      proxy: apiProxy,
    },
    preview: {
      port: 4173,
      strictPort: true,
      proxy: apiProxy,
    },
    build: {
      rollupOptions: {
        input: {
          index: fileURLToPath(new URL('./index.html', import.meta.url)),
          employee: fileURLToPath(new URL('./employee.html', import.meta.url)),
          manager: fileURLToPath(new URL('./manager.html', import.meta.url)),
          hr: fileURLToPath(new URL('./hr.html', import.meta.url)),
          admin: fileURLToPath(new URL('./admin.html', import.meta.url)),
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      server: {
        deps: {
          inline: ['element-plus'],
        },
      },
    },
  }
})
