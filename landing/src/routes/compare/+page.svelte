<script>
  // SPDX-License-Identifier: GPL-3.0-only
  // Shiraz beside the six projects closest to it. Every competitor value comes
  // from that project's own README or wiki, read on 21 November 2026; none was
  // built or run. Shiraz values come from README.md, docs/cross_platform.md,
  // docs/macos_final_goal.md, docs/windows_final_goal.md and docs/tested.md.
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import SiteNav from '#lib/SiteNav.svelte';
  import { ROBOTS_INDEX, compareFaqs, compareJsonLd, serializeJsonLd } from '#lib/seo.js';
  import '../../styles.css';

  const home = resolve('/').replace(/\/$/, '') + '/';

  const families = [
    {
      id: 'app',
      name: 'Translation layers',
      line: 'Run the app on a rebuilt framework',
      footprint: 'App only · no OS image · no boot',
      text: 'Only the app runs. Its code executes on ART or a JVM, and a reimplemented framework maps Android™ APIs to the host. The lightest approach; every missing API has to be built.',
      who: 'Shiraz, ATL, ACL, a4l, Mac.apk'
    },
    {
      id: 'user',
      name: 'Rootless userspace',
      line: 'Android’s own pieces as host processes',
      footprint: 'Android userspace + emulated binder',
      text: 'Android’s libraries and services run as ordinary host processes, with binder emulated in userspace. More of real Android, without root or a kernel module.',
      who: 'apklayer, Innkeeper'
    },
    {
      id: 'os',
      name: 'Containers and VMs',
      line: 'Boot Android beside the desktop',
      footprint: 'Full OS image · container/VM boot',
      text: 'A full Android™ system runs in a container or virtual machine. High compatibility because it is real Android, but it has to boot and usually needs binder and root.',
      who: 'Waydroid, Lepton, Droidloom, emulators'
    }
  ];

  // rating: 'good' | 'warn' | 'bad' | undefined (no judgement)
  const rows = [
    ['host', 'Host OSes'],
    ['code', 'App code'],
    ['framework', 'Framework'],
    ['native', 'Native code'],
    ['arm', 'ARM on x86-64'],
    ['graphics', 'Graphics'],
    ['window', 'Window'],
    ['binder', 'Binder'],
    ['webview', 'WebView'],
    ['isolation', 'App isolation'],
    ['dist', 'Distribution'],
    ['license', 'License'],
    ['repo', 'Repository'],
    ['apps', 'Reported apps']
  ];

  // Nine comparable architectural capabilities for the visual heatmap and hero pillars.
  const matrixDims = [
    ['host', 'Multi-OS host'],
    ['binder', 'No binder / root'],
    ['window', 'Desktop freedom'],
    ['graphics', 'GPU & graphics'],
    ['webview', 'WebView'],
    ['isolation', 'App sandboxing'],
    ['arm', 'ARM translation'],
    ['dist', 'Per-app packaging'],
    ['apps', 'Verified real apps']
  ];

  const projects = [
    {
      id: 'shiraz',
      name: 'Shiraz',
      family: 'app',
      url: 'https://github.com/spectre-oss/shiraz',
      tagline: 'This project',
      stack: [
        { stage: 'App code', title: 'Standalone ART', detail: 'Warm standby pool on Linux & macOS (2.67×); MAP_JIT/remap' },
        { stage: 'Framework', title: '4,445 API 35 classes', detail: '49/49 packages in Java + JNI (bounded behavior)' },
        { stage: 'Native ABI', title: '15 NDK libs · 887 syms', detail: 'Host ELF, Digitalis, macOS guest loader, Win32 NativeBridge v3' },
        { stage: 'Graphics', title: 'GLES · Metal · Vulkan', detail: 'Zero-copy GBM/IOSurface, live MoltenVK 1.4.2 Metal Vulkan' },
        { stage: 'Window', title: 'SDL2 on 3 OSes', detail: 'Linux, macOS Cocoa & Windows Win32 — no GTK' }
      ],
      scores: { host: 'good', binder: 'good', window: 'good', graphics: 'warn', webview: 'warn', isolation: 'good', arm: 'warn', dist: 'good', apps: 'warn' },
      host: [
        'Three native desktop OSes: Linux x86-64 & ARM64 (primary), Apple Silicon macOS (48/91 goal items verified, 21 partial), and Windows x64 & ARM64 (32/37 CI-verified, 5 partial, 0 open).',
        'good'
      ],
      code: ['Standalone ART with WarmArtPool standby daemon wired on both Linux and macOS (2.67× faster launch on 2048), AOT/page-cache pre-warming on Linux, and verified Darwin MAP_JIT, mach_vm_remap & ARM64 fault-handler primitives (-Xint until libart-compiler.so lands).'],
      framework: ['All 4,445 Android™ API 35 class surfaces across 49 packages in Java + C++ JNI (bounded subsystem behavior), plus attributed AOSP code.'],
      native: [
        'All 15 tracked API 35 NDK libraries (887/887 audited symbols) built for Linux and macOS ARM64; ALSA/PipeWire AAudio on Linux; opt-in macOS guest ELF/Bionic loader + pinned MoltenVK v1.4.2 guest libvulkan.so boots OsmAnd+, F-Droid v2.0.2, Math & Telegram; CoreAudio/VideoToolbox/CoreLocation/Metal on macOS; CI-verified Win32 WASAPI (AAudio & OpenSL ES), Media Foundation (decode + IMFSourceReader capture), DirectWrite, DXGI & Win32 ART NativeBridge v3 / guest ELF SysV↔Win64 thunks on Windows.'
      ],
      arm: ['Digitalis ARM64-on-x86_64 bridge works in CLI runs, CLI --digitalis AppImage/Flatpak builds, and browser WASM assembly (when the base includes Digitalis); native ARM64 on Apple Silicon and Linux ARM64. No 32-bit ARM; published browser bases do not bundle Digitalis yet.', 'warn'],
      graphics: [
        'EGL/GLES + Skia Ganesh + bundled libvulkan.so/.dll (VK_KHR_android_surface swapchain, AHB, and real-ICD PackageManager probe); zero-copy GBM/DMA-BUF swapchain & vsync choreographer on Linux; zero-copy IOSurface swapchain, MTLSharedEvent fences, 120 Hz CVDisplayLink vsync & pinned MoltenVK v1.4.2 live Metal Vulkan 1.4.357 on Apple M2 Pro (host/guest thunks + Java PackageManager level=1) on macOS; DXGI shared buffers, DwmFlush & Per-Monitor V2 DPI/ANGLE-D3D11 wiring on Windows. End-to-end Vulkan games remain unverified.',
        'warn'
      ],
      window: ['SDL2 across Linux (Wayland & X11), macOS Cocoa (Retina, Cmd shortcuts, live resize) and Windows Win32 (Per-Monitor V2 DPI, IME, clipboard, Precision Touchpad). Any desktop, no GTK.', 'good'],
      binder: ['None needed — no kernel binder module or root container.', 'good'],
      webview: ['Embedded pure-Java HTML5 DOM, CSS selectors, interactive contentEditable editing, Selection/Range, execCommand, MutationObserver, timers, DOM storage, step-budgeted JS evaluator & @JavascriptInterface bridge; not a full Blink/V8 layout engine yet.', 'warn'],
      isolation: [
        'On by default across CLI, desktop launchers, macOS Launcher, daemon, and Linux AppImages (--no-sandbox opt-out, --no-network/--no-internet): Linux Landlock LSM + PR_SET_NO_NEW_PRIVS + seccomp-BPF, macOS Seatbelt SBPL + App Sandbox entitlements, Windows Job Object + Restricted Token, plus Flatpak bubblewrap bundles.',
        'good'
      ],
      dist: ['Browser & CLI builders make per-app Linux AppImages and sandboxed Flatpak (.flatpak) bundles (with Digitalis ARM64 support & bundled libvulkan.so); signed macOS .app bundler + browser .app sidecar zip packager; Windows portable/MSIX bundler; shiraz install-desktop on all 3 OSes; WebRTC browser streaming.', 'good'],
      license: ['GPL-3.0-only.'],
      repo: [`spectre-oss/shiraz, version ${__SHIRAZ_VERSION__}.`],
      apps: [
        '18 recorded apps across 8 engines: live Apple Silicon walkthroughs of OsmAnd+ (vector maps, offline search, turn-by-turn routing/navigation, 468 MB HTTPS downloads, resize, zoom persistence), F-Droid v2.0.2 (Material 3 Compose + two-pane landscape), Math (ARM64 native), Editor, Crossword, SolitaireCG (with restart persistence), 2048 (402 ms warm standby), Telegram 13.2.1 auth, plus Obtainium, Joplin, Unciv, Mindustry, SuperTux, Tiny Crate, Ember Quest & more, with open limits documented.',
        'warn'
      ]
    },
    {
      id: 'atl',
      name: 'ATL',
      family: 'app',
      url: 'https://gitlab.com/android_translation_layer/android_translation_layer',
      tagline: 'Android™ Translation Layer',
      stack: [
        { stage: 'App code', title: 'Standalone ART', detail: 'AOSP ART on Linux hosts' },
        { stage: 'Framework', title: 'GTK-mapped views', detail: 'Reimplemented Android views as GTK widgets' },
        { stage: 'Native ABI', title: 'bionic_translation', detail: 'Dynamic linker bridge (also reused by Shiraz)' },
        { stage: 'Graphics', title: 'EGL/GLES via GTK', detail: 'Host GLES inside GTK4 surfaces' },
        { stage: 'Window', title: 'GTK4 windows', detail: 'Linux desktop & phone distros' }
      ],
      scores: { host: 'warn', binder: 'good', window: 'warn', graphics: 'warn', webview: 'none', isolation: 'bad', arm: 'none', dist: 'good', apps: 'warn' },
      host: ['Linux, including aarch64 laptops. Apple Silicon needs interpreter workarounds.', 'warn'],
      code: ['Standalone ART.'],
      framework: ['Reimplemented; Android views become GTK widgets.'],
      native: ['bionic_translation linker.'],
      arm: ['Not described in its README.'],
      graphics: ['EGL/GLES through GTK.', 'warn'],
      window: ['GTK4 window.', 'warn'],
      binder: ['None needed.', 'good'],
      webview: ['Not described.'],
      isolation: ['None yet; bubblewrap is on the roadmap.', 'bad'],
      dist: ['Alpine, postmarketOS, Nix, AUR and Flatpak; --install adds a desktop entry.', 'good'],
      license: ['GPL-3.0-or-later.'],
      repo: ['GitLab, since January 2021, 344 stars.'],
      apps: ['Angry Birds, Worms 2, Gravity Defied, and Beat Saber (Quest) on an aarch64 laptop.', 'warn']
    },
    {
      id: 'acl',
      name: 'ACL',
      family: 'app',
      url: 'https://github.com/reveny/Android-Compatibility-Layer',
      tagline: 'Android Compatibility Layer',
      stack: [
        { stage: 'App code', title: 'AOSP ART', detail: 'x86-64 Linux host runtime' },
        { stage: 'Framework', title: 'Reimplemented core', detail: 'FreeType, HarfBuzz, SQLite, PipeWire' },
        { stage: 'Native ABI', title: 'LLVM AOT / JIT', detail: 'Whole ARM64 libs recompiled to x86-64' },
        { stage: 'Graphics', title: 'Vulkan & GLES', detail: 'Direct host GPU acceleration' },
        { stage: 'Window', title: 'Wayland only', detail: 'No rotation listed yet' }
      ],
      scores: { host: 'warn', binder: 'good', window: 'warn', graphics: 'good', webview: 'bad', isolation: 'good', arm: 'good', dist: 'warn', apps: 'bad' },
      host: ['x86-64 Linux with Wayland.', 'warn'],
      code: ['ART built from AOSP.'],
      framework: ['Reimplemented, with FreeType, HarfBuzz, SQLite and PipeWire.'],
      native: ['arm64-v8a and x86_64 APKs. No 32-bit ARM.'],
      arm: ['Whole ARM64 libraries recompiled ahead of time with LLVM, or JIT.', 'good'],
      graphics: ['Vulkan and GLES on the host GPU.', 'good'],
      window: ['Wayland only. No rotation yet.', 'warn'],
      binder: ['None.', 'good'],
      webview: ['Listed as missing.', 'bad'],
      isolation: ['On by default: seccomp, landlock, private PID and network namespaces.', 'good'],
      dist: ['Release tarball with runtime and framework.', 'warn'],
      license: ['MIT.'],
      repo: ['Since October 2026, 68 stars.'],
      apps: ['Very early; app support is still limited.', 'bad']
    },
    {
      id: 'a4l',
      name: 'a4l',
      family: 'app',
      url: 'https://github.com/android-4-linux/a4l',
      tagline: 'android-4-linux',
      stack: [
        { stage: 'App code', title: 'Real ART', detail: 'Split-process ART on ARM64 Java apps' },
        { stage: 'Framework', title: 'AOSP subset + C++23', detail: 'Vendored Apache-2.0 Java, clean-room C++' },
        { stage: 'Native ABI', title: 'Bionic in glibc', detail: 'x86-64 single process; ARM64 native pending' },
        { stage: 'Graphics', title: 'Skia Vulkan + dma-buf', detail: 'Zero-copy Vulkan + Mesa GL + Chromium WebView' },
        { stage: 'Window', title: 'Wayland / ivi-shell', detail: 'Desktop & automotive kiosk shells' }
      ],
      scores: { host: 'warn', binder: 'good', window: 'warn', graphics: 'good', webview: 'good', isolation: 'bad', arm: 'none', dist: 'bad', apps: 'warn' },
      host: ['x86-64 Linux. ARM64 runs Java-only apps through split-process ART; ivi-shell for cars.', 'warn'],
      code: ['Real ART.'],
      framework: ['Vendored Apache-2.0 AOSP Java subset over a clean-room C++23 core.'],
      native: ['App’s bionic libraries in one glibc process. Not yet on ARM64.'],
      arm: ['Not described.'],
      graphics: ['Skia on Vulkan into dma-buf, zero-copy; native GL through Mesa.', 'good'],
      window: ['Wayland xdg-toplevel or ivi-shell.', 'warn'],
      binder: ['None.', 'good'],
      webview: ['Chromium WebView through its Vulkan draw functor.', 'good'],
      isolation: ['Native code is not confined; signatures checked on request.', 'bad'],
      dist: ['Meson source build.', 'bad'],
      license: ['Apache-2.0, with an enforced clean-room policy.'],
      repo: ['Since November 2026, 2 stars.'],
      apps: ['15 of 18 F-Droid apps reach layout headless. Compose, Flutter, WebView and libGDX seen on screen.', 'warn']
    },
    {
      id: 'macapk',
      name: 'Mac.apk',
      family: 'app',
      url: 'https://github.com/kksimp/Mac.apk',
      tagline: 'Apple Silicon Macs',
      stack: [
        { stage: 'App code', title: 'dex2jvm + OpenJDK', detail: 'DEX converted to JVM bytecode on first launch' },
        { stage: 'Framework', title: 'Own AOSP + Play subset', detail: 'Custom framework & Play Services subset' },
        { stage: 'Native ABI', title: 'ARM64 + ARM32 AOT', detail: 'Own bionic on macOS libs; ARM32 recompiled' },
        { stage: 'Graphics', title: 'ANGLE + MoltenVK', detail: 'GLES & Vulkan both on Apple Metal' },
        { stage: 'Window', title: 'Native Cocoa .app', detail: 'Dock icon, back button & signed DMG' }
      ],
      scores: { host: 'warn', binder: 'good', window: 'good', graphics: 'good', webview: 'none', isolation: 'bad', arm: 'good', dist: 'good', apps: 'good' },
      host: ['macOS 26.4 or later, Apple Silicon only.', 'warn'],
      code: ['dex2jvm converts DEX to Java bytecode on first launch; runs on bundled OpenJDK.'],
      framework: ['Own implementation modelled on AOSP, plus its own Play Services subset.'],
      native: ['ARM64 runs as is; ARM32 recompiled to ARM64; own bionic on macOS libraries.', 'good'],
      arm: ['Not needed on Apple Silicon; ARM32 recompiled.', 'good'],
      graphics: ['GLES via ANGLE, Vulkan via MoltenVK, both on Metal.', 'good'],
      window: ['Native macOS windows with a Dock icon and back button.', 'good'],
      binder: ['None described.', 'good'],
      webview: ['Not described; Instagram and Reddit run partly.'],
      isolation: ['None; apps run with the user’s permissions.', 'bad'],
      dist: ['Signed, notarized disk image; each app becomes its own .app.', 'good'],
      license: ['AGPL-3.0, with a commercial license sold.'],
      repo: ['Since May 2026, about 4,400 stars.'],
      apps: ['66 tested: 52 work, 10 partly, 4 not yet. Includes Minecraft and Subway Surfers.', 'good']
    },
    {
      id: 'apklayer',
      name: 'apklayer',
      family: 'user',
      url: 'https://github.com/erfanoabdi/apklayer',
      tagline: 'Real Android userspace',
      stack: [
        { stage: 'App code', title: 'dex2jvm on JVM', detail: 'DEX translated onto host JVM' },
        { stage: 'Framework', title: 'AOSP android-all', detail: 'Unmodified AOSP + Robolectric shadows' },
        { stage: 'Native ABI', title: 'LineageOS 24 libs', detail: 'bionic_translation + userspace binder' },
        { stage: 'Graphics', title: 'SurfaceFlinger + HWUI', detail: 'Host processes; Vulkan in progress' },
        { stage: 'Window', title: 'Wayland per app', detail: 'Mobile & desktop modes + Slint manager' }
      ],
      scores: { host: 'warn', binder: 'warn', window: 'warn', graphics: 'warn', webview: 'good', isolation: 'none', arm: 'good', dist: 'warn', apps: 'good' },
      host: ['ARM64 Linux. x86-64 staged but untested.', 'warn'],
      code: ['dex2jvm to a JVM.'],
      framework: ['Unmodified AOSP android-all with Robolectric shadows.'],
      native: ['LineageOS 24 libraries (SurfaceFlinger, HWUI, media) via bionic_translation.'],
      arm: ['Optimizing, lite and interpret modes on x86-64.', 'good'],
      graphics: ['SurfaceFlinger and HWUI as host processes; Vulkan in progress.', 'warn'],
      window: ['One Wayland window per app; mobile and desktop modes.', 'warn'],
      binder: ['Its own userspace binder; kernel binderfs optional.', 'warn'],
      webview: ['Listed as working.', 'good'],
      isolation: ['Not described.'],
      dist: ['Meson build plus a prebuilts branch; Slint manager app.', 'warn'],
      license: ['AGPL-3.0-or-later, with a commercial license sold.'],
      repo: ['Since October 2026, 27 stars.'],
      apps: ['Over 40 apps, including OsmAnd, Organic Maps, Thunderbird, VLC and Firefox.', 'good']
    },
    {
      id: 'innkeeper',
      name: 'Innkeeper',
      family: 'user',
      url: 'https://github.com/sargunv/innkeeper',
      tagline: 'Android 17 in Flatpak',
      stack: [
        { stage: 'App code', title: 'Android 17 ART', detail: 'Runs inside unmodified AOSP userspace' },
        { stage: 'Framework', title: 'Full AOSP (1.2 GB)', detail: 'Google public CI system image' },
        { stage: 'Native ABI', title: 'Android’s own', detail: 'Emulated userspace binder; no ARM yet' },
        { stage: 'Graphics', title: 'Own vendor HALs', detail: 'Bridged onto desktop GPU' },
        { stage: 'Window', title: 'Wayland + portals', detail: 'Flatpak sandbox, shared app uid' }
      ],
      scores: { host: 'warn', binder: 'warn', window: 'good', graphics: 'warn', webview: 'good', isolation: 'warn', arm: 'bad', dist: 'warn', apps: 'warn' },
      host: ['x86-64 Linux on Wayland, kernel 6.5+, Flatpak.', 'warn'],
      code: ['ART inside an Android 17 userspace.'],
      framework: ['Unmodified AOSP from Google’s public CI, about 1.2 GB.'],
      native: ['Android’s own.'],
      arm: ['Not yet; planned.', 'bad'],
      graphics: ['Its own vendor HALs on the desktop GPU.', 'warn'],
      window: ['A Wayland window per app, with desktop portals.', 'good'],
      binder: ['Emulated in userspace.', 'warn'],
      webview: ['Real Android WebView.', 'good'],
      isolation: ['Flatpak sandbox; all apps share one uid.', 'warn'],
      dist: ['Flatpak snapshot.', 'warn'],
      license: ['AGPL-3.0-or-later.'],
      repo: ['Since October 2026, 4 stars.'],
      apps: ['Pre-alpha. Compose, Flutter, React Native and Godot apps; no Play Integrity.', 'warn']
    }
  ];

  const shiraz = projects[0];
  const peers = projects.slice(1);
  let picked = $state('atl');
  const rival = $derived(peers.find(p => p.id === picked));

  const countScores = p => {
    const vals = matrixDims.map(([k]) => p.scores[k]);
    return {
      good: vals.filter(v => v === 'good').length,
      warn: vals.filter(v => v === 'warn').length,
      bad: vals.filter(v => v === 'bad').length,
      none: vals.filter(v => v === 'none').length
    };
  };
  const shirazSummary = countScores(shiraz);
  const rivalSummary = $derived(countScores(rival));

  const notes = {
    atl: {
      summary: 'The oldest and best-known translation layer, and the project Shiraz learned from first. Shiraz ships ATL’s bionic translation linker under its original notice.',
      ahead: ['Five years of work and an established community.', 'Packaged across Alpine, postmarketOS, Nix, the AUR and Flatpak.'],
      differs: [
        'No GTK in Shiraz, so one SDL2 runtime drives Linux, macOS Cocoa, Windows Win32, AppImages and WebRTC streaming.',
        'Shiraz ships per-app AppImages & Flatpaks, warm ART standby pre-warming on Linux and macOS, default-on 3-OS sandboxing and native Apple Silicon + Windows ports.'
      ],
      learn: ['Its widget coverage and stub-then-implement workflow.', 'Its reach on Linux phone distributions.']
    },
    acl: {
      summary: 'Runs DEX on AOSP host ART over a reimplemented framework, with games in mind. It recompiles whole native libraries to x86-64 ahead of time with LLVM.',
      ahead: ['Private PID and network namespaces by default alongside Landlock and seccomp.', 'Ahead-of-time ARM64 recompilation for native game speed.', 'End-to-end Vulkan GPU rendering.'],
      differs: [
        'ACL lists no rotation, WebView or MediaCodec. Shiraz tests portrait and landscape resizing in real apps and ships an embedded DOM/JS WebView with interactive contentEditable editing.',
        'ACL targets Wayland-only x86-64 Linux; Shiraz uses SDL2 across Linux, macOS and Windows with default-on 3-OS sandboxing and builds per-app AppImages & Flatpaks.'
      ],
      learn: ['Private PID and network namespaces on Linux to go even further than Landlock.', 'LLVM recompilation as a faster path for ARM64 game code.']
    },
    a4l: {
      summary: 'A clean-room C++23 layer running real ART with a vendored Apache-2.0 AOSP subset in one glibc process. It targets cars and kiosks as well as desktops.',
      ahead: ['Working Chromium WebView; Shiraz’s pure-Java contentEditable/DOM/JS WebView is not yet a full Blink/V8 layout engine.', 'Zero-copy Vulkan and dma-buf presentation.', 'Provenance rules enforced in CI.'],
      differs: ['Its evidence is mostly headless layout; Shiraz records interactive walkthroughs across 8 engines and 3 OSes with default-on sandboxing.', 'No ARM translation and Meson source builds only.'],
      learn: ['Chromium WebView integration for complex web editors.', 'Dated, per-app status tracking.']
    },
    macapk: {
      summary: 'The most polished consumer product here. It converts DEX to Java bytecode, runs it on OpenJDK, and turns each APK into a Mac app.',
      ahead: ['52 of 66 tested apps fully work, including large 3D games.', 'Notarized DMG polish and a custom Play Services subset.', 'MoltenVK Vulkan titles and an ARM32-to-ARM64 recompiler.'],
      differs: [
        'Shiraz runs DEX directly on standalone ART (not dex2jvm) with default-on Seatbelt sandboxing and shares one GPL-3.0-only codebase across Linux, macOS (zero-copy Metal/IOSurface + signed .app bundler) and Windows.',
        'Shiraz’s macOS port already runs live OsmAnd+ vector maps, turn-by-turn routing/navigation, 468 MB HTTPS downloads and Telegram auth, with open M5/JIT work explicitly documented.'
      ],
      learn: ['A public compatibility list as the scoreboard.', 'Polished notarized installer and smooth consumer onboarding.']
    },
    apklayer: {
      summary: 'Loads Android’s own native libraries from LineageOS 24 and runs SurfaceFlinger and other services as host processes over its own userspace binder, with the unmodified AOSP framework on a JVM.',
      ahead: ['Broad app list for its age (40+ apps, OsmAnd included).', 'Real Android graphics, WebView, media and providers out of the box.'],
      differs: ['It carries Android’s userspace libraries and userspace binder; Shiraz carries only the app and ships across Linux, macOS and Windows.', 'ARM64 Linux hosts first; x86-64 is untested.'],
      learn: ['A reference for how real Android behaves when Shiraz diverges.']
    },
    innkeeper: {
      summary: 'Runs Google’s unmodified Android 17 userspace inside Flatpak with binder emulated, and boots in about three seconds warm. It can also run OpenHarmony.',
      ahead: ['Real Android 17 userspace, so most framework and WebView behavior works directly.', 'Flatpak sandbox and desktop portals by default.'],
      differs: [
        'A 1.2 GB Android image and a boot; Shiraz has neither, using a warm ART standby daemon for fast app-only launch.',
        'No ARM translation yet, and Linux Wayland Flatpak only — Shiraz spans Linux, macOS and Windows.'
      ],
      learn: ['Desktop integration through portals, notifications and media controls.']
    }
  };
  const note = $derived(notes[picked]);

  const reasons = [
    ['Apps, not a second OS', 'Containers and VMs boot all of Android beside the desktop. Shiraz runs only the app, with a warm ART standby pool so it starts like a normal program.'],
    ['No kernel or root', 'Waydroid needs the binder driver and a root container. Shiraz needs no binder, no kernel module and no root.'],
    ['No toolkit lock-in', 'No GTK, no GNOME or KDE. SDL2 and EGL/GLES let one runtime drive Linux, macOS Cocoa, Windows Win32, AppImages and WebRTC streams.'],
    ['An APK becomes a native app', 'Browser Builder makes a Linux AppImage locally; CLI bundlers emit signed macOS .app and Windows portable/MSIX packages without altering the APK.'],
    ['Proof in real apps', 'OsmAnd+ and 13 other recorded apps are tested interactively across 8 engines: maps, search, resizing, persistence — with unfinished limits left visible.'],
    ['Free, toward Linux phones', 'GPL-3.0-only, with Linux phones as the long-term goal, so you can use the apps you need on hardware you control.']
  ];

  const leads = [
    'The only open project here natively spanning Linux (x86-64 & ARM64), Apple Silicon macOS (48/91 goal items verified, 21 partial) and Windows (32/37 CI-verified, 5 partial, 0 open) in one codebase.',
    'Default-on OS process sandboxing across CLI, desktop launchers, macOS Launcher, daemon, AppImages and Flatpaks (--no-sandbox opt-out) via Landlock/seccomp, Seatbelt, and Win32 Job Objects.',
    'Complete API 35 Java class surface (4,445/4,445 across 49 packages) and all 15 tracked NDK C/C++ libraries (887/887 audited symbols).',
    'The only one that packages an APK into a standalone Linux AppImage, sandboxed Flatpak (.flatpak), or unmodified signed macOS .app sidecar zip directly in the browser, alongside CLI --digitalis, Windows MSIX and cross-OS install-desktop.',
    'The only one that streams a running Android app to any browser over WebRTC, with zero-copy GBM/DMA-BUF & IOSurface graphics, live MoltenVK v1.4.2 Metal Vulkan on Apple Silicon, and a warm ART standby pool on both Linux and macOS (2.67× faster launch).',
    'No GTK, no Wayland-only lock-in, no kernel binder, and interactive walkthrough evidence across 18 recorded apps and 8 engines (Android, Flutter, React Native, Unity, libGDX, Godot, SDL, Arc).'
  ];
  const trails = [
    'Live Metal Vulkan 1.4.357 (MoltenVK v1.4.2) passes host, guest ELF64, and Java PackageManager probes on Apple Silicon, but end-to-end Vulkan game titles are still unverified.',
    'WebView uses an embedded pure-Java DOM/JS/contentEditable engine rather than a full Blink/V8 browser engine (Joplin CodeMirror 6 retesting remains open).',
    'OsmAnd+, F-Droid v2.0.2, Math, Editor, Crossword, SolitaireCG and 2048 ran interactively on Apple Silicon, but building libart-compiler.so JIT, M5 hardened signing and Linux/Waydroid parity remain open.',
    'No 32-bit ARM support, published browser bases do not ship Digitalis or macOS signed bases yet, and live Win32 libart.dll execution is still in progress.',
    'Fewer fully verified end-to-end apps than Mac.apk (52/66) or apklayer (40+) report.'
  ];

  const ratingLabel = { good: 'Strength', warn: 'Partial', bad: 'Gap', none: 'Not described' };
  const shortScore = { good: 'Strong', warn: 'Partial', bad: 'Gap', none: '—' };

  function pickPeer(id) {
    if (id !== 'shiraz') picked = id;
  }
