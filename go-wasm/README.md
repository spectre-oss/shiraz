# Shiraz browser AppImage builder

The generic x86_64 base AppImage contains the Shiraz runtime but no APK. This
Go/WASM module reads an APK in the browser, parses its Android binary manifest,
creates Shiraz launch metadata, and appends an application payload to the base.
The downloaded result is one AppImage file. On Linux, its bundled launcher
verifies and mounts that payload beside the prebuilt runtime, then starts Shiraz.

## Build the base once on Linux

From the Shiraz repository root, first build any supported APK with `AOT=0` so
the prebuilt runtime components exist. Then run:

```sh
python3 scripts/make_runtime_template.py build/appimage/PACKAGE.AppDir build/runtime-template.tar.gz
cd go-wasm/launcher
go build -o ../../build/shiraz-payload-launcher .
cd ../..
python3 scripts/make_browser_base.py build/runtime-template.tar.gz build/shiraz-payload-launcher dist/Shiraz-Browser-Base-$(cat VERSION)-x86_64.AppImage
```

The template script removes the source APK, native libraries, app launch files,
desktop icon, and app-specific provenance. The base can be reused for APKs that
the current Shiraz runtime supports.

## Build an AppImage in the browser

```sh
cd go-wasm
python3 bootstrap_font.py
GOOS=js GOARCH=wasm go build -o shiraz-assemble.wasm .
cp "$(go env GOROOT)/lib/wasm/wasm_exec.js" .
python3 -m http.server 8000
```

Open `http://localhost:8000/demo.html`, select the versioned base AppImage and
an APK, then download the result. The browser does not upload the APK. On Linux:

```sh
chmod +x APK-Shiraz-VERSION-x86_64.AppImage
./APK-Shiraz-VERSION-x86_64.AppImage --version
```

The WASM API is `shirazBuildFromApk(baseBytes, apkBytes, version)`. It returns
`{bytes: Uint8Array, sha256: string, package: string}` or `{error: string}`.
The older `shirazAssemble` API remains available for producing an AppDir archive.

## Limits

This builder supports x86_64 APKs with `minSdk <= 27` and a launcher activity.
Shiraz's Android compatibility remains incremental. The browser cannot execute
the resulting Linux AppImage; run it on Linux to test actual app behavior.
The current browser code buffers the base, APK, and output in memory. Large APKs
may exceed a browser's memory limit. The browser manifest parser covers common
APK fields, but has not yet been proved equivalent to Shiraz's Python inspector
for every APK; unsupported inputs should fail explicitly or be tested with the
Linux packager.

Generated templates, WASM binaries, `wasm_exec.js`, and AppImages are local
build artifacts and should not be committed.

## GitHub Actions artifact

Run **Browser AppImage kit** from the Actions tab, or push a `v*` tag. The
workflow builds the runtime from source with a generated fixture APK, then
uploads `shiraz-browser-kit-x86_64` as a run artifact. Download and unzip it,
serve the `go-wasm/` directory with an HTTP server, and select the included
versioned base AppImage in `demo.html`. The artifact contains the demo, WASM
assembler, Go browser support script, generic base AppImage, and SHA-256 file.
It does not contain an APK or an app-specific AppImage. This workflow currently
produces x86_64 only; ARM browser packaging needs a separate ARM base and ABI
support in the assembler.
