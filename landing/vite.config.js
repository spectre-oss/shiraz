// SPDX-License-Identifier: GPL-3.0-only
import { sveltekit } from '@sveltejs/kit/vite';
import adapter from '@sveltejs/adapter-static';
import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';
export default defineConfig({
  define: { __SHIRAZ_VERSION__: JSON.stringify(readFileSync(new URL('../VERSION', import.meta.url), 'utf8').trim()) },
  plugins: [sveltekit({
    adapter: adapter(),
    paths: { base: process.env.BASE_PATH || '' }
  })]
});