</script>

<svelte:head>
  <title>Shiraz vs ATL, ACL, a4l, Mac.apk, apklayer &amp; Innkeeper — Shiraz</title>
  <meta
    name="description"
    content="Compare Shiraz beside ATL, ACL, a4l, Mac.apk, apklayer and Innkeeper: Linux/macOS/Windows support, runtime architecture, graphics, sandboxing, AppImage/Flatpak packaging, and reported apps."
  />
  <meta name="robots" content={ROBOTS_INDEX} />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/compare/" />
  <meta property="og:title" content="Shiraz vs ATL, ACL, a4l, Mac.apk, apklayer &amp; Innkeeper" />
  <meta property="og:description" content="Seven open-source ways to run Android™ apps off Android, compared across nine capabilities and fourteen technical dimensions." />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/compare/" />
  <meta property="og:type" content="website" />
  <meta property="og:locale" content="en_US" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta property="og:image:type" content="image/png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="Seven open-source Android-on-desktop projects compared side by side with Shiraz." />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Shiraz vs ATL, ACL, a4l, Mac.apk, apklayer &amp; Innkeeper" />
  <meta name="twitter:description" content="Seven open-source Android-on-desktop layers compared side by side: architecture, graphics, sandboxing, packaging, and tested apps." />
  <meta name="twitter:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta name="twitter:image:alt" content="Seven open-source Android-on-desktop projects compared side by side with Shiraz." />
  {@html serializeJsonLd(compareJsonLd({ version: __SHIRAZ_VERSION__, updated: '2026-11-24', projects }))}
