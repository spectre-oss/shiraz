// SPDX-License-Identifier: GPL-3.0-only
// Generates landing/static/llms.txt and landing/static/llms-full.txt directly
// from the checked compatibility datasets so AI crawlers (GEO/AEO) always read
// grounded, up-to-date, explicitly bounded Shiraz facts.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const readJson = rel => JSON.parse(readFileSync(`${root}${rel}`, 'utf8'));

const version = readFileSync(`${root}VERSION`, 'utf8').trim();
const compat = readJson('landing/src/lib/compatibility.json');
const hosts = readJson('landing/src/lib/host-platforms.json');
const inventory = readJson('landing/src/lib/android-inventory.json');
const ndk = readJson('landing/src/lib/ndk-inventory.json');
const support = readJson('landing/src/lib/android-support.json');

const SITE = 'https://spectre-oss.github.io/shiraz';
const REPO = 'https://github.com/spectre-oss/shiraz';

const partialApps = compat.applications.filter(a => a.status === 'Partial').length;
const blockedApps = compat.applications.filter(a => a.status === 'Blocked').length;
const historicalApps = compat.applications.filter(a => a.status === 'Historical').length;
const macHost = hosts.platforms.find(p => p.id === 'macos');
const winHost = hosts.platforms.find(p => p.id === 'windows');

