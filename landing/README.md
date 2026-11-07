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

The independent website workflow builds this site and
`scripts/prepare_pages_runtime.py` adds checksum-verified runtime bases from the
latest published release. Website deployment does not wait for native builds.
Successful native releases trigger a website refresh. The downloadable offline builder ZIP retains
the standalone Go demo, independently of the SvelteKit site.

Original code is GPL-3.0-only. Adapted Heroicons paths and animation retain MIT
notices in `static/licenses/`. The Spectre SVG is the existing project brand asset.

## Contribute compatibility results

`src/lib/compatibility.json` is the compatibility page’s single source of
app records. Add or update one entry per app and submit a pull request. Each entry
has a unique stable `id`, `name`, `framework`, `status`, `observed`, `limitations`,
and `source` (a public Markdown report relative to the repository’s `docs/`).
Use `Partial` for bounded interactive results, `Blocked` for a documented blocker,
and `Historical` for earlier observations. Do not infer full support from launch.

Keep APK version, architecture, runtime version/commit, test environment, steps,
and evidence in the linked report, following `docs/AI_CONTRIBUTING.md`. Never add
APKs, private logs, credentials, or personal app data to the catalog. New public
reports must follow the repository documentation allowlist rules. `npm run check`
validates unique IDs, required fields, status values and evidence paths before
checking the Svelte app. This catalog is source code under the project GPL license.