</svelte:head>

<a class="skip-link" href="#compare-main">Skip to the comparison</a>
<header id="top" class="compat-header wrap">
  <a class="brand" href={resolve('/')}>
    <img src={home + 'spectre.svg'} width="42" height="42" alt="" />
    <span>shiraz</span>
  </a>
  <SiteNav />
</header>

<main id="compare-main">
  <nav class="breadcrumbs wrap" aria-label="Breadcrumb">
    <ol>
      <li><a href={resolve('/')}>Home</a></li>
      <li aria-current="page">Compare</li>
    </ol>
  </nav>
  <section class="peers-hero wrap">
    <div class="hero-copy">
      <p class="eyebrow">Shiraz v{__SHIRAZ_VERSION__} and its peers · updated 24 November 2026</p>
      <h1>
        Run the app.
        <br />
        <span>Not the phone.</span>
      </h1>
      <p class="lede">
        Seven open projects want Android™ apps on your desktop. Here is how far Shiraz has come across Linux, macOS and Windows — and where each peer is ahead of us.
      </p>
      <ul class="hero-facts" aria-label="Shiraz current milestones">
        <li><strong>3 OSes</strong><span>Linux · macOS · Windows</span></li>
        <li><strong>4,445</strong><span>API 35 classes</span></li>
        <li><strong>15 / 887</strong><span>NDK libs &amp; symbols</span></li>
        <li><strong>18</strong><span>Recorded apps</span></li>
      </ul>
      <div class="hero-actions">
        <a class="button primary" href="#capability-map">See capability map</a>
        <a class="button secondary" href="#head-to-head">Compare head to head</a>
      </div>
    </div>

    <figure class="hero-glass-wrap">
      <div class="hero-glass" role="group" aria-label="Evaluated capability profile across nine dimensions for all seven projects">
        {#each projects as p, i (p.id)}
          {@const stats = countScores(p)}
          <button
            type="button"
            class="pillar-col"
            class:me={p.id === 'shiraz'}
            class:is-picked={p.id === picked}
            onclick={() => {
              if (p.id !== 'shiraz') {
                picked = p.id;
                document.getElementById('head-to-head')?.scrollIntoView({ behavior: 'smooth' });
              }
            }}
            aria-label={`${p.name}: ${stats.good} strengths, ${stats.warn} partial, ${stats.bad} gaps across 9 dimensions${p.id === 'shiraz' ? '' : '. Activate to compare head to head.'}`}
          >
            <span class="pillar-stack" style="--i:{i}" aria-hidden="true">
              {#each matrixDims as [dimKey]}
                <i class="pillar-seg seg-{p.scores[dimKey]}"></i>
              {/each}
            </span>
            <span class="pillar-score" aria-hidden="true">{stats.good}/9</span>
            <span class="pillar-name">{p.name}</span>
          </button>
        {/each}
      </div>
      <figcaption class="hero-glass-caption">
        <span><i class="cap-dot good" aria-hidden="true"></i>Strength</span>
        <span><i class="cap-dot warn" aria-hidden="true"></i>Partial</span>
        <span><i class="cap-dot bad" aria-hidden="true"></i>Gap</span>
        <span>9 evaluated dimensions · click a peer to duel</span>
      </figcaption>
    </figure>
  </section>

  <section class="block wrap" aria-labelledby="families-title">
    <header class="block-head">
      <p class="eyebrow">Three approaches</p>
      <h2 id="families-title">
        Three ways in.
        <br />
        <span>Very different costs.</span>
      </h2>
    </header>
    <div class="families">
      {#each families as f (f.id)}
        <article class="family {f.id}">
          <p class="family-line">{f.line}</p>
          <h3>{f.name}</h3>
          <p class="family-footprint">{f.footprint}</p>
          <p>{f.text}</p>
          <p class="family-who">{f.who}</p>
        </article>
      {/each}
    </div>
  </section>

  <section class="block wrap" id="capability-map" aria-labelledby="map-title">
    <header class="block-head">
      <p class="eyebrow">At a glance</p>
      <h2 id="map-title">
        Nine capabilities,
        <br />
        <span>seven projects in one view.</span>
      </h2>
      <p>Every tile encodes status by both shape and color. Select any peer column to inspect its full head-to-head breakdown below.</p>
    </header>

    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable heatmap) -->
    <div class="heatmap-scroll" role="region" aria-label="Capability comparison heatmap" tabindex="0">
      <table class="heatmap">
        <caption class="visually-hidden">Nine architectural capabilities rated across Shiraz and six peer projects</caption>
        <thead>
          <tr>
            <th scope="col"><span class="visually-hidden">Capability</span></th>
            {#each projects as p (p.id)}
              {@const stats = countScores(p)}
              <th scope="col" class:me={p.id === 'shiraz'} class:is-picked={p.id === picked}>
                {#if p.id === 'shiraz'}
                  <span class="hm-project-name">{p.name}</span>
                {:else}
                  <button
                    type="button"
                    class="hm-pick-btn"
                    aria-pressed={picked === p.id}
                    onclick={() => pickPeer(p.id)}
                  >
                    {p.name}
                  </button>
                {/if}
                <small class="hm-tally">{stats.good} strong · {stats.warn} partial</small>
              </th>
            {/each}
          </tr>
        </thead>
        <tbody>
          {#each matrixDims as [dimKey, dimLabel]}
            <tr>
              <th scope="row">{dimLabel}</th>
              {#each projects as p (p.id)}
                {@const score = p.scores[dimKey]}
                <td class:me={p.id === 'shiraz'} class:is-picked={p.id === picked}>
                  <span class="hm-tile {score}" title={`${p.name} — ${dimLabel}: ${ratingLabel[score]}`}>
                    {#if score === 'none'}
                      <span class="mark none" aria-hidden="true"></span>
                      <span class="visually-hidden">Not described</span>
                    {:else}
                      <span class="mark {score}" aria-hidden="true"></span>
                      <span>{shortScore[score]}</span>
                    {/if}
                  </span>
                </td>
              {/each}
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    <p class="legend">
      <span><span class="mark good" aria-hidden="true"></span>Strength</span>
      <span><span class="mark warn" aria-hidden="true"></span>Partial / opt-in</span>
      <span><span class="mark bad" aria-hidden="true"></span>Gap / missing</span>
      <span><span class="mark none" aria-hidden="true"></span>Not described in README</span>
    </p>
  </section>

  <section class="block wrap" aria-labelledby="why-title">
    <header class="block-head">
      <p class="eyebrow">Why we built Shiraz</p>
      <h2 id="why-title">
        An Android™ app should be
        <br />
        <span>just another program.</span>
      </h2>
    </header>
    <ol class="reasons">
      {#each reasons as [title, text], idx}
        <li>
          <span class="reason-num">0{idx + 1}</span>
          <h3>{title}</h3>
          <p>{text}</p>
        </li>
      {/each}
    </ol>
  </section>

  <section class="block wrap" aria-labelledby="layers-title">
    <header class="block-head">
      <p class="eyebrow">Layer by layer</p>
      <h2 id="layers-title">
        What sits between
        <br />
        <span>the APK and your screen.</span>
      </h2>
      <p>Follow each project’s five-stage runtime pipeline from bytecode to desktop window. The glowing edge marks the family.</p>
    </header>

    <div class="pipelines" aria-label="Runtime pipeline per project">
      {#each projects as p (p.id)}
        <article class="pipeline-row {p.family}" class:me={p.id === 'shiraz'}>
          <div class="pipeline-head">
            <h3><a href={p.url}>{p.name}</a></h3>
            <small>{p.tagline}</small>
          </div>
          <ol class="pipeline-stages">
            {#each p.stack as node, idx}
              <li class="pipeline-node">
                <span class="stage-label">0{idx + 1} · {node.stage}</span>
                <strong>{node.title}</strong>
                <span>{node.detail}</span>
              </li>
            {/each}
          </ol>
        </article>
      {/each}
    </div>
  </section>

  <section class="block wrap" id="head-to-head" aria-labelledby="h2h-title">
    <header class="block-head">
      <p class="eyebrow">Head to head</p>
      <h2 id="h2h-title">
        Shiraz against
        <br />
        <span>one project at a time.</span>
      </h2>
    </header>
    <div class="picker" role="group" aria-label="Choose a project to compare with Shiraz">
      {#each peers as p (p.id)}
        <button type="button" class={p.family} aria-pressed={picked === p.id} onclick={() => (picked = p.id)}>{p.name}</button>
      {/each}
    </div>

    <div class="duel-scorecard" aria-label={`Capability score summary: Shiraz versus ${rival.name}`}>
      <div class="score-side">
        <div class="score-head">
          <strong>Shiraz</strong>
          <span>{shirazSummary.good} strong · {shirazSummary.warn} partial · {shirazSummary.bad} gaps</span>
        </div>
        <div class="score-bar" aria-hidden="true">
          <i class="seg-good" style:width={(100 * shirazSummary.good) / 9 + '%'}></i>
          <i class="seg-warn" style:width={(100 * shirazSummary.warn) / 9 + '%'}></i>
          <i class="seg-bad" style:width={(100 * shirazSummary.bad) / 9 + '%'}></i>
          <i class="seg-none" style:width={(100 * shirazSummary.none) / 9 + '%'}></i>
        </div>
      </div>
      <div class="score-side">
        <div class="score-head">
          <strong>{rival.name}</strong>
          <span>{rivalSummary.good} strong · {rivalSummary.warn} partial · {rivalSummary.bad} gaps</span>
        </div>
        <div class="score-bar" aria-hidden="true">
          <i class="seg-good" style:width={(100 * rivalSummary.good) / 9 + '%'}></i>
          <i class="seg-warn" style:width={(100 * rivalSummary.warn) / 9 + '%'}></i>
          <i class="seg-bad" style:width={(100 * rivalSummary.bad) / 9 + '%'}></i>
          <i class="seg-none" style:width={(100 * rivalSummary.none) / 9 + '%'}></i>
        </div>
      </div>
    </div>

    <div class="duel">
      <div class="duel-head">
        <span></span>
        <p class="me-name">Shiraz<small>{shiraz.tagline}</small></p>
        <p>
          <a href={rival.url}>
            {rival.name}
            <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg>
          </a>
          <small>{rival.tagline}</small>
        </p>
      </div>
      {#each rows as [key, label]}
        <div class="duel-row">
          <p class="duel-label">{label}</p>
          {#each [shiraz, rival] as p}
            <div class="duel-cell">
              {#if p[key][1]}
                <span class="rating-pill {p[key][1]}"><span class="mark {p[key][1]}" aria-hidden="true"></span>{ratingLabel[p[key][1]]}</span>
              {/if}
              <p>{p[key][0]}</p>
            </div>
          {/each}
        </div>
      {/each}
    </div>
    <div class="verdict">
      <p class="verdict-summary">{note.summary}</p>
      <div class="verdict-cols">
        <div><h3>Where {rival.name} is ahead</h3><ul>{#each note.ahead as item}<li>{item}</li>{/each}</ul></div>
        <div><h3>Where Shiraz differs</h3><ul>{#each note.differs as item}<li>{item}</li>{/each}</ul></div>
        <div><h3>What we take from it</h3><ul>{#each note.learn as item}<li>{item}</li>{/each}</ul></div>
      </div>
    </div>
    <p class="legend">
      <span><span class="mark good" aria-hidden="true"></span>Strength</span>
      <span><span class="mark warn" aria-hidden="true"></span>Partial</span>
      <span><span class="mark bad" aria-hidden="true"></span>Gap</span>
      <span>Unmarked: described, not judged.</span>
    </p>
  </section>

  <section class="block wrap" id="all" aria-labelledby="all-title">
    <header class="block-head">
      <p class="eyebrow">All seven</p>
      <h2 id="all-title">
        Every project,
        <br />
        <span>every dimension.</span>
      </h2>
      <p>Full reference table across all fourteen dimensions. Scroll sideways on smaller screens. Competitor values are their own published claims.</p>
    </header>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable region) -->
    <div class="all-scroll" role="region" aria-labelledby="all-title" tabindex="0">
      <table class="all">
        <caption class="visually-hidden">Seven projects compared across fourteen dimensions</caption>
        <thead>
          <tr>
            <th scope="col"><span class="visually-hidden">Dimension</span></th>
            {#each projects as p (p.id)}
              <th scope="col" class:me={p.id === 'shiraz'}>
                <a href={p.url}>{p.name}</a>
                <small>{families.find(f => f.id === p.family).name}</small>
              </th>
            {/each}
          </tr>
        </thead>
        <tbody>
          {#each rows as [key, label]}
            <tr>
              <th scope="row">{label}</th>
              {#each projects as p (p.id)}
                <td class:me={p.id === 'shiraz'}>
                  {#if p[key][1]}
                    <span class="mark {p[key][1]}" title={ratingLabel[p[key][1]]}><span class="visually-hidden">{ratingLabel[p[key][1]]}: </span></span>
                  {/if}
                  {p[key][0]}
                </td>
              {/each}
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    <p class="fineprint">“Not described” means the README does not say, which is not the same as unsupported.</p>
  </section>

  <section class="block wrap" aria-labelledby="stand-title">
    <header class="block-head">
      <p class="eyebrow">Where Shiraz stands</p>
      <h2 id="stand-title">Honest about both sides.</h2>
    </header>
    <div class="stand">
      <div class="lead"><h3>Where Shiraz leads</h3><ul>{#each leads as item}<li>{item}</li>{/each}</ul></div>
      <div class="trail"><h3>Where Shiraz trails</h3><ul>{#each trails as item}<li>{item}</li>{/each}</ul></div>
    </div>
    <div class="stand-cta">
      <a class="button primary" href={home + '#builder'}>Try the builder</a>
      <a class="button secondary" href={resolve('/compatibility/')}>See compatibility tracker</a>
    </div>
  </section>

  <section class="block wrap" aria-labelledby="compare-faq-title">
    <header class="block-head">
      <p class="eyebrow">Comparison questions</p>
      <h2 id="compare-faq-title">Quick answers.</h2>
    </header>
    <div class="faq-block faq-wide">
      {#each compareFaqs as item (item.q)}
        <details class="source-build faq-item">
          <summary>{item.q}<span class="disclosure-icon" aria-hidden="true"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14" /><path d="M12 5v14" /></svg></span></summary>
          <div class="build-content"><p>{item.a}</p></div>
        </details>
      {/each}
    </div>
  </section>

  <section class="block wrap sources" aria-labelledby="sources-title">
    <h2 id="sources-title">Sources and method</h2>
    <p>
      Shiraz facts come from its README, cross-platform host specifications and tested-apps reports at version {__SHIRAZ_VERSION__} (24 November 2026). Other projects’ facts come from their READMEs and wikis, read on 21 November 2026. We did not build or run them, so their compatibility figures are their own claims. Corrections are welcome as
      <a href="https://github.com/spectre-oss/shiraz/issues">issues</a>.
    </p>
    <ul>
      {#each projects as p}<li><a href={p.url}>{p.name}</a></li>{/each}
      <li><a href="https://github.com/kksimp/Mac.apk/wiki/How-Mac.apk-Works">How Mac.apk works</a></li>
    </ul>
  </section>
</main>
<Footer />

<style>
  .peers-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .95fr); gap: clamp(32px, 5vw, 72px); align-items: center; padding-block: clamp(32px, 6vw, 80px) clamp(56px, 8vw, 112px); }
  .peers-hero > * { min-width: 0; }
  .peers-hero h1 { font-size: clamp(2.5rem, 7.6vw, 7rem); line-height: .9; }
  .peers-hero h1 span, .block-head h2 span { color: var(--ink-soft); }
  .lede { max-width: 54ch; margin-top: 24px; font-size: clamp(17px, 1.5vw, 20px); color: var(--ink-soft); }
  .hero-facts { list-style: none; margin: 26px 0 0; padding: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; max-width: 560px; }
  .hero-facts li { padding: 12px 14px; border: 1px solid var(--line); border-radius: 14px; background: var(--wine-deep); }
  .hero-facts strong { display: block; font-family: var(--display); font-stretch: 78%; font-size: 24px; line-height: 1.05; color: var(--ink); font-variant-numeric: tabular-nums; }
  .hero-facts span { display: block; margin-top: 4px; font-size: 12px; color: var(--ink-faint); }
  .hero-actions, .stand-cta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 28px; }

  /* Seven stained-glass data pillars: each segment is one of the 9 evaluated capabilities. */
  .hero-glass-wrap { margin: 0; display: grid; gap: 14px; }
  .hero-glass { overflow: hidden; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 8px; height: clamp(320px, 34vw, 460px); padding: 54px 22px 16px; border-radius: 50% 50% 20px 20px / 18% 18% 20px 20px; background: radial-gradient(90% 60% at 50% 0%, #3d1429 0%, #12050b 68%); box-shadow: 0 0 0 1px #ffffff1f, 0 0 110px #4f6df02e; }
  .pillar-col { display: grid; grid-template-rows: 1fr auto auto; gap: 7px; min-width: 0; padding: 6px 4px; border: 1px solid transparent; border-radius: 12px; background: transparent; color: inherit; transition: border-color .2s ease, background-color .2s ease; }
  .pillar-col:hover { border-color: #ffffff28; background: #ffffff08; }
  .pillar-col.is-picked { border-color: var(--saffron); background: #f4b63f14; }
  .pillar-col.me { cursor: default; background: linear-gradient(180deg, #ff5d8f1c, #ff5d8f08); border-color: #ff5d8f55; }
  .pillar-stack { display: grid; grid-template-rows: repeat(9, 1fr); gap: 4px; height: 100%; align-self: stretch; padding: 4px; border-radius: 999px 999px 6px 6px; background: #ffffff08; }
  .pillar-seg { display: block; border-radius: 3px; }
  .pillar-seg.seg-good { background: var(--glass-emerald); box-shadow: 0 0 12px color-mix(in srgb, var(--glass-emerald) 45%, transparent); }
  .pillar-seg.seg-warn { background: var(--glass-saffron); opacity: .9; }
  .pillar-seg.seg-bad { background: var(--glass-rose); opacity: .78; }
  .pillar-seg.seg-none { background: #ffffff14; }
  .pillar-score { font-family: var(--mono); font-size: 11px; color: var(--ink-faint); text-align: center; font-variant-numeric: tabular-nums; }
  .pillar-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: center; font-family: var(--display); font-stretch: 72%; font-size: 13px; font-weight: 700; color: var(--ink-soft); }
  .me .pillar-name, .me .pillar-score { color: var(--ink); }
  .hero-glass-caption { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px 18px; font-size: 13px; color: var(--ink-faint); }
  .cap-dot { display: inline-block; width: 9px; height: 9px; margin-right: 6px; vertical-align: middle; }
  .cap-dot.good { border-radius: 50%; background: var(--glass-emerald); }
  .cap-dot.warn { border-radius: 2px; background: var(--glass-saffron); }
  .cap-dot.bad { border-radius: 1px; background: var(--glass-rose); transform: rotate(45deg); }

  .block { padding-block: clamp(56px, 8vw, 108px) 0; }
  .block-head { max-width: 820px; margin-bottom: clamp(28px, 4vw, 48px); }
  .block-head h2, .sources h2 { font-size: clamp(2.3rem, 5vw, 4.4rem); line-height: .95; }
  .block-head > p:last-child:not(.eyebrow) { margin-top: 18px; font-size: 18px; color: var(--ink-soft); }

  .families { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  .family { --c: var(--glass-teal); position: relative; display: grid; gap: 10px; align-content: start; padding: 28px; border: 1px solid var(--line); border-top: 3px solid var(--c); border-radius: 20px; background: var(--wine-deep); overflow: hidden; }
  .family-line { display: flex; align-items: center; gap: 10px; font-size: 14px; font-weight: 500; color: var(--ink-faint) !important; }
  .family-line::before { content: ""; flex: none; width: 10px; height: 10px; border-radius: 50%; background: var(--c); box-shadow: 0 0 16px var(--c); }
  .family.user { --c: var(--glass-saffron); }
  .family.os { --c: var(--glass-lapis); }
  .family h3 { font-size: 30px; }
  .family-footprint { font-family: var(--mono); font-size: 12px; color: var(--ink) !important; padding: 5px 10px; border-radius: 8px; background: var(--wine); width: fit-content; }
  .family p { color: var(--ink-soft); }
  .family-who { margin-top: 6px; padding-top: 14px; border-top: 1px solid var(--line); color: var(--ink) !important; font-weight: 500; }

  /* Capability heatmap matrix. */
  .heatmap-scroll { position: relative; overflow-x: auto; border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); }
  .heatmap { width: 100%; min-width: 860px; border-collapse: collapse; text-align: left; font-size: 14px; }
  .heatmap th, .heatmap td { padding: 14px 12px; border-bottom: 1px solid var(--line); text-align: center; vertical-align: middle; }
  .heatmap thead th { padding-block: 18px; background: var(--wine-raised); }
  .heatmap tbody th { position: sticky; left: 0; z-index: 1; text-align: left; width: 170px; padding-left: 20px; background: var(--wine-deep); font-weight: 600; color: var(--ink); }
  .heatmap .me { background: color-mix(in srgb, var(--glass-rose) 8%, var(--wine-raised)); }
  .heatmap thead th.me { border-top: 3px solid var(--glass-rose); }
  .heatmap .is-picked { background: color-mix(in srgb, var(--saffron) 7%, var(--wine-deep)); }
  .hm-project-name { display: block; font-family: var(--display); font-stretch: 78%; font-size: 21px; font-weight: 700; color: var(--ink); }
  .hm-pick-btn { display: inline-flex; align-items: center; justify-content: center; min-height: 36px; padding: 4px 12px; border: 1px solid transparent; border-radius: 999px; background: transparent; color: var(--ink); font-family: var(--display); font-stretch: 78%; font-size: 20px; font-weight: 700; transition: border-color .2s ease, background-color .2s ease, color .2s ease; }
  .hm-pick-btn:hover { border-color: var(--line-strong); }
  .hm-pick-btn[aria-pressed='true'] { background: var(--saffron); color: var(--on-saffron); }
  .hm-tally { display: block; margin-top: 4px; font-size: 12px; font-weight: 400; color: var(--ink-faint); font-variant-numeric: tabular-nums; }
  .hm-tile { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-width: 82px; padding: 6px 10px; border-radius: 10px; font-size: 13px; font-weight: 600; }
  .hm-tile .mark { margin-right: 0; }
  .hm-tile.good { background: #1f3a34; color: #bfe9d3; }
  .hm-tile.warn { background: #3f2f17; color: #fce0a2; }
  .hm-tile.bad { background: #4d1f2c; color: #ffc9d2; }
  .hm-tile.none { background: transparent; color: var(--ink-faint); font-weight: 400; }

  .reasons { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 40px; }
  .reasons li { display: grid; gap: 8px; align-content: start; padding-block: 24px; border-top: 1px solid var(--line); }
  .reason-num { font-family: var(--mono); font-size: 12px; color: var(--saffron); }
  .reasons h3 { font-size: 26px; }
  .reasons p { color: var(--ink-soft); }

  /* Visual 5-stage runtime pipelines (replaces dense text table). */
  .pipelines { display: grid; gap: 14px; }
  .pipeline-row { --c: var(--glass-teal); display: grid; grid-template-columns: 165px minmax(0, 1fr); gap: 16px; align-items: stretch; padding: 18px 20px; border: 1px solid var(--line); border-left: 4px solid var(--c); border-radius: 18px; background: var(--wine-deep); }
  .pipeline-row.user { --c: var(--glass-saffron); }
  .pipeline-row.me { --c: var(--glass-rose); background: var(--wine-raised); border-color: color-mix(in srgb, var(--glass-rose) 40%, var(--line)); border-left-color: var(--glass-rose); }
  .pipeline-head { display: flex; flex-direction: column; justify-content: center; gap: 4px; }
  .pipeline-head h3 { font-size: 24px; line-height: 1.1; }
  .pipeline-head a:hover { text-decoration: underline; text-underline-offset: 4px; }
  .pipeline-head small { font-size: 13px; color: var(--ink-faint); }
  .pipeline-stages { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
  .pipeline-node { display: grid; gap: 4px; align-content: start; padding: 12px 14px; border: 1px solid var(--line); border-radius: 12px; background: var(--wine); }
  .pipeline-row.me .pipeline-node { background: var(--wine-deep); border-color: color-mix(in srgb, var(--glass-rose) 28%, var(--line)); }
  .stage-label { font-family: var(--mono); font-size: 11px; color: var(--ink-faint); }
  .pipeline-node strong { font-size: 14px; line-height: 1.25; color: var(--ink); }
  .pipeline-node > span:last-child { font-size: 12.5px; line-height: 1.4; color: var(--ink-soft); }

  .picker { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
  .picker button { min-height: 44px; padding: 0 18px; border: 1px solid var(--line); border-radius: 999px; background: transparent; color: var(--ink-soft); font-weight: 600; transition: border-color .2s ease, background-color .2s ease, color .2s ease; }
  .picker button:hover { border-color: var(--line-strong); color: var(--ink); }
  .picker button[aria-pressed='true'] { background: var(--saffron); border-color: var(--saffron); color: var(--on-saffron); }

  /* Side-by-side scorecard above head-to-head duel. */
  .duel-scorecard { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
  .score-side { display: grid; gap: 10px; padding: 16px 20px; border: 1px solid var(--line); border-radius: 16px; background: var(--wine-deep); }
  .score-side:first-child { background: var(--wine-raised); border-color: color-mix(in srgb, var(--glass-rose) 35%, var(--line)); }
  .score-head { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px; }
  .score-head strong { font-family: var(--display); font-stretch: 78%; font-size: 22px; }
  .score-head span { font-size: 13px; color: var(--ink-soft); font-variant-numeric: tabular-nums; }
  .score-bar { display: flex; height: 10px; gap: 2px; border-radius: 999px; overflow: hidden; background: var(--line); }
  .score-bar i { display: block; height: 100%; }
  .score-bar .seg-good { background: var(--glass-emerald); }
  .score-bar .seg-warn { background: var(--glass-saffron); }
  .score-bar .seg-bad { background: var(--glass-rose); }
  .score-bar .seg-none { background: #4a2335; }

  .duel { border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); overflow: hidden; }
  .duel-head, .duel-row { display: grid; grid-template-columns: 170px minmax(0, 1fr) minmax(0, 1fr); }
  .duel-head p, .duel-row > * { padding: 16px 20px; min-width: 0; overflow-wrap: anywhere; }
  .duel-head { border-bottom: 1px solid var(--line-strong); }
  .duel-head p { font-family: var(--display); font-stretch: 78%; font-size: 28px; font-weight: 700; line-height: 1.1; }
  .duel-head small { display: block; margin-top: 4px; font-family: var(--body); font-stretch: normal; font-size: 13px; font-weight: 400; color: var(--ink-faint); }
  .duel-head a { display: inline-flex; align-items: center; gap: 8px; }
  .duel-head a:hover { text-decoration: underline; text-underline-offset: 5px; }
  .duel-head a .icon { width: 15px; height: 15px; }
  .me-name, .duel-row .duel-cell:nth-child(2) { background: var(--wine-raised); }
  .duel-row { border-bottom: 1px solid var(--line); }
  .duel-row:last-child { border-bottom: 0; }
  .duel-label { font-size: 13px; font-weight: 500; color: var(--ink-faint); }
  .duel-cell { display: grid; gap: 8px; align-content: start; font-size: 15px; line-height: 1.5; color: var(--ink-soft); }
  .rating-pill { display: inline-flex; align-items: center; gap: 7px; width: fit-content; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
  .rating-pill .mark { margin-right: 0; width: 8px; height: 8px; }
  .rating-pill.good { background: #1f3a34; color: #bfe9d3; }
  .rating-pill.warn { background: #3f2f17; color: #fce0a2; }
  .rating-pill.bad { background: #4d1f2c; color: #ffc9d2; }

  .mark { display: inline-block; width: 10px; height: 10px; margin-right: 9px; vertical-align: .05em; }
  .mark.good { border-radius: 50%; background: var(--glass-emerald); box-shadow: 0 0 10px var(--glass-emerald); }
  .mark.warn { border-radius: 2px; background: var(--glass-saffron); }
  .mark.bad { border-radius: 1px; background: var(--glass-rose); transform: rotate(45deg) scale(.85); }
  .mark.none { width: 10px; height: 2px; border-radius: 2px; background: var(--ink-faint); vertical-align: middle; }

  .verdict { margin-top: 16px; padding: 28px; border: 1px solid var(--line); border-radius: 20px; }
  .verdict-summary { max-width: 72ch; font-size: 18px; }
  .verdict-cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px; margin-top: 24px; }
  .verdict h3, .stand h3 { font-size: 22px; margin-bottom: 10px; }
  ul { margin: 0; padding-left: 18px; display: grid; gap: 6px; color: var(--ink-soft); }
  .legend { display: flex; flex-wrap: wrap; gap: 8px 22px; margin-top: 16px; font-size: 14px; color: var(--ink-faint); }

  .all-scroll { position: relative; overflow-x: auto; border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); }
  table { width: 100%; border-collapse: collapse; text-align: left; font-size: 15px; line-height: 1.5; }
  .all { min-width: 1500px; font-size: 14px; }
  .all th, .all td { padding: 18px; border-bottom: 1px solid var(--line); vertical-align: top; }
  .all thead th { font-family: var(--display); font-stretch: 78%; font-size: 22px; line-height: 1.1; }
  .all thead th a:hover { text-decoration: underline; text-underline-offset: 5px; }
  .all thead small { display: block; margin-top: 4px; font-family: var(--body); font-stretch: normal; font-size: 13px; font-weight: 500; color: var(--ink-faint); }
  .all tbody th { position: sticky; left: 0; z-index: 1; width: 130px; background: var(--wine-deep); font-size: 13px; font-weight: 500; color: var(--ink-faint); }
  .all td { color: var(--ink-soft); }
  .all .me { background: var(--wine-raised); color: var(--ink); }
  tbody tr:last-child > * { border-bottom: 0; }
  .fineprint { margin-top: 16px; font-size: 14px; color: var(--ink-faint); }

  .stand { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
  .stand > div { padding: 28px; border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); }
  .stand h3 { display: flex; align-items: center; gap: 12px; }
  .stand h3::before { content: ""; flex: none; width: 10px; height: 10px; border-radius: 50%; background: var(--glass-emerald); box-shadow: 0 0 12px var(--glass-emerald); }
  .stand .trail h3::before { border-radius: 1px; background: var(--glass-rose); box-shadow: none; transform: rotate(45deg) scale(.85); }

  .sources { padding-bottom: clamp(56px, 8vw, 112px); }
  .sources h2 { font-size: clamp(1.8rem, 3vw, 2.6rem); margin-bottom: 16px; }
  .sources p { max-width: 72ch; color: var(--ink-soft); }
  .sources a { text-decoration: underline; text-underline-offset: 4px; }
  .sources ul { display: flex; flex-wrap: wrap; gap: 6px 22px; margin-top: 16px; padding: 0; list-style: none; }

  @media (prefers-reduced-motion: no-preference) {
    .pillar-stack { animation: pillar-rise .85s var(--ease-out) both; animation-delay: calc(.12s + var(--i) * 65ms); transform-origin: bottom; }
  }
  @keyframes pillar-rise { from { transform: scaleY(.25); opacity: 0; } }

  @media (max-width: 1120px) {
    .pipeline-row { grid-template-columns: 1fr; }
    .pipeline-stages { grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr)); }
  }
  @media (max-width: 900px) {
    .peers-hero { grid-template-columns: 1fr; }
    .hero-glass { height: 260px; padding-top: 28px; }
    .hero-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .families, .reasons, .verdict-cols, .stand, .duel-scorecard { grid-template-columns: 1fr; }
    .duel-head, .duel-row { grid-template-columns: 1fr 1fr; }
    .duel-head > span, .duel-label { grid-column: 1 / -1; }
    .duel-head > span { display: none; }
    .duel-label { padding-bottom: 0 !important; }
  }
  @media (max-width: 520px) {
    .hero-glass { gap: 4px; padding: 38px 14px 12px; }
    .pillar-name { font-size: 11px; }
    .duel-head, .duel-row { grid-template-columns: 1fr; }
  }
</style>
