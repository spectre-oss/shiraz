<script>
  // SPDX-License-Identifier: GPL-3.0-only
  // Shiraz beside the six projects closest to it. Every competitor value comes
  // from that project's own README or wiki, read on 21 November 2026; none was
  // built or run. Shiraz values come from README.md and docs/tested.md.
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import SiteNav from '#lib/SiteNav.svelte';
  import '../../styles.css';

  const home = resolve('/').replace(/\/$/, '') + '/';

  const families = [
    { id: 'app', name: 'Translation layers', line: 'Run the app on a rebuilt framework', text: 'Only the app runs. Its code executes on ART or a JVM, and a reimplemented framework maps Android™ APIs to the host. The lightest approach; every missing API has to be built.', who: 'Shiraz, ATL, ACL, a4l, Mac.apk' },
    { id: 'user', name: 'Rootless userspace', line: 'Android’s own pieces as host processes', text: 'Android’s libraries and services run as ordinary host processes, with binder emulated in userspace. More of real Android, without root or a kernel module.', who: 'apklayer, Innkeeper' },
    { id: 'os', name: 'Containers and VMs', line: 'Boot Android beside the desktop', text: 'A full Android™ system runs in a container or virtual machine. High compatibility because it is real Android, but it has to boot and usually needs binder and root.', who: 'Waydroid, Lepton, Droidloom, emulators' }
  ];

  // rating: 'good' | 'warn' | 'bad' | undefined (no judgement)
  const rows = [
    ['host', 'Host'], ['code', 'App code'], ['framework', 'Framework'], ['native', 'Native code'], ['arm', 'ARM on x86-64'],
    ['graphics', 'Graphics'], ['window', 'Window'], ['binder', 'Binder'], ['webview', 'WebView'], ['isolation', 'App isolation'],
    ['dist', 'Distribution'], ['license', 'License'], ['repo', 'Repository'], ['apps', 'Reported apps']
  ];

  const projects = [
    {
      id: 'shiraz', name: 'Shiraz', family: 'app', url: 'https://github.com/spectre-oss/shiraz', tagline: 'This project',
      host: ['Linux x86-64 and ARM64. Experimental Apple Silicon macOS port.'],
      code: ['Standalone ART.'],
      framework: ['All 4,445 Android™ API 35 class surfaces in Java + C++ JNI (bounded behavior), plus attributed AOSP code.'],
      native: ['Host-ABI libraries load directly. On macOS, its own ELF loader and bionic adapters.'],
      arm: ['Optional Digitalis bridge for ARM64. Developer runs only, not yet in AppImages.', 'warn'],
      graphics: ['EGL/GLES with software composition. No Vulkan rendering yet.', 'warn'],
      window: ['SDL2. Any desktop, no GTK.', 'good'],
      binder: ['None needed.', 'good'],
      webview: ['No JavaScript or DOM engine yet.', 'bad'],
      isolation: ['No app sandbox. Optional --no-internet seccomp policy.', 'bad'],
      dist: ['Browser Builder makes a per-app AppImage; Web streams over WebRTC; CLI.', 'good'],
      license: ['GPL-3.0-only.'],
      repo: [`spectre-oss/shiraz, version ${__SHIRAZ_VERSION__}.`],
      apps: ['Obtainium, Joplin, Unciv, Mindustry, SuperTux, Tiny Crate and 2048, with limits recorded. OsmAnd reached maps and routes; full flows still unverified.']
    },
    {
      id: 'atl', name: 'ATL', family: 'app', url: 'https://gitlab.com/android_translation_layer/android_translation_layer', tagline: 'Android™ Translation Layer',
      host: ['Linux, including aarch64 laptops. Apple Silicon needs interpreter workarounds.'],
      code: ['Standalone ART.'],
      framework: ['Reimplemented; Android views become GTK widgets.'],
      native: ['bionic_translation linker.'],
      arm: ['Not described in its README.'],
      graphics: ['EGL/GLES through GTK.'],
      window: ['GTK4 window.'],
      binder: ['None needed.', 'good'],
      webview: ['Not described.'],
      isolation: ['None yet; bubblewrap is on the roadmap.', 'bad'],
      dist: ['Alpine, postmarketOS, Nix, AUR and Flatpak; --install adds a desktop entry.', 'good'],
      license: ['GPL-3.0-or-later.'],
      repo: ['GitLab, since January 2021, 344 stars.'],
      apps: ['Angry Birds, Worms 2, Gravity Defied, and Beat Saber (Quest) on an aarch64 laptop.']
    },
    {
      id: 'acl', name: 'ACL', family: 'app', url: 'https://github.com/reveny/Android-Compatibility-Layer', tagline: 'Android Compatibility Layer',
      host: ['x86-64 Linux with Wayland.'],
      code: ['ART built from AOSP.'],
      framework: ['Reimplemented, with FreeType, HarfBuzz, SQLite and PipeWire.'],
      native: ['arm64-v8a and x86_64 APKs. No 32-bit ARM.'],
      arm: ['Whole ARM64 libraries recompiled ahead of time with LLVM, or JIT.', 'good'],
      graphics: ['Vulkan and GLES on the host GPU.', 'good'],
      window: ['Wayland only. No rotation yet.', 'warn'],
      binder: ['None.', 'good'],
      webview: ['Listed as missing.', 'bad'],
      isolation: ['On by default: seccomp, landlock, private PID and network namespaces.', 'good'],
      dist: ['Release tarball with runtime and framework.'],
      license: ['MIT.'],
      repo: ['Since October 2026, 68 stars.'],
      apps: ['Very early; app support is still limited.']
    },
    {
      id: 'a4l', name: 'a4l', family: 'app', url: 'https://github.com/android-4-linux/a4l', tagline: 'android-4-linux',
      host: ['x86-64 Linux. ARM64 runs Java-only apps through split-process ART; ivi-shell for cars.'],
      code: ['Real ART.'],
      framework: ['Vendored Apache-2.0 AOSP Java subset over a clean-room C++23 core.'],
      native: ['App’s bionic libraries in one glibc process. Not yet on ARM64.'],
      arm: ['Not described.'],
      graphics: ['Skia on Vulkan into dma-buf, zero-copy; native GL through Mesa.', 'good'],
      window: ['Wayland xdg-toplevel or ivi-shell.'],
      binder: ['None.', 'good'],
      webview: ['Chromium WebView through its Vulkan draw functor.', 'good'],
      isolation: ['Native code is not confined; signatures checked on request.', 'bad'],
      dist: ['Meson source build.'],
      license: ['Apache-2.0, with an enforced clean-room policy.'],
      repo: ['Since November 2026, 2 stars.'],
      apps: ['15 of 18 F-Droid apps reach layout headless. Compose, Flutter, WebView and libGDX seen on screen.']
    },
    {
      id: 'macapk', name: 'Mac.apk', family: 'app', url: 'https://github.com/kksimp/Mac.apk', tagline: 'Apple Silicon Macs',
      host: ['macOS 26.4 or later, Apple Silicon only.'],
      code: ['dex2jvm converts DEX to Java bytecode on first launch; runs on bundled OpenJDK.'],
      framework: ['Own implementation modelled on AOSP, plus its own Play Services subset.'],
      native: ['ARM64 runs as is; ARM32 recompiled to ARM64; own bionic on macOS libraries.', 'good'],
      arm: ['Not needed on Apple Silicon.'],
      graphics: ['GLES via ANGLE, Vulkan via MoltenVK, both on Metal.', 'good'],
      window: ['Native macOS windows with a Dock icon and back button.', 'good'],
      binder: ['None described.'],
      webview: ['Not described; Instagram and Reddit run partly.'],
      isolation: ['None; apps run with the user’s permissions.', 'bad'],
      dist: ['Signed, notarized disk image; each app becomes its own .app.', 'good'],
      license: ['AGPL-3.0, with a commercial license sold.'],
      repo: ['Since May 2026, about 4,400 stars.'],
      apps: ['66 tested: 52 work, 10 partly, 4 not yet. Includes Minecraft and Subway Surfers.', 'good']
    },
    {
      id: 'apklayer', name: 'apklayer', family: 'user', url: 'https://github.com/erfanoabdi/apklayer', tagline: 'Real Android userspace',
      host: ['ARM64 Linux. x86-64 staged but untested.'],
      code: ['dex2jvm to a JVM.'],
      framework: ['Unmodified AOSP android-all with Robolectric shadows.'],
      native: ['LineageOS 24 libraries (SurfaceFlinger, HWUI, media) via bionic_translation.'],
      arm: ['Optimizing, lite and interpret modes on x86-64.', 'good'],
      graphics: ['SurfaceFlinger and HWUI as host processes; Vulkan in progress.'],
      window: ['One Wayland window per app; mobile and desktop modes.'],
      binder: ['Its own userspace binder; kernel binderfs optional.'],
      webview: ['Listed as working.', 'good'],
      isolation: ['Not described.'],
      dist: ['Meson build plus a prebuilts branch; Slint manager app.'],
      license: ['AGPL-3.0-or-later, with a commercial license sold.'],
      repo: ['Since October 2026, 27 stars.'],
      apps: ['Over 40 apps, including OsmAnd, Organic Maps, Thunderbird, VLC and Firefox.', 'good']
    },
    {
      id: 'innkeeper', name: 'Innkeeper', family: 'user', url: 'https://github.com/sargunv/innkeeper', tagline: 'Android 17 in Flatpak',
      host: ['x86-64 Linux on Wayland, kernel 6.5+, Flatpak.'],
      code: ['ART inside an Android 17 userspace.'],
      framework: ['Unmodified AOSP from Google’s public CI, about 1.2 GB.'],
      native: ['Android’s own.'],
      arm: ['Not yet; planned.', 'bad'],
      graphics: ['Its own vendor HALs on the desktop GPU.'],
      window: ['A Wayland window per app, with desktop portals.', 'good'],
      binder: ['Emulated in userspace.'],
      webview: ['Real Android WebView.', 'good'],
      isolation: ['Flatpak sandbox; all apps share one uid.', 'warn'],
      dist: ['Flatpak snapshot.'],
      license: ['AGPL-3.0-or-later.'],
      repo: ['Since October 2026, 4 stars.'],
      apps: ['Pre-alpha. Compose, Flutter, React Native and Godot apps; no Play Integrity.']
    }
  ];

  const shiraz = projects[0];
  const peers = projects.slice(1);
  let picked = $state('atl');
  const rival = $derived(peers.find(p => p.id === picked));

  const notes = {
    atl: { summary: 'The oldest and best-known translation layer, and the project Shiraz learned from first. Shiraz ships ATL’s bionic translation linker under its original notice.', ahead: ['Five years of work and an established community.', 'Packaged across Alpine, postmarketOS, Nix, the AUR and Flatpak.'], differs: ['No GTK in Shiraz, so one runtime serves SDL windows, AppImages, browser streaming and macOS.', 'Shiraz adds per-app AppImages and an ARM64 bridge for x86-64.'], learn: ['Its widget coverage and stub-then-implement workflow.', 'Its reach on Linux phone distributions.'] },
    acl: { summary: 'Runs DEX on AOSP host ART over a reimplemented framework, with games in mind. It recompiles whole native libraries to x86-64 ahead of time with LLVM.', ahead: ['Sandboxing on by default.', 'Ahead-of-time ARM64 recompilation for native game speed.', 'Vulkan.'], differs: ['ACL lists no rotation, WebView or MediaCodec. Shiraz tests portrait and landscape resizing in real apps.', 'ACL needs Wayland; Shiraz uses SDL2 and ships AppImages.'], learn: ['A default sandbox, which closes Shiraz’s biggest gap.', 'LLVM recompilation as a faster path for game code.'] },
    a4l: { summary: 'A clean-room C++23 layer running real ART with a vendored Apache-2.0 AOSP subset in one glibc process. It targets cars and kiosks as well as desktops.', ahead: ['Working Chromium WebView; Shiraz’s blocks Joplin’s editor.', 'Zero-copy Vulkan and dma-buf presentation.', 'Provenance rules enforced in CI.'], differs: ['Its evidence is mostly headless layout; Shiraz records interactive walkthroughs.', 'No ARM translation and source builds only.'], learn: ['Its WebView integration.', 'Dated, per-app status tracking.'] },
    macapk: { summary: 'The most polished consumer product here. It converts DEX to Java bytecode, runs it on OpenJDK, and turns each APK into a Mac app.', ahead: ['52 of 66 tested apps fully work, including large games.', 'Signed installer, Dock integration and a Play Services subset.', 'Vulkan through MoltenVK and an ARM32 recompiler.'], differs: ['Shiraz runs DEX on ART, as Android does, instead of converting it.', 'Shiraz targets Linux first; its macOS port takes the same native path.'], learn: ['A public compatibility list as the scoreboard.', 'Per-app packaging and a smooth install flow.'] },
    apklayer: { summary: 'Loads Android’s own native libraries from LineageOS 24 and runs SurfaceFlinger and other services as host processes over its own userspace binder, with the unmodified AOSP framework on a JVM.', ahead: ['Broad app list for its age, OsmAnd included.', 'Real Android graphics, media and providers.'], differs: ['It carries Android’s userspace libraries; Shiraz carries only the app.', 'ARM64 hosts first; x86-64 is untested.'], learn: ['A reference for how real Android behaves when Shiraz diverges.'] },
    innkeeper: { summary: 'Runs Google’s unmodified Android 17 userspace inside Flatpak with binder emulated, and boots in about three seconds warm. It can also run OpenHarmony.', ahead: ['Real Android, so most framework behaviour just works.', 'Flatpak sandbox and desktop portals.'], differs: ['A 1.2 GB Android image and a boot; Shiraz has neither.', 'No ARM translation yet, so most games are out.'], learn: ['Desktop integration through portals, notifications and media controls.'] }
  };
  const note = $derived(notes[picked]);

  const reasons = [
    ['Apps, not a second OS', 'Containers and VMs boot all of Android beside the desktop. Shiraz runs only the app, so it starts like a normal program.'],
    ['No kernel or root', 'Waydroid needs the binder driver and a root container. Shiraz needs no binder, no kernel module and no root.'],
    ['No toolkit lock-in', 'No GTK, no GNOME or KDE. SDL2 and EGL/GLES let one runtime drive a window, an AppImage, a browser stream and macOS.'],
    ['An APK becomes a Linux app', 'Builder turns a supported APK into one AppImage, in your browser. The APK stays unchanged and never leaves your machine.'],
    ['Proof in real apps', 'OsmAnd+ has to work completely: maps, search, routing, settings, persistence and resizing. Claims come from walkthroughs, with limits written down.'],
    ['Free, toward Linux phones', 'GPL-3.0-only, with Linux phones as the long-term goal, so you can use the apps you need on hardware you control.']
  ];

  const leads = [
    'The only one here that packages an APK into a standalone Linux app inside the browser.',
    'The only one that streams a running app to a browser over WebRTC.',
    'No GTK, no Wayland-only stack, no specific desktop.',
    'An ARM64 bridge on x86-64 and a native ARM64 macOS port in one codebase.',
    'Interactive evidence across Flutter, React Native, Unity, libGDX, Godot, SDL and Arc apps.'
  ];
  const trails = [
    'No app sandbox; ACL sandboxes by default and Innkeeper uses Flatpak.',
    'No Vulkan rendering, which blocks some Godot titles.',
    'WebView has no JavaScript engine; a4l and apklayer have one.',
    'OsmAnd+, the first target, is not yet verified end to end.',
    'No 32-bit ARM, and ARM translation is not yet in AppImages.',
    'Fewer verified apps than Mac.apk or apklayer report.'
  ];

  const ratingLabel = { good: 'Strength', warn: 'Partial', bad: 'Gap' };
