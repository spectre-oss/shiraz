<script>
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  const base = resolve('/').replace(/\/$/, '');
  import Builder from '#lib/Builder.svelte';
  import Footer from '#lib/Footer.svelte';
  import SiteNav from '#lib/SiteNav.svelte';
  import { initIcons } from '#lib/icons.js';
  import { initInteractions } from '#lib/interactions.js';
  import media from '#lib/media.json';
  import '../styles.css';
  // The newest published tag when the build could ask GitHub; otherwise the
  // source version, linked to the release list because its tag may not exist.
  const releaseTag = __SHIRAZ_RELEASE_TAG__;
  const release = releaseTag ?? `v${__SHIRAZ_VERSION__}`;
  const releaseUrl = 'https://github.com/spectre-oss/shiraz/releases' + (releaseTag ? `/tag/${releaseTag}` : '');
  // True only when the build holds the film and poster that media.json pins.
  const film = __SHIRAZ_FILM__;
  let translatePoem = $state(false);
  let activeVerse = $state(-1);
  let allPeers = $state(false);
  // Fifteen panes: three across in the tall window, five across in the desktop one.
  const paneRows = [['rose','saffron','rose'],['teal','lapis','teal'],['saffron','rose','saffron'],['lapis','emerald','lapis'],['teal','saffron','teal']];
  const tallPanes = paneRows.flat();
  const widePanes = [0,1,2].flatMap(column => paneRows.map(row => row[column]));
  const verses = [
    {fa: 'بس بگردید و بگردد روزگار', en: 'Many have passed, and time will turn;'},
    {fa: 'دل به دنیا در نبندد هوشیار', en: 'The wise do not bind their hearts to this world.'},
    {fa: 'ای که دستت می‌رسد کاری بکن', en: 'While you still have the power, do some good,'},
    {fa: 'پیش از آن کز تو نیاید هیچ کار', en: 'Before the day you can do nothing more.'}
  ];
  onMount(() => {
    const stopIcons = initIcons();
    const stopInteractions = initInteractions();
    let translationTimer;
    let poemObserver;
    if (matchMedia('(hover: none) and (pointer: coarse)').matches) {
      poemObserver = new IntersectionObserver(([entry]) => {
        clearTimeout(translationTimer);
        if (entry.isIntersecting) {
          translationTimer = setTimeout(() => {
            translatePoem = true;
            poemObserver.disconnect();
          }, 1800);
        }
      }, {threshold: 0.5});
      poemObserver.observe(document.querySelector('.poem-translation'));
    }
    return () => { clearTimeout(translationTimer); poemObserver?.disconnect(); stopInteractions(); stopIcons(); };
  });
</script>
<svelte:head>
  <title>Shiraz — Android™ apps on Linux, macOS and Windows.</title>
  <meta name="description" content="Supported Android™ apps on Linux, with an experimental Apple Silicon macOS port and native Windows foundation. Build an AppImage or Flatpak locally in your browser with Shiraz." />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:title" content="Shiraz — Your Android™ apps, at home on Linux, macOS and Windows" />
  <meta property="og:description" content="An experimental, open-source Android™ compatibility layer for Linux, macOS and Windows. Build an AppImage or Flatpak locally in your browser. No virtual machine." />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta property="og:image:type" content="image/png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="Shiraz. Your Android™ apps. A little more at home. Android™ apps on Linux, macOS and Windows." />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Shiraz — Android™ apps on Linux, macOS and Windows" />
  <meta name="twitter:description" content="Your Android™ apps. A little more at home. Explore Shiraz and package a supported APK locally in your browser." />
  <meta name="twitter:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  {@html '<script type="application/ld+json">' + JSON.stringify({"@context":"https://schema.org","@type":"SoftwareApplication","name":"Shiraz","applicationCategory":"DeveloperApplication","operatingSystem":"Linux, macOS, Windows","license":"https://www.gnu.org/licenses/gpl-3.0.html","url":"https://spectre-oss.github.io/shiraz/","sameAs":"https://github.com/spectre-oss/shiraz","offers":{"@type":"Offer","price":"0","priceCurrency":"USD"}}) + '</' + 'script>'}
  <meta name="twitter:image:alt" content="Shiraz — Your Android™ apps. A little more at home." />
