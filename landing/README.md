# Shiraz web frontend

The Svelte 5 / SvelteKit 3 site lives here. Its integrated Builder uses the
existing Go WebAssembly assembler; APK bytes stay in the browser. The Go server
in `go-server/` remains the separate app-streaming service described in `docs/WEB.md`.

From the repository root (Node 24+, npm, and Go required):

```sh
npm ci --prefix landing
make landing-dev
make landing-check
make landing-build
```

`landing/build/` is the prerendered static site. `BASE_PATH=/shiraz make landing-build`
builds for GitHub project Pages. Go assets are generated from `go-wasm/` and ignored,
as are node_modules and all build output. CI can use verified upstream-job assets
with `SHIRAZ_PREBUILT_WASM=1`. No APKs or runtime bases belong in source control.

For browser checks after building:

```sh
cd landing
npx playwright install chromium
npm test
```

The builder loads the published `release.json`, checks runtime size and SHA-256,
and supports a user-selected local base when the published base is unavailable.
Downloads are generated only after user submission. A build can package a supported
APK; it does not prove that the app will work. Keep the Go assembler checks in place.

Pages publishing builds this site and `scripts/package_browser_site.py` adds the
verified runtime bases and manifest. The downloadable offline builder ZIP retains
the standalone Go demo, independently of the SvelteKit site.

Original code is GPL-3.0-only. Adapted Heroicons paths and animation retain MIT
notices in `static/licenses/`. The Spectre SVG is the existing project brand asset.
