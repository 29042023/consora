import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, el front corre en http://127.0.0.1:5173/app/ y le pide a Django (puerto 8000)
// la API, las imágenes subidas y el CSS propio. Las cookies de sesión son las mismas porque es el mismo host.
const django = 'http://127.0.0.1:8000'

export default defineConfig({
  plugins: [react()],
  base: '/app/',
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': django,
      '/media': django,
      '/static': django,
    },
  },
  build: {
    // El build queda listo para que Django lo sirva en /app/
    outDir: '../consora/web/static/app',
    emptyOutDir: true,
  },
})
