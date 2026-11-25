<div align="center">

<a href="https://shiraz.foo/">
  <img src=".github/readme-banner.svg" alt="Shiraz — Your Android™ apps. A little more at home." width="100%" />
</a>

<br /><br />

<a href="https://github.com/spectre-oss/shiraz/releases"><img src="https://img.shields.io/github/v/tag/spectre-oss/shiraz?sort=semver&filter=v*&style=flat-square&label=version&labelColor=2b0f1c&color=f4b63f" alt="Version" /></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--only-25c4b4?style=flat-square&labelColor=2b0f1c" alt="License: GPL-3.0-only" /></a>
<a href="#status"><img src="https://img.shields.io/badge/platforms-Linux%20%7C%20macOS%20%7C%20Windows%20(exp.)-ff5d8f?style=flat-square&labelColor=2b0f1c" alt="Platforms: Linux, macOS, and Windows" /></a>
<a href="docs/framework_final_goal.md"><img src="https://img.shields.io/badge/API%2035-4%2C445%20classes-f9b233?style=flat-square&labelColor=2b0f1c" alt="API 35: 4,445 classes" /></a>
<a href="docs/ndk_final_goal.md"><img src="https://img.shields.io/badge/NDK-15%2F15%20libs%20(887%20syms)-3fbf78?style=flat-square&labelColor=2b0f1c" alt="NDK: 15 of 15 libraries, 887 symbols" /></a>
<a href="#how-it-works"><img src="https://img.shields.io/badge/UI-GTK--free-4f6df0?style=flat-square&labelColor=2b0f1c" alt="GTK-free" /></a>
<a href="https://github.com/spectre-oss/shiraz/actions/workflows/quality.yml"><img src="https://img.shields.io/github/actions/workflow/status/spectre-oss/shiraz/quality.yml?branch=main&style=flat-square&label=checks&labelColor=2b0f1c&color=3fbf78" alt="Code quality checks" /></a>

<br /><br />

