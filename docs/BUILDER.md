# Shiraz browser AppImage & Flatpak builder

Each generic x86_64 or ARM64 (aarch64) base contains the Shiraz runtime but no APK:
- **AppImage base (`Shiraz-Browser-Base-<ver>-<arch>.AppImage`)**: the Go/WASM module parses the APK's Android binary manifest, generates Shiraz launch metadata, and appends an application overlay footer to the base AppImage.
- **Flatpak base (`Shiraz-Flatpak-Base-<ver>-<arch>.flatpak-base`)**: contains a pre-compressed OSTree static-delta runtime part (`Part 0`) and subtree checksum index for `org.freedesktop.Platform/<arch>/24.08`. The Go/WASM module hashes only the APK, native libraries, desktop entry, icon, and Flatpak metadata into an uncompressed overlay part (`Part 1`) and emits a standard single-file `.flatpak` bundle installable with `flatpak install --user`.

## Build the bases once on Linux

From the Shiraz repository root, first build any supported APK with `AOT=0` so
the prebuilt runtime components exist.

For an **AppImage** base:

```sh
make appimage APK=/path/to/app.apk AOT=0
python3 scripts/make_runtime_template.py build/appimage/PACKAGE.AppDir build/runtime-template.tar.gz
cd go-wasm/launcher
go build -o ../../build/shiraz-payload-launcher .
cd ../..
python3 scripts/make_browser_base.py build/runtime-template.tar.gz build/shiraz-payload-launcher dist/Shiraz-Browser-Base-$(cat VERSION)-$(uname -m).AppImage
```

For a **Flatpak** base (or a direct native CLI Flatpak bundle):

```sh
make flatpak APK=/path/to/app.apk AOT=0
python3 scripts/make_flatpak_base.py build/flatpak/io.github.spectre_oss.shiraz.PACKAGE.stage dist/Shiraz-Flatpak-Base-$(cat VERSION)-$(uname -m).flatpak-base
```

Both base scripts strip the source APK, native libraries, app launch files,
desktop icon, and app-specific provenance so the base can be reused for any APK
the current Shiraz runtime supports. Flatpak bundles derive their App ID as
`io.github.spectre_oss.shiraz.<sanitized_package>` and target `org.freedesktop.Platform//24.08`
(hardware GPU/Mesa via `--device=dri`, Wayland/X11, PipeWire/PulseAudio, notifications,
portals, and optional `--no-internet` / sandbox network toggle).

## Build an AppImage or Flatpak in the browser

The [Shiraz site](https://spectre-oss.github.io/shiraz/#builder) includes the
SvelteKit builder directly on the page. Select the **Linux package format**
(**AppImage** or **Flatpak**), choose the **Linux device architecture**
(`x86_64` or `ARM64`), and select an APK, then build. The published runtime is
checked against its size and SHA-256 before assembly. APK data stays in the browser.

For the complete site locally, install Node 24+, npm and Go, then run from the
repository root:

```sh
npm ci --prefix landing
make landing-dev
```

`make landing-check` checks the Svelte components; `make landing-build` generates
the static site. See [frontend development](../landing/README.md) for browser tests
and GitHub Pages base-path configuration.

The standalone offline builder remains available in the release ZIP. To run its
original Go demo directly:

```sh
cd go-wasm
python3 bootstrap_font.py
GOOS=js GOARCH=wasm go build -o shiraz-assemble.wasm .
cp "$(go env GOROOT)/lib/wasm/wasm_exec.js" .
python3 -m http.server 8000
```

Open `http://localhost:8000/demo.html`, select the versioned base AppImage under
"Use a local base AppImage", choose the matching Linux device architecture, and select an APK, then download the result. On the deployed
GitHub Pages site, the matching base loads automatically from the site when you
build. The integrated site uses its published release manifest; the standalone demo also
checks the latest prerelease through the GitHub Releases API. Your APK is never uploaded. On Linux:

```sh
# AppImage:
chmod +x APK-Shiraz-VERSION-x86_64.AppImage
./APK-Shiraz-VERSION-x86_64.AppImage --version

# Flatpak:
flatpak install --user ./io.github.spectre_oss.shiraz.PACKAGE-Shiraz-VERSION-x86_64.flatpak
flatpak run io.github.spectre_oss.shiraz.PACKAGE --version
```

The WASM APIs are:
- `shirazBuildFromApk(baseBytes, apkBytes, version)` → `{bytes: Uint8Array, sha256: string, package: string, architecture: string}` or `{error: string}`.
- `shirazBuildFlatpakFromApk(baseBytes, apkBytes, version, {allowNetwork?: boolean})` → `{bytes: Uint8Array, sha256: string, package: string, appId: string, architecture: string, filename: string}` or `{error: string}`.
The older `shirazAssemble` API remains available for producing an AppDir archive.

## Limits

The builder accepts APKs with `minSdk <= 27` and a launcher activity. Native
libraries must match the base: `x86_64` for x86_64 Linux or `arm64-v8a` for
ARM64 Linux. APKs without native libraries can use either base. ARM32 is not
supported. Selecting ARM64 does not guarantee compatibility with a particular
Linux phone; device graphics, input and app behavior still require testing.
Shiraz's Android compatibility remains incremental. The browser cannot execute
the resulting Linux AppImage; run it on Linux to test actual app behavior.
The current browser code buffers the base, APK, and output in memory. Large APKs
may exceed a browser's memory limit. The browser manifest parser covers common
APK fields, but has not yet been proved equivalent to Shiraz's Python inspector
for every APK; unsupported inputs should fail explicitly or be tested with the
Linux packager.

Generated templates, WASM binaries, `wasm_exec.js`, and AppImages are local
build artifacts and should not be committed.

## GitHub Actions release and Pages

The build tools are pinned by `flake.nix`. On a Linux host with Nix, run
`nix develop -c make deps` and `nix develop -c make build` to use the same
tool versions as CI. The Ubuntu runner provides system development libraries
for the native AppImage build; the flake supplies Go, Java, Python, Node,
Meson, Ninja, and the other build tools.

Every push to `main` runs the `Shiraz prerelease and Pages` workflow. It reads
the pushed Conventional Commit, increments `VERSION`, and pushes a version
commit as `github-actions[bot]`. A breaking change bumps major, `feat` bumps
minor, and every other accepted type bumps patch. If the source commit already
contains the expected version, the workflow uses it without a second bump.
The workflow builds the runtime from source using a generated fixture APK on
native x86_64 and ARM64 runners without QEMU. It publishes both base AppImages
and the browser builder ZIP in a versioned
GitHub prerelease, uploads them as run artifacts, and deploys the matching
browser site to GitHub Pages. The site bundle also contains the versioned base
AppImage so browser fetching stays on the same origin. The builder ZIP does not
contain a base; select one locally when serving that ZIP outside Pages. No APK
is included in a release. Publication waits for both architecture builds and
fixture checks to pass. The Debian web package remains amd64-only.

Build the runtime, payload launcher and base on the same architecture. The base
packager verifies the launcher architecture; renaming an x86_64 artifact does
not produce an ARM64 build. The assembler derives its target from the base ELF
header and rejects native libraries with a different architecture.