const llmsTxt = `# Shiraz

> Shiraz is an open-source (GPL-3.0-only), GTK-free Android compatibility layer that runs supported Android APKs directly on Linux, Apple Silicon macOS (experimental), and Windows (experimental foundation) using standalone ART, SDL2, and native graphics/audio/OS bridges — without booting an Android system image, virtual machine, container, or kernel binder module.

Snapshot version: v${version} (data updated ${inventory.updated}).

## Core Facts

- **Execution model:** App-only API translation layer on standalone ART (with pre-warmed WarmArtPool standby daemon on Linux and macOS). No Android VM, LXC container, or binder kernel driver.
- **API 35 Java surface:** ${inventory.inventory.present.toLocaleString('en')} of ${inventory.inventory.reference.toLocaleString('en')} Android 15 / API 35 \`android.*\` class names present across ${inventory.inventory.packages.length} packages (class-name presence is not CTS certification or full behavioral parity).
- **API 35 NDK C/C++ surface:** ${ndk.librariesPresent} of ${ndk.librariesTotal} tracked NDK shared libraries built, exporting ${ndk.symbolsPresent.toLocaleString('en')} of ${ndk.symbolsReference.toLocaleString('en')} cataloged C symbols (audited with \`nm\`).
- **Host OS backends:**
  - **Linux (x86_64, ARM64 — primary):** Standalone ART, warm standby pool, GBM/DMA-BUF zero-copy swapchain, ALSA/PipeWire AAudio, bundled \`libvulkan.so\` with real-ICD \`PackageManager\` probe, default-on Landlock + \`PR_SET_NO_NEW_PRIVS\` sandboxing (\`--no-sandbox\` opt-out, \`--no-network\`/\`--no-internet\` seccomp), WebRTC streaming, AppImage & sandboxed Flatpak (\`.flatpak\`) builders (CLI \`--digitalis\` ARM64 bundling).
  - **macOS (Apple Silicon ARM64 — experimental port):** ${macHost.progress.verified} verified, ${macHost.progress.partial} partial, ${macHost.progress.open} open goal checklist items; ANGLE-Metal zero-copy IOSurface swapchain, SurfaceTexture, \`MTLSharedEvent\` native fences, 120 Hz \`CVDisplayLink\` vsync, VideoToolbox/AudioToolbox, pre-warmed \`WarmArtPool\`, opt-in guest ELF/Bionic loader + guest \`libvulkan.so\` AAPCS64 bridge booting OsmAnd+ (routing & 468 MB HTTPS downloads) & Telegram, default-on Seatbelt sandboxing, signed \`.app\` bundler.
  - **Windows (x64, ARM64 — experimental foundation):** ${winHost.progress.verified} verified, ${winHost.progress.partial} partial, ${winHost.progress.open} open items (CI-verified Win32 primitives, SDL2 presentation/IME/clipboard, DXGI shared AHB, \`libvulkan.dll\`, WASAPI AAudio/OpenSL ES, Media Foundation, DirectWrite, Job Object/Restricted Token sandbox, portable/MSIX bundler; partial foundations for ANGLE-D3D11 Per-Monitor V2 DPI, Win32 guest ELF/SysV↔Win64 thunks, and \`bootstrap_windows.py\` ART orchestrator). Live Win32 \`libart.dll\` execution remains open.
- **Recorded apps:** ${compat.applications.length} apps (${partialApps} Partial, ${blockedApps} Blocked, ${historicalApps} Historical) across 8 engines (Android framework, Flutter, React Native, Unity, libGDX, Godot, SDL, Arc).
- **Packaging:** Browser-based Go/WASM Builder produces standalone Linux AppImages and sandboxed Flatpak (\`.flatpak\`) bundles locally without uploading the APK; CLI also supports \`--digitalis\` ARM64-on-x86_64 bundling, builds signed macOS \`.app\` and Windows portable/MSIX bundles, and runs \`shiraz install-desktop\`.

## Explicit Boundaries (Do Not Misstate)

- Shiraz is experimental. No whole-Android compatibility percentage or CTS certification is claimed.
- Shiraz does **not** include the Google Play Store or Google Play Services.
- OS process sandboxing is enabled by default across \`shiraz run\`, \`shiraz install-desktop\`, \`shiraz daemon\`, Linux AppImages (\`--no-sandbox\` opt-out, \`--no-network\`/\`--no-internet\`), and Flatpak bundles, but this is OS process confinement rather than Android's full per-UID/SELinux sandbox.
- WebView uses an embedded pure-Java HTML5 DOM/JS engine (including interactive \`contentEditable\`, \`Selection\`/\`Range\`, \`execCommand\`, \`MutationObserver\`, timers, and DOM storage) rather than a full Blink/V8 browser engine.
- 32-bit ARM (\`armeabi-v7a\`) native libraries are unsupported; Digitalis ARM64 translation on \`x86_64\` Linux works in CLI runs and CLI \`--digitalis\` AppImage/Flatpak builds (not yet in prebuilt browser bases).

## Website Pages

- [Home & Browser Builder](${SITE}/): Architecture overview, Shiraz Web (WebRTC), macOS Launcher, and in-browser AppImage/Flatpak APK packager.
- [Run APK in Browser](${SITE}/run/): 100% client-side in-browser Android APK runner powered by Shiraz (aarch64 Linux runtime) inside [pithings/unkernel](https://github.com/pithings/unkernel) (Linux 7.3 + kwemu on WebAssembly). Zero backend compute.
- [Compatibility & Coverage Tracker](${SITE}/compatibility/): Filterable app compatibility catalog, 3-OS host matrix, 30 subsystem notes, 49-package API 35 breakdown, 15-library NDK audit, and behavioral probe tracker.
- [Compare Shiraz and Its Peers](${SITE}/compare/): Side-by-side comparison with ATL, ACL, a4l, Mac.apk, apklayer, Innkeeper, Waydroid, and Android Emulator.
- [Full AI Context (llms-full.txt)](${SITE}/llms-full.txt): Complete single-file reference with app records, host layers, subsystem limits, and FAQs.

## Documentation & Source

- [GitHub Repository](${REPO}): GPL-3.0-only source code, releases, and issue tracker.
- [Tested Applications](${REPO}/blob/main/docs/tested.md): Observed app walkthroughs and open limits.
- [Cross-Platform Architecture](${REPO}/blob/main/docs/cross_platform.md): Linux, macOS, and Windows host layer summary.
- [Framework APIs Goal](${REPO}/blob/main/docs/framework_final_goal.md): All 4,445 Android API 35 classes across 49 areas.
- [NDK Libraries Goal](${REPO}/blob/main/docs/ndk_final_goal.md): All 15 tracked API 35 NDK libraries (887 symbols).
- [macOS Native Goal](${REPO}/blob/main/docs/macos_final_goal.md): Apple Silicon checklist and OsmAnd+ walkthrough notes.
- [Windows Native Goal](${REPO}/blob/main/docs/windows_final_goal.md): Win32/DXGI/WASAPI/MSIX checklist and CI coverage.
- [Browser Builder Guide](${REPO}/blob/main/docs/BUILDER.md): Client-side Go/WASM AppImage and Flatpak assembly.
- [Shiraz Web Guide](${REPO}/blob/main/docs/WEB.md): Streaming apps over WebRTC to any browser or Linux phone.
- [Security Model](${REPO}/blob/main/SECURITY.md): Isolation boundaries and safe testing guidance.
`;

