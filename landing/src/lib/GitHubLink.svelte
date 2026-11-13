<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onMount } from 'svelte';
  const repository = 'spectre-oss/shiraz';
  const cacheKey = 'shiraz-stars';
  // Baked at build time; null when the build could not reach GitHub.
  let stars = $state(__SHIRAZ_STARS__);
  const compact = count => count < 1000 ? String(count) : (count / 1000).toFixed(count < 10000 ? 1 : 0).replace(/\.0$/, '') + 'k';
  const label = $derived(stars === null ? 'GitHub repository' : `GitHub repository, ${stars} ${stars === 1 ? 'star' : 'stars'}`);
  onMount(() => {
    // Refresh once per browser session. Any failure keeps the baked value.
    let cancelled = false;
    try {
      const cached = Number.parseInt(sessionStorage.getItem(cacheKey) ?? '', 10);
      if (Number.isInteger(cached)) { stars = cached; return; }
    } catch { /* storage unavailable */ }
    fetch(`https://api.github.com/repos/${repository}`, { headers: { accept: 'application/vnd.github+json' } })
      .then(response => response.ok ? response.json() : null)
      .then(data => {
        if (cancelled || !Number.isInteger(data?.stargazers_count)) return;
        stars = data.stargazers_count;
        try { sessionStorage.setItem(cacheKey, String(stars)); } catch { /* storage unavailable */ }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  });
</script>
<a class="github-link" href={`https://github.com/${repository}`} aria-label={label}>
  <svg class="github-mark" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/></svg>
  <span class="github-word">GitHub</span>
  {#if stars !== null}<span class="github-stars"><svg class="github-star" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M10.788 3.21c.448-1.077 1.976-1.077 2.424 0l2.082 5.006 5.404.434c1.164.093 1.636 1.545.749 2.305l-4.117 3.527 1.257 5.273c.271 1.136-.964 2.033-1.96 1.425L12 18.354 7.373 21.18c-.996.608-2.231-.29-1.96-1.425l1.257-5.273-4.117-3.527c-.887-.76-.415-2.212.749-2.305l5.404-.434 2.082-5.005Z"/></svg>{compact(stars)}</span>{/if}
</a>