[Try Builder](https://shiraz.foo/#builder) · [Compatibility](https://shiraz.foo/compatibility/) · [Compare Peers](https://shiraz.foo/compare/) · [Shiraz Web](docs/WEB.md) · [macOS Launcher](macos/Launcher/README.md) · [Documentation](docs/README.md)

</div>

---

[Shiraz](https://www.openstreetmap.org/node/26082452) is an experimental, open-source (**GPL-3.0-only**) Android™ compatibility layer that runs supported Android APKs directly on **Linux** (`x86_64` and `ARM64`), with an active native port for **Apple Silicon macOS**, a CI-verified native **Windows** (`x64` and `ARM64`) foundation, and a long-term goal of supporting **Linux phones**. It executes app bytecode on standalone **ART** and translates Android framework and NDK calls directly to host OS libraries — without booting an Android system image, running a virtual machine, requiring kernel binder or root, or depending on GTK.

> [!WARNING]
> **Experimental — OS process sandboxing on by default, not a full Android SELinux sandbox.** `shiraz run`, `shiraz install-desktop`, `shiraz daemon`, and Linux AppImages now apply OS-level confinement by default (`--no-sandbox` opt-out; `--no-network`/`--no-internet` to deny sockets): Linux Landlock LSM + `PR_SET_NO_NEW_PRIVS`, macOS Seatbelt SBPL, and Windows Job Objects + Restricted Tokens, alongside Flatpak bubblewrap bundles. This confines writes to the app's own data/cache directories and temp space, but is not Android's full per-UID/SELinux sandbox. **Only run APKs you trust.** When testing unfamiliar APKs, use an isolated environment such as an unprivileged LXC container or a dedicated VM without exposing personal files or sensitive host resources. See [SECURITY.md](SECURITY.md).

## One runtime, four ways to use it

| Interface | What it does | Best for |
| --- | --- | --- |
| **Shiraz Runtime** | The core CLI, standalone ART host (with warm standby daemon on Linux), framework layer, 15 NDK libraries, and native OS adapters ([development guide](docs/DEVELOPMENT.md)). | Running APKs directly on Linux/macOS desktops, installing desktop entries (`shiraz install-desktop`), and developing Android compatibility. |
| **[Shiraz Builder](https://shiraz.foo/#builder)** | Packages an APK and the runtime into a standalone Linux **AppImage** or sandboxed **Flatpak** (`.flatpak`) directly in your browser using Go WebAssembly — your APK stays local ([guide](docs/BUILDER.md)). | Turning a supported APK into a single-file or sandboxed Linux desktop application with no upload. |
| **[Shiraz Browser Runner](https://shiraz.foo/run/)** *(exp.)* | Runs supported pure-DEX and `arm64-v8a` APKs 100% client-side in your browser tab by executing Shiraz’s `aarch64` Linux runtime inside [pithings/unkernel](https://github.com/pithings/unkernel) (Linux 7.3 + `kwemu` on WebAssembly) with zero backend compute. | Dropping an APK into a modern browser (Chrome/Edge 137+, Firefox 153+, Safari 27+) with no server or install. |
| **[Shiraz Web](docs/WEB.md)** | Runs an APK on a Linux host and streams its screen, touch, and keyboard to any browser over WebRTC. | Trying apps through a browser locally or on Linux phones from a host. |
| **[Shiraz Launcher](macos/Launcher/README.md)** *(macOS, exp.)* | Native SwiftUI shelf for Apple Silicon Macs with per-app screen presets, F-Droid catalog browsing, Dock icons, and live console ([watch demo](https://shiraz.foo/#mac)). | Managing, configuring, and launching local APKs on Apple Silicon. |

Builder creates standalone Linux applications, Web streams from a Linux host, and Launcher drives local runs on Apple Silicon. All of them share the Shiraz runtime and its recorded compatibility limits.

## How it works

Shiraz executes Android DEX bytecode with a standalone host build of **ART** (Android's runtime) and translates Android framework, resource, and JNI/NDK calls into native host functionality using **SDL2**, **EGL/GLES**, **Skia Ganesh**, and OS-specific bridges:

- **Linux (`x86_64`, `aarch64`):** Warm ART standby daemon & AOT/page-cache pre-warming, zero-copy GBM/DMA-BUF swapchain, `SurfaceTexture`, `EGL_ANDROID_native_fence_sync`, live vsync `AChoreographer`, ALSA/PipeWire AAudio & OpenSL ES, `libvulkan.so` (`VK_KHR_android_surface`, `AHardwareBuffer`, real-ICD `PackageManager` probe, bundled in AppImages/Flatpaks), default-on Landlock + `PR_SET_NO_NEW_PRIVS` sandboxing (`--no-sandbox` opt-out, `--no-network`/`--no-internet` seccomp), [Digitalis](https://digitalisx64.github.io/) ARM64-on-`x86_64` bundling (`--digitalis`), plus AppImage and sandboxed Flatpak packaging.
- **macOS (Apple Silicon `arm64`):** Native Mach-O ART (`-Xint`, with `mach_vm_remap`, `MAP_JIT` + `pthread_jit_write_protect_np`, and ARM64 fault-handler primitives) with `WarmArtPool` standby wired into `HostSession` and `ShirazDaemon` (`2.67×` faster launch on `2048`), opt-in guest ARM64 ELF/Bionic loader + pinned **MoltenVK v1.4.2** (live Metal Vulkan 1.4.357 on Apple M2 Pro across host `libvulkan.so`, guest AAPCS64 thunks, and Java `PackageManager` `level=1`), **ANGLE (Metal)** zero-copy `IOSurface` swapchain, `SurfaceTexture`, `AHardwareBuffer` `EGLImage`, `MTLSharedEvent` native fences, 120 Hz `CVDisplayLink` vsync, VideoToolbox H.264/HEVC/VP9/AV1, AudioToolbox AAudio/Opus, `Security.framework` TLS trust (`wolfJSSE` + OkHttp), CoreLocation, `UNUserNotificationCenter`, default-on Seatbelt sandboxing, signed `.app` bundling, and browser `.app` sidecar zip packaging.
- **Windows (`x64`, `arm64`):** CI-verified native Win32 primitives (`CreateFileMappingW`, `WaitForMultipleObjects`, Named Pipe IPC, Job Object + Restricted Token sandbox), SDL2 Win32 presentation/IME/clipboard/keyboard + Per-Monitor V2 DPI & ANGLE-D3D11 wiring, DXGI shared `AHardwareBuffer` pool, `DwmFlush` choreographer, `libvulkan.dll`, WASAPI `libaaudio.dll` & `libOpenSLES.dll`, Media Foundation decode + `IMFSourceReader` camera capture bridge, DirectWrite, Win32 ART `NativeBridge` v3 (`libshiraz-win32-guest.dll`) + Guest ELF (`SysV ↔ Win64` thunks), pinned `patches/art-win32-build.patch` + `bootstrap_windows.py` orchestrator, and portable/MSIX bundler (live Win32 `libart.dll` execution in progress).

| Layer | Role | Source |
| --- | --- | --- |
| `01` **Your APK** | Inspects the unmodified package, manifest, resources, and native ABIs. | [`src/shiraz/`](src/shiraz) |
| `02` **ART** | Standalone host build executes DEX bytecode (interpreter, AOT, and warm standby pool on Linux). | [`scripts/bootstrap_runtime.py`](scripts/bootstrap_runtime.py) |
| `03` **Android™ API layer** | All **4,445 / 4,445** Android 15 / API 35 `android.*` class surfaces across 49 packages in Java + ART boot jars (bounded per-subsystem behavior). | [`java/android/`](java/android) |
| `04` **Native bridges** | All **15 / 15** tracked API 35 NDK C/C++ libraries (**887 / 887** audited symbols), JNI, Bionic compatibility, Skia, fonts, audio, SQLite, zero-copy EGL/GLES, Vulkan loader hooks, and guest ELF loading. | [`native/`](native) |
| `05` **Desktop host** | Presents windows, input, services, and isolation directly on Linux, macOS, or Windows without GTK. | [`native/presentation.cpp`](native/presentation.cpp) |

## Status

| Platform | State | Current scope & limits |
| --- | --- | --- |
| **Linux** (`x86_64`, `aarch64`) | Experimental · Primary | Prebuilt `x86_64` and `aarch64` browser base AppImages and Flatpak bases, the browser builder, and an `amd64` `shiraz-web` Debian package are published on [GitHub Releases](https://github.com/spectre-oss/shiraz/releases). 18 recorded apps across 8 engines ([tested applications](docs/tested.md), [compatibility tracker](https://shiraz.foo/compatibility/)) with behavioral limits documented. |
| **macOS** (Apple Silicon) | Experimental · Active port | **48 verified / 21 partial / 22 open** items in [docs/macos_final_goal.md](docs/macos_final_goal.md). Live Apple Silicon walkthroughs on Metal/ANGLE verified OsmAnd+ (vector maps, Persian RTL, offline search, turn-by-turn routing/navigation, 468 MB live HTTPS downloads, resize, zoom persistence), F-Droid v2.0.2 (Material 3 Compose UI + two-pane landscape), Math (ARM64 native), Editor, Crossword, SolitaireCG (with restart persistence), 2048 (`402 ms` `WarmArtPool` standby reuse, `2.67×` faster), and Telegram 13.2.1 auth, plus live MoltenVK v1.4.2 Metal Vulkan 1.4.357 (`Apple M2 Pro`, Java `PackageManager` `level=1`). Building `libart-compiler.so` JIT, M5 hardened signing gate, and Linux/Waydroid parity remain open. |
| **Windows** (`x64`, `arm64`) | Experimental · Foundation | **32 verified / 5 partial / 0 open** items in [docs/windows_final_goal.md](docs/windows_final_goal.md) (CI-verified SDL2 Win32 presentation, DXGI, `libvulkan.dll`, WASAPI, Media Foundation decode + `IMFSourceReader` camera capture, DirectWrite, portable/MSIX bundler, plus cross-platform tested Per-Monitor V2 DPI/ANGLE-D3D11 layer, Win32 ART `NativeBridge` v3 + guest ELF `SysV ↔ Win64` thunks, and pinned `patches/art-win32-build.patch` + `bootstrap_windows.py` orchestrator). Live Win32 `libart.dll` execution remains open. |
| **Linux phones** | Future goal | Long-term target. `aarch64` base AppImages/Flatpaks build in CI and Shiraz Web targets mobile touch and keyboard controls, but phone-level compatibility is not yet established. |

- **Framework & NDK coverage:** All **4,445 / 4,445** Android API 35 `android.*` framework class surfaces (`49 / 49` areas) and all **15 / 15** tracked API 35 NDK C/C++ libraries (`887 / 887` symbols) are built and audited — see [Framework APIs goal](docs/framework_final_goal.md), [NDK libraries goal](docs/ndk_final_goal.md), and [missing Android libraries](docs/MISSING_ANDROID_LIBRARIES.md). Class/symbol presence is not CTS certification or a whole-Android compatibility percentage.
- **How Shiraz compares:** See [Shiraz and its peers](https://shiraz.foo/compare/) for a side-by-side comparison with ATL, ACL, a4l, Mac.apk, apklayer, Innkeeper, Waydroid, and Android Emulator.
- **Local verification:** With pinned development dependencies prepared, run `make check` on Linux or `make macos-guest-check` on Apple Silicon macOS.

## Get started

- **Package an AppImage or Flatpak in your browser:** Open [Shiraz Builder](https://shiraz.foo/#builder) — no upload, everything runs locally in WebAssembly ([Builder guide](docs/BUILDER.md)).
- **Run apps through a browser:** Install the `shiraz-web` `.deb` package from [Releases](https://github.com/spectre-oss/shiraz/releases) or run `go-server` from source ([Web setup guide](docs/WEB.md)).
- **Build and run from source:** With runtime dependencies prepared (see [development guide](docs/DEVELOPMENT.md) and [Makefile](Makefile)):

```sh
# Build runtime and run an APK directly on Linux
make build
make run APK=/absolute/path/application.apk

# Package a standalone Linux AppImage or sandboxed Flatpak (.flatpak)
make appimage APK=/absolute/path/application.apk
make flatpak APK=/absolute/path/application.apk

# Or install a per-APK desktop entry (Linux .desktop, macOS ~/Applications, Windows Start Menu)
python3 -m shiraz install-desktop /absolute/path/application.apk
```

To verify and fetch an open-source APK from F-Droid using Shiraz's pinned repository check:

```sh
python3 -m shiraz fdroid sync --cache fdroid
python3 -m shiraz fdroid fetch org.example.app --cache fdroid --output app.apk
```

On Apple Silicon macOS, build the native app shelf with `make launcher-app` — see [macOS Launcher](macos/Launcher/README.md) and the [macOS checklist](docs/macos_final_goal.md).

<details>
<summary><strong>Advanced runtime &amp; developer flags</strong></summary>

- **Default-on cross-platform sandboxing (`--no-sandbox` opt-out, `--no-network`):**

  ```sh
  # Sandboxed by default across CLI, install-desktop, daemon, and AppImages:
  PYTHONPATH=src python3 -m shiraz run /absolute/path/application.apk --no-network
  # Explicitly opt out for debugging only:
  PYTHONPATH=src python3 -m shiraz run /absolute/path/application.apk --no-sandbox
  ```

  Applies Linux Landlock LSM + `PR_SET_NO_NEW_PRIVS` + seccomp-BPF network deny, macOS Seatbelt (`/usr/bin/sandbox-exec`) SBPL profile, or Windows Job Object limits by default.

- **Block direct internet access in a packaged Linux AppImage (`--no-internet`):**

  ```sh
  ./YourApp.AppImage --no-internet
  ```

  Enables a Linux seccomp policy (on top of default-on Landlock filesystem confinement, `--no-sandbox` to disable) that blocks non-Unix socket creation in Java/native code and child processes, closes inherited sockets, and disables `io_uring`. Launch fails closed if the policy cannot be installed. Local Unix IPC remains available for display and audio; this is not a complete Android SELinux sandbox and does not prevent network access through an external local service. Apps requiring servers may not function offline. Use `--diagnose` to inspect the selected policy without launching.

- **Native-only ABI splits (`--native-split`):**

  ```sh
  PYTHONPATH=src python3 -m shiraz run base.apk --native-split split_config.arm64_v8a.apk
  ```

  Accepts a matching native-only ABI split without modifying the APKs. Package/version mismatches and code or resource splits are rejected. This is not general APKM installation support.

- **Experimental ARM64 translation on `x86_64` Linux (Digitalis):**
  Point to the built bridge via `SHIRAZ_DIGITALIS_LIBRARY=/absolute/path/libberberis_arm64.so`, or bundle Digitalis directly into an AppImage or Flatpak with `--digitalis` (`--digitalis-dir /path/to/digitalis`) in `scripts/build_appimage.py` / `scripts/build_flatpak.py`. Build its core with `make runtime-digitalis` and platform proxies with `make digitalis-platform-proxies`. The pinned Digitalis guest-support libraries must be visible under `/system` inside the test process's private namespace (never install them over the host). Run `make digitalis-platform-smoke DIGITALIS_GUEST_SYSTEM=/path/to/guest/system` to check translated math, thread attributes/signals/exit, and the existing NDK media subset. Unsupported media APIs fail explicitly.

- **Virtual touch from mouse drags (`SHIRAZ_MOUSE_AS_TOUCH=1`):**
  Exposes a virtual single-touch device and translates left-button mouse drags into Android finger gestures. Physical touch input and host mouse capture remain available; unset it for normal mouse events.

</details>

## Frequently asked questions

**How is Shiraz different from Waydroid or Android emulators?**
Waydroid boots a full LineageOS Android system in an LXC container and requires Linux kernel binder support; emulators boot Android inside a virtual machine. Shiraz runs only the app on standalone ART over native host libraries — no OS image, no VM, no binder kernel module, and no root.

**How does Shiraz differ from Android Translation Layer (ATL)?**
Both are GPL-3.0 standalone-ART translation layers (Shiraz ships ATL's `bionic_translation` linker under its original notice). Shiraz is GTK-free (SDL2 across Linux, macOS, and Windows), adds in-browser AppImage and Flatpak packaging, WebRTC streaming, warm ART pre-warming, and native macOS/Windows host backends. See [the full 7-project comparison](https://shiraz.foo/compare/).

**Does Shiraz include Google Play Services or run every Android app?**
No. Shiraz does not ship the Google Play Store or Google Play Services, and compatibility grows app by app. Check [the live compatibility catalog](https://shiraz.foo/compatibility/) and [docs/tested.md](docs/tested.md) for verified flows and open limits.

**Can AI tools or search engines read a structured summary of Shiraz?**
Yes — up-to-date machine-readable summaries are published at [`llms.txt`](https://shiraz.foo/llms.txt) and [`llms-full.txt`](https://shiraz.foo/llms-full.txt).

## Documentation

| Topic | Guide |
| --- | --- |
| Full documentation index | [docs/README.md](docs/README.md) |
| Cross-platform host architecture (Linux, macOS, Windows) | [docs/cross_platform.md](docs/cross_platform.md) |
| Setup, architecture, builds, and tests | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Observed app results and limits | [docs/tested.md](docs/tested.md) |
| Live compatibility & API 35/NDK tracker | [shiraz.foo/compatibility/](https://shiraz.foo/compatibility/) |
| Comparison with ATL, ACL, a4l, Mac.apk, apklayer, Innkeeper | [shiraz.foo/compare/](https://shiraz.foo/compare/) |
| Browser AppImage & Flatpak builder (Go/WASM) | [docs/BUILDER.md](docs/BUILDER.md) |
| WebRTC browser runner | [docs/WEB.md](docs/WEB.md) |
| macOS Launcher (SwiftUI) | [macos/Launcher/README.md](macos/Launcher/README.md) |
| Android API 35 Java roadmap (4,445 classes) | [docs/framework_final_goal.md](docs/framework_final_goal.md) |
| Android API 35 NDK libraries roadmap (15 libs / 887 symbols) | [docs/ndk_final_goal.md](docs/ndk_final_goal.md) |
| Native macOS (Apple Silicon) checklist | [docs/macos_final_goal.md](docs/macos_final_goal.md) |
| Native Windows (`x64` / `arm64`) checklist | [docs/windows_final_goal.md](docs/windows_final_goal.md) |
| AI-assisted isolated testing & PRs | [docs/AI_CONTRIBUTING.md](docs/AI_CONTRIBUTING.md) |
| Security model and isolation boundaries | [SECURITY.md](SECURITY.md) |

## Contributing

Bug reports, compatibility fixes, tests, and clearer documentation all help Shiraz grow. Start with the [documentation index](docs/README.md) and [development guide](docs/DEVELOPMENT.md).

If you have access to a capable AI coding assistant — such as GPT 6.1 Sol, Astra, Opus 5.5, or Sonnet 5.5 — we would especially appreciate help reproducing and fixing real app bugs. Start with an app you care about and trust, and follow the [AI-assisted contribution guide](docs/AI_CONTRIBUTING.md) covering isolated execution, evidence collection, issue reports, and pull requests. You do not need an AI assistant to contribute; please review and test any AI-generated changes before submitting.

### Help fund Shiraz

Shiraz is expensive to develop: AI tools, compute, and testing cost real money. We have plenty of ideas and future work planned, but funding is a real limit on how much we can do.

So far, we have received no financial support from OpenAI or Anthropic. All funding comes from us — people who love open source, believe in Linux phones, and want everyone to have the freedom to control their own hardware. If you like Shiraz and believe in that future, please consider donating. Your support helps us keep building. Thank you.

## Acknowledgments

Shiraz is built with the help of AI coding assistants: OpenAI Codex (primarily using GPT Astra and GPT 6.1 Sol) and Grok CLI for general tasks.

## Credits & license

ARM64 support builds on Digitalis and AOSP Berberis. Thanks to [utzcoz](https://github.com/utzcoz), the Digitalis contributors, and the Android Open Source Project — see [LICENSES/DIGITALIS-NOTICE.txt](LICENSES/DIGITALIS-NOTICE.txt).

Shiraz's original code is licensed under [GPL-3.0-only](LICENSE). Third-party code, libraries, and fonts retain their own licenses and copyright notices; Shiraz's license does not replace them. Thanks also to the projects documented in [LICENSES](LICENSES):

| Component | License & notice |
| --- | --- |
| AOSP contributions & ripple rendering adaptations | [Apache-2.0](LICENSES/Apache-2.0.txt) · [AOSP notice](LICENSES/AOSP-NOTICE.txt) · [Ripple notice](LICENSES/RIPPLE-NOTICE.txt) |
| Bionic translation linker (`android_translation_layer`) | Original BSD-style notice · [Bionic linker notice](LICENSES/BIONIC-LINKER.txt) |
| Skia & libsndfile | BSD-3-Clause / LGPL-2.1-or-later · [Skia notice](LICENSES/SKIA-NOTICE.txt) · [sndfile notice](LICENSES/SNDFILE-NOTICE.txt) |
| Noto fonts | SIL Open Font License 1.1 · [Noto fonts notice](LICENSES/NOTO-FONTS.txt) |
| macOS text, path, and image libraries | FreeType (FTL), HarfBuzz / fontconfig / pixman (MIT-style), cairo (LGPL-2.1), libwebp (BSD-3-Clause + patent grant), libjpeg-turbo (IJG, BSD-3-Clause, zlib) · [macOS graphics notice](LICENSES/MACOS-GRAPHICS-NOTICE.txt). *This software is based in part on the work of the Independent JPEG Group.* |
| [unkernel](https://github.com/pithings/unkernel) (`Linux 7.3` + `kwemu` on WebAssembly, by Pooya Parsa) | MIT (JS SDK) & GPL-2.0 (kernel bundle) · [unkernel notice](LICENSES/UNKERNEL-NOTICE.txt) |
| macOS OpenGL ES & GPU canvas libraries | ANGLE (BSD-3-Clause), Abseil (Apache-2.0), Chromium zlib (Zlib), Chromium zlib/google & anglebase (BSD-3-Clause), xxHash (BSD-2-Clause), Skia with skcms (BSD-3-Clause) · [macOS GPU notice](LICENSES/MACOS-GPU-NOTICE.txt) |

These are acknowledgments, not a complete license inventory for every build. Redistributed components must retain their applicable license texts and notices.

[Android is a trademark of Google LLC.](https://developer.android.com/legal) Other product names and trademarks belong to their respective owners. Shiraz is an independent project; references to other projects or companies do not imply affiliation or endorsement.

---

<div align="center">

<p dir="rtl" lang="fa">
بس بگردید و بگردد روزگار<br />
دل به دنیا در نبندد هوشیار<br />
ای که دستت می‌رسد کاری بکن<br />
پیش از آن کز تو نیاید هیچ کار
</p>

<p><em>Many have passed, and time will turn; / The wise do not bind their hearts to this world. / While you still have the power, do some good, / Before the day you can do nothing more.</em></p>

<p><a href="https://ganjoor.net/saadi/mavaez/ghasides/sh28">سعدی — قصیدهٔ شمارهٔ ۲۸</a> · Saadi</p>

</div>
