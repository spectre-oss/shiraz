# Developing Shiraz

Shiraz runs supported Android apps on Linux without an Android system image or
virtual machine. Linux desktops are the current focus; Linux phones are a
longer-term goal. Compatibility is experimental.

Shiraz does not sandbox apps like Android. Use trusted APKs, or a separate,
restricted test environment such as an unprivileged LXC container without
personal files or sensitive host resources. Keep APKs and app data out of Git.

## Find the right component

| Location | Purpose |
| --- | --- |
| `native/`, `include/` | C++ runtime adapters, JNI, windows, graphics, input, and native APIs. |
| `java/android/`, `java/shiraz/` | Android framework compatibility and runtime integration. |
| `src/shiraz/` | Python APK inspection, packaging, and launch tooling. |
| `go-wasm/` | Browser builder: packages an APK with a prepared runtime into an AppImage. |
| `go-server/` | Web runner: executes apps on a Linux host and streams their screen and controls to a browser. |
| `scripts/`, `tests/` | Dependency preparation, build helpers, regression tests, and fixtures. |

Builder and Web share the core runtime. Packaging an APK successfully does not
mean its Android APIs or native libraries are supported.

## Prepare a build environment

Use x86-64 Linux for the current desktop packaging workflow. The repository's
[Nix development shell](../flake.nix) pins build tools, but native compiler and
system-library dependencies must also be installed. Follow the **Install native
build dependencies** step in the [build workflow](../.github/workflows/browser-appimage.yml)
for the reference Ubuntu package list. Graphics builds also require GLES1
development support, including `libGLESv1_CM`.

From the repository root, enter the tool environment:

```sh
nix develop
```

Then prepare fonts, SDL, standalone ART, and the runtime:

```sh
python3 scripts/bootstrap_fonts.py
make deps
make runtime-art
make build
```

The reference Ubuntu workflow selects the system JDK 17 for JNI builds; follow
its environment settings when reproducing that build. `make deps` prepares SDL,
not every dependency. Initial ART preparation downloads and builds substantial
dependencies, so allow disk space and time. Generated files belong in ignored
directories such as `build/`, `.local/`, and `context/`.

## Run or package an app

With the runtime prepared, inspect a trusted APK before running or packaging it:

```sh
make inspect APK=/absolute/path/application.apk
make run APK=/absolute/path/application.apk
make appimage APK=/absolute/path/application.apk
```

AppImage packaging may prepare additional media and runtime components. Use the
build output to locate the resulting artifact; do not commit it or its source APK.

For the other interfaces:

- **Builder:** follow the [browser builder guide](BUILDER.md) to prepare a
  reusable base AppImage and build the Go/WASM frontend. Packaging happens in the
  browser; the generated application runs on Linux.
- **Web:** follow the [web runner guide](WEB.md) for installation or
  source setup. It requires a prepared runtime and FFmpeg with VP8 encoding.
  The default service is local-only; remote access requires authentication,
  transport security, and suitable WebRTC configuration.
- **ARM64 translation:** `make runtime-digitalis` builds the optional Digitalis
  integration. It requires the pinned sources, prepared ART, compiler headers,
  and matching guest libraries. See [attribution and provenance](../LICENSES/DIGITALIS-NOTICE.txt).
  Individual experimental proxies can be built with
  `make digitalis-nativewindow-proxy DIGITALIS_PROXY_OPTIONS="--kind android"`.
  Other kinds include `opensles`, `android_runtime`, `gles2`, and `gles3`. The Android
  proxy fetches checksum-pinned AOSP headers into the ignored build tree.
  Use `--library-dir` and `--android-library-dir` for an existing host runtime;
  GLES2/3 require Shiraz's EGL adapter. Building a proxy does not establish full
  API support: consult the per-app experiment results and test real callbacks.
  `make digitalis-thread-smoke` exercises ARM64 callbacks, TLS, thread identity,
  join, detach, key destructors, and thread naming. `make guest-thread-names-smoke`
  checks live handle resolution, cleanup, and fork handling on the host side.
  The launcher and ARM64 fixture select the configured bridge by absolute path
  to avoid silently loading a different copy with the same filename.
  Configure an existing runtime with
  `DIGITALIS_THREAD_OPTIONS`; `scripts/digitalis/thread_smoke.py --help` lists
  the available paths and compiler options. Run this only in your test sandbox.

## Make and verify a change

1. Find the failing layer: framework API, JNI/native adapter, graphics, packaging,
   or browser controls. Preserve the original APK when investigating compatibility.
2. Make a focused change. Add a regression test for a bug or new behavior when it
   provides meaningful coverage; use existing fixtures where possible.
3. Run the relevant target from the [Makefile](../Makefile), then the repository checks:

   ```sh
   make check
   ```

4. For Go changes, also run `go test ./...` from the affected Go module directory.
5. For app compatibility changes, verify the actual app: rendering, input, the
   affected interaction, and whether it survives repeated use. A passing fixture
   alone does not establish app compatibility. Record remaining limitations.

Run builds, tests, and APKs in the designated test environment when one is used.
For ownership fixes, use sanitizers or allocation-failure tests where practical;
a short successful run is not proof that every runtime path is leak-free.

## Prepare a contribution

Read [AGENTS.md](../AGENTS.md) for repository rules. Keep changes focused, preserve
third-party notices, and explain the problem, resulting behavior, and validation.
Never include APKs, generated binaries, private app names or logs, credentials,
deployment addresses, or machine-specific configuration in a commit.

After review and any required approval, stage explicit source paths and inspect
the staged diff. Run `make check` and the staged-content guard before committing:

```sh
git diff --cached
git diff --cached --check
python3 scripts/commit_guard.py
make commit MSG="fix(runtime): describe the change"
```

Use Conventional Commits. Do not edit `VERSION` manually; the release workflow
updates it. Push only when authorized.

Session logs are private workspace files, outside the Git checkout. In the
`codes/shiraz` workspace layout, use `../../docs/jobs/`. They are not part of the
public development guide or release artifacts.