const llmsFull = `${llmsTxt}
---

## Detailed Host OS Capability Matrix (${hosts.updated})

${hosts.layers
  .map(
    row => `### ${row.layer}
- **Linux (${row.status.linux}):** ${row.linux}
- **macOS (${row.status.macos}):** ${row.macos}
- **Windows (${row.status.windows}):** ${row.windows}`
  )
  .join('\n\n')}

---

## Recorded Applications (${compat.applications.length} total)

${compat.applications
  .map(
    app =>
      `- **${app.name}**${app.version ? ` v${app.version}` : ''} (${app.framework}) — **${app.status}**
  - Observed: ${app.observed}
  - Limitations: ${app.limitations}
  - Evidence: ${REPO}/blob/main/docs/${app.source}`
  )
  .join('\n')}

---

## Maintained Android Subsystems (${support.areas.length} areas, reviewed ${support.updated})

${support.areas
  .map(a => `- **${a.name}** [${a.scope} · ${a.status} · ${a.verification}]: ${a.notes}`)
  .join('\n')}

---

## API 35 NDK Native Libraries (${ndk.librariesPresent}/${ndk.librariesTotal} libs, ${ndk.symbolsPresent}/${ndk.symbolsReference} symbols)

${ndk.libraries.map(l => `- \`${l.library}\`: ${l.present}/${l.reference} symbols (${l.families.join(', ')})`).join('\n')}

---

## Frequently Asked Questions (Direct Answers)

### How does Shiraz run Android apps without a virtual machine or Waydroid?
Shiraz executes Android DEX bytecode on a standalone host build of ART (Android Runtime) and translates Android framework, resource, and NDK calls directly into host OS APIs using SDL2, EGL/GLES (GBM/DMA-BUF zero-copy on Linux, ANGLE-Metal IOSurface on macOS), and native audio/IPC bridges. Unlike Waydroid or Android Emulator, Shiraz does not boot an Android OS image, does not require a virtual machine, and does not require the Linux binder kernel module or root access.

### How do I convert an Android APK into a Linux AppImage or Flatpak?
Open Shiraz Builder at ${SITE}/#builder, choose your CPU architecture (x86_64 or ARM64) and output format (AppImage or sandboxed Flatpak \`.flatpak\`), select your local APK file, and click Build. Assembly runs locally in your browser via Go WebAssembly against a SHA-256-verified runtime base — the APK never leaves your device. From source, run \`make appimage APK=/path/to/app.apk\` or \`make flatpak APK=/path/to/app.apk\`.

### How does Shiraz compare to ATL, Mac.apk, and Waydroid?
- **vs. Waydroid / Android Emulator:** Waydroid boots a full LineageOS container requiring kernel binder; Shiraz runs only the app with no OS boot or binder module, trading full-Android breadth for desktop-native footprint.
- **vs. Android Translation Layer (ATL):** Both are GPL-3.0 standalone-ART translation layers (Shiraz reuses ATL's bionic translation linker under its original notice). Shiraz is GTK-free (SDL2 across Linux, macOS, and Windows), adds browser AppImage/Flatpak packaging, WebRTC streaming, and warm ART pre-warming.
- **vs. Mac.apk:** Mac.apk targets Apple Silicon Macs exclusively using dex2jvm on OpenJDK (AGPL-3.0 + commercial) and currently reports more verified consumer games (52/66). Shiraz runs DEX directly on standalone ART under GPL-3.0-only across Linux, Apple Silicon macOS, and Windows.

### Does Shiraz include Google Play Services or full sandboxing?
No. Shiraz does not ship the Google Play Store or Google Play Services. For isolation, OS-level sandboxing is enabled by default across \`shiraz run\`, \`shiraz install-desktop\`, \`shiraz daemon\`, and Linux AppImages (\`--no-sandbox\` opt-out; \`--no-network\` / \`--no-internet\` to deny sockets) via Linux Landlock LSM + \`PR_SET_NO_NEW_PRIVS\` + seccomp-BPF, macOS Seatbelt + App Sandbox entitlements, and Windows Job Objects + Restricted Tokens, alongside Flatpak bubblewrap bundles. Because host process confinement is not Android's full per-UID/SELinux sandbox, still only run APKs you trust.
`;

writeFileSync(`${root}landing/static/llms.txt`, llmsTxt);
writeFileSync(`${root}landing/static/llms-full.txt`, llmsFull);
console.log(`Generated llms.txt (${llmsTxt.length} chars) and llms-full.txt (${llmsFull.length} chars) for v${version}`);