</svelte:head>

  <a class="skip-link" href="#main">Skip to content</a>
  <div class="scroll-progress" aria-hidden="true"></div>
  <header id="top" class="site-header wrap">
    <a class="brand" href="#top" aria-label="Shiraz home"><img src="{base}/spectre.svg" width="42" height="42" alt="Spectre"><span>shiraz</span></a>
    <SiteNav />
  </header>
  <main id="main">
    <section class="hero" aria-labelledby="title">
      <div class="hero-inner">
        <div class="hero-content">
          <h1 id="title"><span class="line"><span>Your Android<sup class="trademark">™</sup> apps.</span></span> <span class="line"><span>A little more at home.</span></span></h1>
          <p class="intro">Supported Android™ apps, as desktop windows on Linux.<br class="desktop-break"> No virtual machine. No full Android™ system.</p>
          <div class="actions"><a class="button primary" href="#builder">Open the builder <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a><a class="button secondary" href="https://github.com/spectre-oss/shiraz">Explore the source <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></div>
          <a class="hero-release" href={releaseUrl}><span class="release-dot" aria-hidden="true"></span>Latest release <strong>{release}</strong><span class="release-status">Preview</span><svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a>
          <p class="local-note">The builder runs in your browser. Your APK never leaves it.</p>
        </div>
        <!-- Decorative: light from a tall window lands, rearranged, in a wide one. -->
        <div class="hero-art" aria-hidden="true">
          <div class="light-beam"></div>
          <div class="orsi">{#each tallPanes as pane, index}<span class="glass-pane" style="--c:var(--glass-{pane});--i:{index}"></span>{/each}</div>
          <div class="landing-window"><div class="window-bar"><i></i><i></i><i></i></div><div class="window-panes">{#each widePanes as pane, index}<span class="light-patch" style="--c:var(--glass-{pane});--i:{index}"></span>{/each}</div></div>
        </div>
      </div>
    </section>
    <section class="experimental-notice" aria-labelledby="experimental-title">
      <div class="experimental-inner"><h2 id="experimental-title">Still experimental.</h2><p>Compatibility grows app by app, and some features are still missing.</p><p class="experimental-macos">On Apple Silicon Macs, native ELF and Bionic bridges, VideoToolbox and AudioToolbox media, Metal, CoreLocation, notifications, and signed <code>.app</code> bundling exist; on Windows, native Win32 primitives, WASAPI, Media Foundation, DirectWrite, and portable or MSIX bundling are in place. Full end-to-end app execution on macOS and Windows is still experimental.</p><p class="experimental-security">Apps run with your permissions and no Android™ sandbox. Run only APKs you trust.</p></div>
    </section>
    <section class="shiraz-web" id="web" aria-labelledby="web-title">
      <div class="web-heading"><p class="eyebrow">Shiraz Web</p><h2 id="web-title">Built for<br><span>Linux mobile.</span></h2>
        <!-- Decorative: the host window sends its light to a phone, a packet at a time. -->
        <div class="web-art" aria-hidden="true"><div class="host"><div class="window-bar"><i></i><i></i><i></i></div><div class="host-panes">{#each widePanes as pane, index}<span style="--c:var(--glass-{pane});--i:{index}"></span>{/each}</div></div><div class="stream"><i></i><i></i><i></i></div><div class="phone"><div class="phone-panes">{#each tallPanes as pane, index}<span style="--c:var(--glass-{pane});--i:{index}"></span>{/each}</div></div><span class="live"><i></i>Live</span></div></div>
      <div class="web-description"><p>The app runs on a Linux host. Your browser becomes its window, streamed over WebRTC, with touch and keyboard.</p><p>Made with Linux phones in mind.</p><a class="button secondary web-link" href="https://github.com/spectre-oss/shiraz/blob/main/docs/WEB.md">Set up Shiraz Web <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a><small>Experimental.</small></div>
    </section>
    <section class="shiraz-web shiraz-mac" id="mac" aria-labelledby="mac-title">
      <div class="web-heading"><p class="eyebrow">Shiraz Launcher</p><h2 id="mac-title">A shelf for<br><span>your Mac.</span></h2></div>
      <div class="web-description"><p>All your APKs in one window on an Apple Silicon Mac. Drop one in: Shiraz reads its name and icon and leaves the file untouched.</p><p>Launch, stop and restart each app with its own screen size, Dock icon and console. Native libraries stay off until you switch them on.</p><a class="button secondary web-link" href="https://github.com/spectre-oss/shiraz/blob/main/macos/Launcher/README.md">Build the launcher <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Experimental · macOS 27 on Apple Silicon · Built from source with Xcode. An APK that imports can still fail to start.</small></div>
      {#if film}
      <!-- Nothing but the poster loads until the visitor presses play. -->
      <figure class="mac-film">
        <video controls playsinline preload="none" width={media.video.width} height={media.video.height} poster="{base}/media/{media.poster.file}" aria-label="Shiraz Launcher on a Mac, {media.video.seconds} second film with sound" aria-describedby="mac-film-note">
          <source src="{base}/media/{media.video.file}" type="video/mp4" />
          <track kind="captions" src="{base}/media/{media.captions}" srclang="en" label="English" />
        </video>
        <figcaption id="mac-film-note">{media.video.seconds} seconds, with sound and captions, from a development build. The F-Droid list shows what you can download, not what runs.</figcaption>
      </figure>
      {/if}
    </section>
    <section class="architecture" id="approach" aria-labelledby="architecture-title">
      <header class="architecture-heading"><h2 id="architecture-title">Android™ on the inside.<br><span>Linux, macOS or Windows underneath.</span></h2><p>Keep the app. Translate only what it needs.</p></header>
      <div class="stack-story">
        <figure class="stack-figure" aria-labelledby="stack-caption">
          <figcaption id="stack-caption">The Shiraz stack, simplified. Light enters at the app and lands on your desktop.</figcaption>
          <ol class="stack-diagram">
            <li data-layer="0"><span class="slab" aria-hidden="true"></span><a class="layer-text" href="#step-1"><span class="layer-number">01</span><span class="layer-name">Your APK<small>App code · resources · native libraries</small></span></a></li>
            <li data-layer="1"><span class="slab" aria-hidden="true"></span><a class="layer-text" href="#step-2"><span class="layer-number">02</span><span class="layer-name">ART<small>Android™ Runtime</small></span></a></li>
            <li data-layer="2"><span class="slab" aria-hidden="true"></span><a class="layer-text" href="#step-3"><span class="layer-number">03</span><span class="layer-name">Android™ API layer<small>Framework · resources · services</small></span></a></li>
            <li data-layer="3"><span class="slab" aria-hidden="true"></span><a class="layer-text" href="#step-4"><span class="layer-number">04</span><span class="layer-name">Native bridges<small>15 NDK libraries · JNI · Bionic · SDL · EGL/GLES</small></span></a></li>
            <li data-layer="4"><span class="slab" aria-hidden="true"></span><a class="layer-text" href="#step-5"><span class="layer-number">05</span><span class="layer-name">Your desktop<small>Linux · macOS · Windows · graphics · media</small></span></a></li>
          </ol>
        </figure>
        <div class="stack-explanations">
          <article class="stack-step" id="step-1" data-step="0"><span class="step-index">01 / The app</span><h3>Start with an APK.</h3><p>Code, assets and native libraries, in one package. Shiraz uses the original, unchanged.</p><a href="https://github.com/spectre-oss/shiraz/tree/main/src/shiraz">Package handling <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" id="step-2" data-step="1"><span class="step-index">02 / Execution</span><h3>ART runs the code.</h3><p>Android™’s own runtime, built standalone for Linux and macOS, runs the app’s bytecode, compiled ahead of time where it can.</p><a href="https://github.com/spectre-oss/shiraz/blob/main/scripts/bootstrap_runtime.py">Runtime setup <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" id="step-3" data-step="2"><span class="step-index">03 / Compatibility</span><h3>Speak the app’s language.</h3><p>Views, media, storage, services. All 4,445 Android™ 15 framework classes exist in Shiraz. How completely each one behaves grows app by app.</p><a href="https://github.com/spectre-oss/shiraz/tree/main/java/android">Java framework code <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" id="step-4" data-step="3"><span class="step-index">04 / Translation</span><h3>Connect it to the host.</h3><p>JNI and all 15 tracked Android™ 15 NDK C/C++ libraries (887 audited symbols across media, audio, ICU, Binder, NNAPI and Vulkan loader hooks) bridge apps to the host alongside SDL and EGL/GLES. Native host backends span Linux, Apple Silicon macOS (VideoToolbox, AudioToolbox, Metal, CoreLocation, guest ELF/Bionic) and Windows (Win32, WASAPI, Media Foundation, DirectWrite, DXGI). On Linux x86-64, optional <a class="inline-link" href="https://digitalisx64.github.io/">Digitalis</a> translation runs ARM64 code from a source build.</p><a href="https://github.com/spectre-oss/shiraz/blob/main/docs/cross_platform.md">Cross-platform &amp; NDK architecture <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" id="step-5" data-step="4"><span class="step-index">05 / At home</span><h3>A window on your desktop.</h3><p>No Android™ image to boot. No virtual machine. Just a window, your keyboard and your mouse — packaged as AppImages or Flatpaks on Linux, signed <code>.app</code> bundles on macOS, or portable and MSIX folders on Windows.</p><a href="https://github.com/spectre-oss/shiraz/blob/main/native/presentation.cpp">Window presentation <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
        </div>
      </div>

    </section>
    <section class="comparison" id="compare" aria-labelledby="comparison-title">
      <header class="comparison-heading"><h2 id="comparison-title">Choose the approach<br><span>that fits your desktop.</span></h2><p>Translation layer, container, virtual device or a whole OS. Each one trades something.</p></header>
      <p class="table-hint" id="comparison-hint">Swipe or scroll sideways to see the full comparison.</p>
      <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
      <div class="comparison-scroll" role="region" aria-labelledby="comparison-title" aria-describedby="comparison-hint" tabindex="0">
        <table class="comparison-table" class:all-peers={allPeers} id="peer-table"><caption class="visually-hidden">Android™ app execution approaches compared</caption><thead><tr><th scope="col">Project</th><th scope="col">Approach</th><th scope="col">Android™ environment</th><th scope="col">What to keep in mind</th></tr></thead><tbody>
          <tr class="shiraz-row"><th scope="row"><a href="https://github.com/spectre-oss/shiraz">Shiraz</a><small>This project</small></th><td>API translation<small>ART + native Linux, macOS and Windows bridges</small></td><td>No full Android™ image<small>4,445 API 35 classes · 15 NDK libs · SDL / EGL / GLES</small></td><td>AppImage and sandboxed Flatpak packaging plus browser access on Linux; experimental Apple Silicon macOS port (VideoToolbox, CoreLocation, signed .app) and native Windows Win32, WASAPI and MSIX foundation. Complete API 35 class and NDK C symbol surface with bounded, experimental behavior; AppImages run without an Android™-style app sandbox.</td></tr>
          <tr><th scope="row"><a href="https://docs.waydro.id/">Waydroid <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Container<small>Shares the Linux kernel</small></td><td>Full Android™ system<small>LineageOS-based image</small></td><td>A full Android™ environment integrated with Linux. Requires container setup and suitable host support.</td></tr>
          <tr><th scope="row"><a href="https://github.com/denialwm/droidloom">Droidloom <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Namespaced userspace<small>Shares the Linux kernel</small></td><td>Full Android™ userspace<small>ART · Binder · Zygote · system_server</small></td><td>Separate Wayland windows with GPU acceleration. Includes Teto for ARM64 apps on x86_64; requires Binder and compatible Wayland protocols. Early preview.</td></tr>
          <tr><th scope="row"><a href="https://gitlab.com/android_translation_layer/android_translation_layer">ATL <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Android™ Translation Layer</small></th><td>API translation<small>Shiraz’s architectural starting point</small></td><td>Android™ APIs on Linux<small>App-focused compatibility</small></td><td>A related translation approach. Check its project documentation for supported apps and build requirements.</td></tr>
          <tr><th scope="row"><a href="https://developer.android.com/studio/run/emulator">Android™ Emulator <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Virtual device<small>Simulated Android™ hardware</small></td><td>Android™ system image<small>Configured through an AVD</small></td><td>Designed for development and testing across device profiles, Android™ versions, and simulated sensors.</td></tr>
          <tr><th scope="row"><a href="https://github.com/anbox/anbox">Anbox <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Archived project</small></th><td>Container<small>Shares the Linux kernel</small></td><td>Full Android™ system</td><td>A historical container approach. The original repository is archived; distinct from Anbox Cloud.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://github.com/remote-android/redroid-doc">Redroid <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Remote Android™</small></th><td>Container<small>Docker, Podman or Kubernetes</small></td><td>Full Android™ system<small>Headless, viewed over the network</small></td><td>Runs many instances on one Linux host, with optional GPU acceleration; arm64 and amd64 images. Needs binder (and on older images ashmem) kernel modules on the host.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://www.genymotion.com/product-desktop/">Genymotion Desktop <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Virtual device<small>Desktop virtual machine</small></td><td>Android™ system image<small>Configured per virtual device</small></td><td>Commercial; runs on Linux, macOS and Windows. A free personal-use edition leaves out some features, including the newest Android™ version.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://blissos.org/">Bliss OS <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Full operating system<small>Installed or booted on a PC</small></td><td>Full Android™ system<small>Based on AOSP and Android-x86</small></td><td>Replaces or sits beside your desktop OS rather than running inside it. Hardware support depends on the PC.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://www.bluestacks.com/mac">BlueStacks Air <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>App player<small>Proprietary, game-focused</small></td><td>Full Android™ system<small>Bundled by the vendor</small></td><td>Free, built for Apple Silicon Macs. Closed source.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://docs.sailfishos.org/Support/Help_Articles/Android_App_Support">Sailfish OS Android™ App Support <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Jolla AppSupport</small></th><td>Container<small>LXC, shares the Linux kernel</small></td><td>Modified Android™ system<small>Installed from the Jolla Store</small></td><td>Proprietary, and ships only for Sailfish OS on Jolla’s supported phones with a Sailfish OS licence.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://github.com/shashlik">Shashlik <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Inactive project</small></th><td>Emulated Android™<small>A stripped-down Android™ booted per app</small></td><td>Minimal Android™ system<small>Graphics rendered on the host</small></td><td>An earlier KDE-aligned attempt to run Android™ apps as Linux desktop apps. Last release was 0.9 in early 2016.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://github.com/vladikoff/chromeos-apk/blob/master/archon.md">ARChon <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Inactive project</small></th><td>Browser runtime<small>Unofficial build of Google’s App Runtime for Chrome</small></td><td>Android™ runtime in Chrome<small>Each app becomes a Chrome extension</small></td><td>Ran converted APKs in Chrome on Windows, macOS and Linux. A 2014–2015 community project built on Google’s ARC beta.</td></tr>
          <tr class="extra-peer"><th scope="row"><a href="https://www.theregister.com/2024/03/05/windows_subsystem_android/">Windows Subsystem for Android™ <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Discontinued</small></th><td>Virtual machine<small>Windows 11 only</small></td><td>AOSP-based system<small>Apps from the Amazon Appstore</small></td><td>Microsoft ended support on 5 March 2025.</td></tr>
        </tbody></table>
      </div>
      <button class="peers-toggle" type="button" aria-expanded={allPeers} aria-controls="peer-table" onclick={() => allPeers = !allPeers}>{allPeers ? 'Show fewer' : 'Show 8 more approaches'}<svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></button>
      <p class="comparison-note">Architecture, not a performance ranking. Results depend on the app, device and setup. Reviewed November 2026. <a href={resolve('/compare/')}>Shiraz beside six close peers, in depth</a>.</p>
    </section>
    <section class="packaging-path" aria-label="APK plus Shiraz runtime produces an AppImage or Flatpak"><p><b class="term-apk">APK</b> <span aria-hidden="true"><svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></span> <b class="term-runtime">Shiraz runtime</b> <span class="result"><span aria-hidden="true"><svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></span> <b class="term-out">AppImage / Flatpak</b></span></p></section>
    <Builder />
    <section class="project-info" aria-label="About Shiraz">
      <div class="principles"><span>Linux · macOS · Windows</span><span>GTK-free</span><span>Open source · GPLv3</span></div>
      <details class="source-build"><summary>Build from source<span class="disclosure-icon" aria-hidden="true"><svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></span></summary><div class="build-content"><p>With the runtime dependencies prepared:</p><div class="code-area"><pre id="build-command"><code>make build
make appimage APK=/path/to/app.apk
make flatpak APK=/path/to/app.apk</code></pre><button class="copy-button" type="button" aria-label="Copy build commands"><svg class="icon" data-icon="copy" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M8.25 7.5V6.10822C8.25 4.97324 9.09499 4.01015 10.2261 3.91627C10.5994 3.88529 10.9739 3.85858 11.3495 3.83619M15.75 18H18C19.2426 18 20.25 16.9926 20.25 15.75V6.10822C20.25 4.97324 19.405 4.01015 18.2739 3.91627C17.9006 3.88529 17.5261 3.85858 17.1505 3.83619M17.1505 3.83619C16.8672 2.91757 16.0116 2.25 15 2.25H13.5C12.4884 2.25 11.6328 2.91757 11.3495 3.83619M17.1505 3.83619C17.2152 4.04602 17.25 4.26894 17.25 4.5V5.25H11.25V4.5C11.25 4.26894 11.2848 4.04602 11.3495 3.83619"/><path d="M6.75 7.5H4.875C4.25368 7.5 3.75 8.00368 3.75 8.625V20.625C3.75 21.2463 4.25368 21.75 4.875 21.75H14.625C15.2463 21.75 15.75 21.2463 15.75 20.625V16.5C15.75 11.5294 11.7206 7.5 6.75 7.5Z"/><path d="M15.75 18.75V16.875C15.75 15.011 14.239 13.5 12.375 13.5H10.875C10.2537 13.5 9.75 12.9963 9.75 12.375V10.875C9.75 9.01104 8.23896 7.5 6.375 7.5H5.25"/></svg></button></div><a class="guide-link" href="https://github.com/spectre-oss/shiraz/blob/main/docs/DEVELOPMENT.md">Development guide <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><p class="copy-status" role="status" aria-live="polite"></p></div></details>
      <details class="source-build"><summary>Get an app from F-Droid<span class="disclosure-icon" aria-hidden="true"><svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></span></summary><div class="build-content"><p>From a source checkout. Shiraz checks the signature on F-Droid’s index, then checks each download against the SHA-256 that index lists. Replace the package name with the app you want.</p><div class="code-area"><pre id="fdroid-command"><code>python3 -m shiraz fdroid sync --cache fdroid
python3 -m shiraz fdroid fetch org.example.app --cache fdroid --output app.apk</code></pre><button class="copy-button" type="button" aria-label="Copy F-Droid commands"><svg class="icon" data-icon="copy" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M8.25 7.5V6.10822C8.25 4.97324 9.09499 4.01015 10.2261 3.91627C10.5994 3.88529 10.9739 3.85858 11.3495 3.83619M15.75 18H18C19.2426 18 20.25 16.9926 20.25 15.75V6.10822C20.25 4.97324 19.405 4.01015 18.2739 3.91627C17.9006 3.88529 17.5261 3.85858 17.1505 3.83619M17.1505 3.83619C16.8672 2.91757 16.0116 2.25 15 2.25H13.5C12.4884 2.25 11.6328 2.91757 11.3495 3.83619M17.1505 3.83619C17.2152 4.04602 17.25 4.26894 17.25 4.5V5.25H11.25V4.5C11.25 4.26894 11.2848 4.04602 11.3495 3.83619"/><path d="M6.75 7.5H4.875C4.25368 7.5 3.75 8.00368 3.75 8.625V20.625C3.75 21.2463 4.25368 21.75 4.875 21.75H14.625C15.2463 21.75 15.75 21.2463 15.75 20.625V16.5C15.75 11.5294 11.7206 7.5 6.75 7.5Z"/><path d="M15.75 18.75V16.875C15.75 15.011 14.239 13.5 12.375 13.5H10.875C10.2537 13.5 9.75 12.9963 9.75 12.375V10.875C9.75 9.01104 8.23896 7.5 6.375 7.5H5.25"/></svg></button></div><a class="guide-link" href="https://github.com/spectre-oss/shiraz/blob/main/src/shiraz/fdroid.py">How the checks work <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><p class="copy-status" role="status" aria-live="polite"></p></div></details>
    </section>
  </main>
  <section class="poem-section" aria-label="A poem by Saadi">
    <svg class="poem-wave poem-wave-top" viewBox="0 0 240 24" preserveAspectRatio="none" aria-hidden="true"><path d="M0 12C20 0 20 0 40 12S60 24 80 12C100 0 100 0 120 12S140 24 160 12C180 0 180 0 200 12S220 24 240 12C260 0 260 0 280 12S300 24 320 12C340 0 340 0 360 12S380 24 400 12C420 0 420 0 440 12S460 24 480 12L480 0H0Z" /></svg>
    <svg class="poem-wave poem-wave-bottom" viewBox="0 0 240 24" preserveAspectRatio="none" aria-hidden="true"><path d="M0 12C20 0 20 0 40 12S60 24 80 12C100 0 100 0 120 12S140 24 160 12C180 0 180 0 200 12S220 24 240 12C260 0 260 0 280 12S300 24 320 12C340 0 340 0 360 12S380 24 400 12C420 0 420 0 440 12S460 24 480 12L480 0H0Z" /></svg>
    <img class="poem-background" src="https://images.unsplash.com/photo-1554058922-d51b58b707f5?auto=format&amp;fit=crop&amp;w=1280&amp;q=70" srcset="https://images.unsplash.com/photo-1554058922-d51b58b707f5?auto=format&amp;fit=crop&amp;w=640&amp;q=70 640w, https://images.unsplash.com/photo-1554058922-d51b58b707f5?auto=format&amp;fit=crop&amp;w=1280&amp;q=70 1280w, https://images.unsplash.com/photo-1554058922-d51b58b707f5?auto=format&amp;fit=crop&amp;w=1920&amp;q=70 1920w" sizes="100vw" alt="" loading="lazy" decoding="async" fetchpriority="low" />
    <div class="poem-content">
      <div class="poem-translation verse-list" onpointerleave={() => activeVerse = -1} role="group" aria-label="Saadi poem with line-by-line English translation">
        {#each verses as verse, index}
          <button class="verse-line" class:verse-translated={translatePoem || activeVerse === index} type="button" aria-label={verse.en} onpointerenter={(event) => { if (event.pointerType === 'mouse') activeVerse = index; }} onfocus={() => activeVerse = index} onblur={() => activeVerse = -1} onclick={() => activeVerse = activeVerse === index ? -1 : index}>
            <span class="verse-persian" lang="fa" dir="rtl" aria-hidden={translatePoem || activeVerse === index}>{verse.fa}</span>
            <span class="verse-english" lang="en" dir="ltr" aria-hidden={!(translatePoem || activeVerse === index)}>{verse.en}</span>
          </button>
        {/each}
      </div>
      <a class="poem-author" lang={translatePoem ? 'en' : 'fa'} href="https://ganjoor.net/saadi/mavaez/ghasides/sh28">{translatePoem ? 'Saadi · English rendering' : 'سعدی'}</a>
    </div>
    <a class="photo-credit" href="https://unsplash.com/photos/brown-concrete-building-close-up-photography-P_Ne56WEe5s">Vakil Mosque, Shiraz · Photo by Faruk Kaymak / Unsplash</a>
  </section>
  <Footer />
