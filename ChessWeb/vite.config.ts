import { defineConfig } from 'vitest/config'
import { viteSingleFile } from 'vite-plugin-singlefile'

// `npm run build` -> normal multi-file build in dist/ (what Vercel serves).
// `npm run build:single` (SINGLE_FILE=1) -> one self-contained dist-single/index.html (handy for sharing/hosting anywhere).
export default defineConfig({
  base: './',
  plugins: process.env.SINGLE_FILE ? [viteSingleFile()] : [],
  build: { target: 'es2022' },
  test: {
    environment: 'node',
    include: ['tests/**/*.test.ts'],
    testTimeout: 120_000,
  },
})
