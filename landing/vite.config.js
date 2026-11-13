// SPDX-License-Identifier: GPL-3.0-only
import { sveltekit } from '@sveltejs/kit/vite';
import adapter from '@sveltejs/adapter-static';
import { defineConfig } from 'vite';
import { existsSync, readFileSync } from 'node:fs';

const API = 'https://api.github.com/repos/spectre-oss/shiraz';

// Public repository facts baked in at build time. The build must also work
// offline and when the API refuses, so every failure becomes null and the page
// then hides the star count and links to the release list instead of a tag.
async function github(path) {
  if (process.env.SHIRAZ_OFFLINE) return null;
  const token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
  try {
    const response = await fetch(API + path, {
      headers: { accept: 'application/vnd.github+json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
      signal: AbortSignal.timeout(4000)
    });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

export default defineConfig(async ({ command }) => {
  // `vite dev` and `vite preview` do not need fresh numbers.
  const [repository, releases] = command === 'build' ? await Promise.all([github(''), github('/releases?per_page=10')]) : [null, null];
  const stars = Number.isInteger(repository?.stargazers_count) ? repository.stargazers_count : null;
  // Newest version tag; the release that only holds website media is not one.
  const tag = (Array.isArray(releases) ? releases : []).map(release => release?.tag_name).find(name => typeof name === 'string' && /^v\d+\.\d+\.\d+$/.test(name)) ?? null;
  // prepare-assets.mjs has already checked these bytes, or left the files out.
  const media = JSON.parse(readFileSync(new URL('./src/lib/media.json', import.meta.url), 'utf8'));
  const film = [media.video, media.poster].every(({ file }) => existsSync(new URL(`./static/media/${file}`, import.meta.url)));
  return {
    define: {
      __SHIRAZ_VERSION__: JSON.stringify(readFileSync(new URL('../VERSION', import.meta.url), 'utf8').trim()),
      __SHIRAZ_STARS__: JSON.stringify(stars),
      __SHIRAZ_RELEASE_TAG__: JSON.stringify(tag),
      __SHIRAZ_FILM__: JSON.stringify(film)
    },
    plugins: [sveltekit({
      adapter: adapter({ fallback: '404.html' }),
      paths: { base: process.env.BASE_PATH || '' }
    })]
  };
});