</script>

<svelte:head>
  <title>Shiraz and its peers — Shiraz</title>
  <meta name="description" content="How Shiraz compares with ATL, ACL, a4l, Mac.apk, apklayer and Innkeeper: architecture, graphics, sandboxing, distribution and reported apps." />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/compare/" />
  <meta property="og:title" content="Shiraz and its peers" />
  <meta property="og:description" content="Seven ways to run Android™ apps off Android, side by side." />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/compare/" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
</svelte:head>

<a class="skip-link" href="#compare-main">Skip to the comparison</a>
<header id="top" class="compat-header wrap"><a class="brand" href={resolve('/')}><img src={home + 'spectre.svg'} width="42" height="42" alt="" /><span>shiraz</span></a><SiteNav /></header>

<main id="compare-main">
  <section class="peers-hero wrap">
    <div class="hero-copy">
      <p class="eyebrow">Shiraz and its peers · reviewed 21 November 2026</p>
      <h1>Run the app.<br><span>Not the phone.</span></h1>
      <p class="lede">Seven open projects want Android™ apps on your desktop. Here is how Shiraz is built, why, and where each of the others is ahead of us.</p>
      <div class="hero-actions"><a class="button primary" href="#head-to-head">Compare head to head</a><a class="button secondary" href="#all">See all seven</a></div>
    </div>
    <div class="hero-glass" aria-hidden="true">
      {#each projects as p, i}<div class="pillar-col" class:me={p.id === 'shiraz'}><div class="pillar {p.family}" style="--i:{i}"></div><span>{p.name}</span></div>{/each}
    </div>
  </section>

  <section class="block wrap" aria-labelledby="families-title">
    <header class="block-head"><p class="eyebrow">Three approaches</p><h2 id="families-title">Three ways in.<br><span>Very different costs.</span></h2></header>
    <div class="families">
      {#each families as f}
        <article class="family {f.id}"><p class="family-line">{f.line}</p><h3>{f.name}</h3><p>{f.text}</p><p class="family-who">{f.who}</p></article>
      {/each}
    </div>
  </section>

  <section class="block wrap" aria-labelledby="why-title">
    <header class="block-head"><p class="eyebrow">Why we built Shiraz</p><h2 id="why-title">An Android™ app should be<br><span>just another program.</span></h2></header>
    <ol class="reasons">
      {#each reasons as [title, text]}<li><h3>{title}</h3><p>{text}</p></li>{/each}
    </ol>
  </section>

  <section class="block wrap" aria-labelledby="layers-title">
    <header class="block-head"><p class="eyebrow">Layer by layer</p><h2 id="layers-title">What sits between<br><span>the APK and your screen.</span></h2><p>Each row follows an app from its bytecode to a window. The edge colour marks the family.</p></header>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable region) -->
    <div class="layers-scroll" role="region" aria-label="Runtime layers per project" tabindex="0">
      <table class="layers"><caption class="visually-hidden">Runtime layers per project</caption>
        <thead><tr><th scope="col">Project</th><th scope="col">App code</th><th scope="col">Framework</th><th scope="col">Native code</th><th scope="col">Graphics</th><th scope="col">Window</th></tr></thead>
        <tbody>{#each projects as p}<tr class={p.family} class:me={p.id === 'shiraz'}><th scope="row">{p.name}</th><td>{p.code[0]}</td><td>{p.framework[0]}</td><td>{p.native[0]}</td><td>{p.graphics[0]}</td><td>{p.window[0]}</td></tr>{/each}</tbody>
      </table>
    </div>
  </section>

  <section class="block wrap" id="head-to-head" aria-labelledby="h2h-title">
    <header class="block-head"><p class="eyebrow">Head to head</p><h2 id="h2h-title">Shiraz against<br><span>one project at a time.</span></h2></header>
    <div class="picker" role="group" aria-label="Choose a project to compare with Shiraz">
      {#each peers as p}<button type="button" class={p.family} aria-pressed={picked === p.id} onclick={() => picked = p.id}>{p.name}</button>{/each}
    </div>
    <div class="duel">
      <div class="duel-head"><span></span><p class="me-name">Shiraz<small>{shiraz.tagline}</small></p><p><a href={rival.url}>{rival.name} <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg></a><small>{rival.tagline}</small></p></div>
      {#each rows as [key, label]}
        <div class="duel-row"><p class="duel-label">{label}</p>
          {#each [shiraz, rival] as p}<p class="duel-cell">{#if p[key][1]}<span class="mark {p[key][1]}" title={ratingLabel[p[key][1]]}><span class="visually-hidden">{ratingLabel[p[key][1]]}: </span></span>{/if}{p[key][0]}</p>{/each}
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
    <p class="legend"><span><span class="mark good"></span>Strength</span><span><span class="mark warn"></span>Partial</span><span><span class="mark bad"></span>Gap</span><span>Unmarked: described, not judged.</span></p>
  </section>

  <section class="block wrap" id="all" aria-labelledby="all-title">
    <header class="block-head"><p class="eyebrow">All seven</p><h2 id="all-title">Every project,<br><span>every dimension.</span></h2><p>Scroll sideways for every column. Competitor values are their own published claims.</p></header>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable region) -->
    <div class="all-scroll" role="region" aria-labelledby="all-title" tabindex="0">
      <table class="all"><caption class="visually-hidden">Seven projects compared across fourteen dimensions</caption>
        <thead><tr><th scope="col"><span class="visually-hidden">Dimension</span></th>{#each projects as p}<th scope="col" class:me={p.id === 'shiraz'}><a href={p.url}>{p.name}</a><small>{families.find(f => f.id === p.family).name}</small></th>{/each}</tr></thead>
        <tbody>{#each rows as [key, label]}<tr><th scope="row">{label}</th>{#each projects as p}<td class:me={p.id === 'shiraz'}>{#if p[key][1]}<span class="mark {p[key][1]}" title={ratingLabel[p[key][1]]}><span class="visually-hidden">{ratingLabel[p[key][1]]}: </span></span>{/if}{p[key][0]}</td>{/each}</tr>{/each}</tbody>
      </table>
    </div>
    <p class="fineprint">“Not described” means the README does not say, which is not the same as unsupported.</p>
  </section>

  <section class="block wrap" aria-labelledby="stand-title">
    <header class="block-head"><p class="eyebrow">Where Shiraz stands</p><h2 id="stand-title">Honest about both sides.</h2></header>
    <div class="stand">
      <div class="lead"><h3>Where Shiraz leads</h3><ul>{#each leads as item}<li>{item}</li>{/each}</ul></div>
      <div class="trail"><h3>Where Shiraz trails</h3><ul>{#each trails as item}<li>{item}</li>{/each}</ul></div>
    </div>
    <div class="stand-cta"><a class="button primary" href={home + '#builder'}>Try the builder</a><a class="button secondary" href={resolve('/compatibility/')}>See tested apps</a></div>
  </section>

  <section class="block wrap sources" aria-labelledby="sources-title">
    <h2 id="sources-title">Sources and method</h2>
    <p>Shiraz facts come from its README and tested-apps list at version {__SHIRAZ_VERSION__}. Other projects’ facts come from their READMEs and wikis, read on 21 November 2026. We did not build or run them, so their compatibility figures are their own claims. Corrections are welcome as <a href="https://github.com/spectre-oss/shiraz/issues">issues</a>.</p>
    <ul>{#each projects as p}<li><a href={p.url}>{p.name}</a></li>{/each}<li><a href="https://github.com/kksimp/Mac.apk/wiki/How-Mac.apk-Works">How Mac.apk works</a></li></ul>
  </section>
</main>
<Footer />

<style>
  .peers-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr); gap: clamp(32px, 5vw, 80px); align-items: center; padding-block: clamp(32px, 6vw, 88px) clamp(56px, 8vw, 120px); }
  .peers-hero h1 { font-size: clamp(3rem, 8vw, 7.5rem); line-height: .9; }
  .peers-hero h1 span, .block-head h2 span { color: var(--ink-soft); }
  .lede { max-width: 52ch; margin-top: 26px; font-size: clamp(17px, 1.6vw, 20px); color: var(--ink-soft); }
  .hero-actions, .stand-cta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 32px; }

  /* Seven glass pillars, one per project, lit by family. Decorative only. */
  .hero-glass { overflow: hidden; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px; height: clamp(280px, 34vw, 460px); padding: 36px 14px 14px; border-radius: 50% 50% 18px 18px / 22% 22% 18px 18px; background: #12050b; box-shadow: 0 0 0 1px #ffffff1f, 0 0 110px #4f6df033; }
  .pillar-col { display: grid; grid-template-rows: 1fr auto; gap: 10px; min-width: 0; }
  .pillar { --c: var(--glass-teal); align-self: end; height: 58%; border-radius: 999px 999px 4px 4px; background: linear-gradient(170deg, color-mix(in srgb, var(--c) 70%, #fff) 0%, var(--c) 45%, color-mix(in srgb, var(--c) 60%, #000) 100%); box-shadow: 0 0 34px color-mix(in srgb, var(--c) 45%, transparent); opacity: .5; }
  .pillar.user { --c: var(--glass-saffron); height: 74%; }
  .me .pillar { --c: var(--glass-rose); height: 86%; opacity: 1; box-shadow: 0 0 60px color-mix(in srgb, var(--c) 70%, transparent); }
  .pillar-col span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: center; font-family: var(--display); font-stretch: 70%; font-size: 12px; font-weight: 700; color: var(--ink-faint); }
  .me span { color: var(--ink); }

  .block { padding-block: clamp(56px, 8vw, 112px) 0; }
  .block-head { max-width: 820px; margin-bottom: clamp(28px, 4vw, 52px); }
  .block-head h2, .sources h2 { font-size: clamp(2.3rem, 5vw, 4.6rem); line-height: .95; }
  .block-head > p:last-child:not(.eyebrow) { margin-top: 20px; font-size: 18px; color: var(--ink-soft); }

  .families { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  .family { --c: var(--glass-teal); position: relative; display: grid; gap: 12px; align-content: start; padding: 28px; border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); overflow: hidden; }
  .family-line { display: flex; align-items: center; gap: 10px; }
  .family-line::before { content: ""; flex: none; width: 10px; height: 10px; border-radius: 50%; background: var(--c); box-shadow: 0 0 16px var(--c); }
  .family.user { --c: var(--glass-saffron); }
  .family.os { --c: var(--glass-lapis); }
  .family h3 { font-size: 30px; }
  .family p { color: var(--ink-soft); }
  .family-line { font-size: 14px; font-weight: 500; color: var(--ink-faint) !important; }
  .family-who { margin-top: 6px; padding-top: 14px; border-top: 1px solid var(--line); color: var(--ink) !important; font-weight: 500; }

  .reasons { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 40px; counter-reset: reason; }
  .reasons li { counter-increment: reason; display: grid; gap: 8px; align-content: start; padding-block: 24px; border-top: 1px solid var(--line); }
  .reasons h3 { font-size: 26px; }
  .reasons p { color: var(--ink-soft); }

  .layers-scroll, .all-scroll { overflow-x: auto; border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); }
  table { width: 100%; border-collapse: collapse; text-align: left; font-size: 15px; line-height: 1.5; }
  .layers { min-width: 980px; }
  .layers th, .layers td, .all th, .all td { padding: 18px; border-bottom: 1px solid var(--line); vertical-align: top; }
  .layers thead th, .all thead small { font-size: 13px; font-weight: 500; color: var(--ink-faint); }
  .layers tbody th { position: relative; width: 140px; padding-left: 26px; font-family: var(--display); font-stretch: 78%; font-size: 22px; line-height: 1.15; }
  .layers tbody th::before { content: ""; position: absolute; left: 8px; top: 27px; width: 8px; height: 8px; border-radius: 50%; background: var(--glass-teal); }
  .layers tr.user th::before { background: var(--glass-saffron); }
  .layers tr.me th::before { background: var(--glass-rose); box-shadow: 0 0 18px var(--glass-rose); }
  .layers td { color: var(--ink-soft); }
  .layers tr.me td, .layers tr.me th { color: var(--ink); background: var(--wine-raised); }
  tbody tr:last-child > * { border-bottom: 0; }

  .picker { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
  .picker button { min-height: 44px; padding: 0 18px; border: 1px solid var(--line); border-radius: 999px; background: transparent; color: var(--ink-soft); font-weight: 600; transition: border-color .2s ease, background-color .2s ease, color .2s ease; }
  .picker button:hover { border-color: var(--line-strong); color: var(--ink); }
  .picker button[aria-pressed='true'] { background: var(--saffron); border-color: var(--saffron); color: var(--on-saffron); }

  .duel { border: 1px solid var(--line); border-radius: 20px; background: var(--wine-deep); overflow: hidden; }
  .duel-head, .duel-row { display: grid; grid-template-columns: 170px minmax(0, 1fr) minmax(0, 1fr); }
  .duel-head p, .duel-row p { padding: 16px 20px; min-width: 0; }
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
  .duel-cell { font-size: 15px; line-height: 1.5; color: var(--ink-soft); }

  .mark { display: inline-block; width: 10px; height: 10px; margin-right: 9px; vertical-align: .05em; }
  .mark.good { border-radius: 50%; background: var(--glass-emerald); box-shadow: 0 0 10px var(--glass-emerald); }
  .mark.warn { border-radius: 2px; background: var(--glass-saffron); }
  .mark.bad { border-radius: 1px; background: var(--glass-rose); transform: rotate(45deg) scale(.85); }

  .verdict { margin-top: 16px; padding: 28px; border: 1px solid var(--line); border-radius: 20px; }
  .verdict-summary { max-width: 72ch; font-size: 18px; }
  .verdict-cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px; margin-top: 24px; }
  .verdict h3, .stand h3 { font-size: 22px; margin-bottom: 10px; }
  ul { margin: 0; padding-left: 18px; display: grid; gap: 6px; color: var(--ink-soft); }
  .legend { display: flex; flex-wrap: wrap; gap: 8px 22px; margin-top: 16px; font-size: 14px; color: var(--ink-faint); }

  .all { min-width: 1500px; font-size: 14px; }
  .all thead th { font-family: var(--display); font-stretch: 78%; font-size: 22px; line-height: 1.1; }
  .all thead th a:hover { text-decoration: underline; text-underline-offset: 5px; }
  .all thead small { display: block; margin-top: 4px; font-family: var(--body); font-stretch: normal; }
  .all tbody th { position: sticky; left: 0; z-index: 1; width: 130px; background: var(--wine-deep); font-size: 13px; font-weight: 500; color: var(--ink-faint); }
  .all td { color: var(--ink-soft); }
  .all .me { background: var(--wine-raised); color: var(--ink); }
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
    .pillar { animation: pillar-rise .9s var(--ease-out) both; animation-delay: calc(.15s + var(--i) * 70ms); transform-origin: bottom; }
  }
  @keyframes pillar-rise { from { transform: scaleY(.2); opacity: 0; } }

  /* The brand already links home; drop the duplicate so the bar fits a phone. */
  @media (max-width: 900px) {
    .peers-hero { grid-template-columns: 1fr; }
    .hero-glass { height: 240px; }
    .families, .reasons, .verdict-cols, .stand { grid-template-columns: 1fr; }
    .duel-head, .duel-row { grid-template-columns: 1fr 1fr; }
    .duel-head > span, .duel-label { grid-column: 1 / -1; }
    .duel-head > span { display: none; }
    .duel-label { padding-bottom: 0 !important; }
  }
</style>
