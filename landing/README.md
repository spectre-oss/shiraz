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

## Launcher film

The Mac section plays a short film. The film and its poster are binaries, so they
are not committed: they are assets of the `media-1` release, and
`src/lib/media.json` records each file's size and SHA-256. `prepare-assets.mjs`
downloads them into the ignored `static/media/` and stops the build if a file
there has other bytes. When they are absent (offline, or not yet uploaded) the
site builds without the player. The captions, `static/media/*.vtt`, are source.

The `media-1` release is created once. It is a pre-release like the version
releases, so it never becomes the repository's "Latest" release:

```sh
gh release create media-1 --prerelease --title "Website media" --notes "Film and poster for the website. Not a Shiraz version."
```

To publish a new cut (needs ffmpeg), check the captions against it, then upload:

```sh
node landing/scripts/make-media.mjs path/to/film.mp4
gh release upload media-1 landing/static/media/shiraz-launcher.mp4 landing/static/media/shiraz-launcher.jpg --clobber
```

After an upload, move the two local files aside and build once: the build must
print `MEDIA_READY` for each. A failed download only prints `MEDIA_MISSING`.

Commit the changed `media.json` and captions only after the upload, or the site
deploys without the film until it lands. `static/licenses/film-source.txt` lists
what the film shows and where its sound comes from.

Original code is GPL-3.0-only. Adapted Heroicons paths and animation retain MIT
notices in `static/licenses/`. Bricolage Grotesque and Instrument Sans are
OFL-1.1 fonts bundled at build time from their npm packages; see
`static/licenses/fonts-source.txt`. No font binaries are committed. The Spectre SVG is the existing project brand asset.

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

## Android coverage tracker

The compatibility page also reads `src/lib/android-support.json` (maintained
subsystem notes) and `src/lib/android-inventory.json` (generated inventories).
Keep host scope and verification level explicit; `Partial` does not certify
an entire subsystem. Add evidence paths and update the review date when notes
change. Do not put private logs, account data, payloads or APKs in either file.

In the runtime development checkout containing `tests/api-probe`,
`make landing-compatibility-data` rebuilds the framework
and regenerates class-name presence against the checksum-pinned Android API 35
SDK plus current planned probe counts. The runtime's advertised API 27 level is
a separate fact. Class-name presence is not method, signature, behavior or CTS
coverage; do not label it as an overall Android compatibility percentage.

To import a behavioral report, pass both its report and exact probe source
snapshot to `uv run --no-project python scripts/update_compatibility_tracker.py
--probe-report <report.json> --probe-source <probe-source-directory>`. The importer
verifies the source fingerprint and case definitions, then exports only category
counts. Unrun cases become `NOT_RUN`, distinct from `SKIPPED`. It does not copy
result details, device identity, run IDs or account information. A normal update
preserves the last imported snapshot and labels it separately from the current
catalog. Do not relabel historical or targeted results as a fresh full run.

`bun run check` inside `landing/` validates the inventories, planned case counts,
subsystem evidence paths and public snapshot schema. Run the compatibility-page
browser tests after changing tracker controls. Updating source does not publish
the site: the Pages workflow deploys `main` after its checks and build succeed.

The website consumes committed snapshots and can build without the runtime probe
source or SDK. Evidence for runtime code links to the recorded public source
baseline; reports and website instructions link to `main`. Use `--source-ref` to
record an already published baseline when the measured build includes local
changes. Publish only reviewed aggregate data, never raw result details.

## Star count and release link

`vite build` asks the public GitHub API for the repository's star count and its
newest release tag and bakes both into the pages (`vite.config.js`). The header
link refreshes the count once per browser session. If the build cannot reach the
API, the count is hidden and the release pill shows `VERSION` and links to the
release list, so offline builds still work. Set `SHIRAZ_OFFLINE=1` to skip the
requests. `GH_TOKEN` or `GITHUB_TOKEN` is used when present, only to avoid the
anonymous rate limit. Browser tests block `api.github.com`.
