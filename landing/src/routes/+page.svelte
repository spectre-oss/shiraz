<script>
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  const base = resolve('/').replace(/\/$/, '');
  import Builder from '#lib/Builder.svelte';
  import Footer from '#lib/Footer.svelte';
  import { initIcons } from '#lib/icons.js';
  import { initInteractions } from '#lib/interactions.js';
  import '../styles.css';
  const version = __SHIRAZ_VERSION__;
  let translatePoem = $state(false);
  let activeVerse = $state(-1);
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
  <title>Shiraz — Android™ apps on Linux and macOS.</title>
  <meta name="description" content="Supported Android™ apps on Linux, with an experimental Apple Silicon macOS port. Build an AppImage locally in your browser with Shiraz." />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:title" content="Shiraz — Your Android™ apps, at home on Linux and macOS" />
  <meta property="og:description" content="An experimental, open-source Android™ compatibility layer for Linux and macOS. Build an AppImage locally in your browser. No virtual machine." />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta property="og:image:type" content="image/png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="Shiraz. Your Android™ apps. A little more at home. Android™ apps on Linux and macOS." />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Shiraz — Android™ apps on Linux and macOS" />
  <meta name="twitter:description" content="Your Android™ apps. A little more at home. Explore Shiraz and package a supported APK locally in your browser." />
  <meta name="twitter:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta name="twitter:image:alt" content="Shiraz — Your Android™ apps. A little more at home." />
</svelte:head>

  <a class="skip-link" href="#main">Skip to content</a>
  <header id="top" class="site-header wrap">
    <a class="brand" href="#top" aria-label="Shiraz home"><img src="{base}/spectre.svg" width="42" height="42" alt="Spectre"><span>shiraz</span></a>
    <nav aria-label="Main navigation"><a href={resolve('/compatibility/')}>Compatibility</a><a href="#approach">How it works</a><a href="https://github.com/spectre-oss/shiraz">GitHub <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></nav>
  </header>
  <main id="main">
    <section class="hero" aria-labelledby="title">
      <div class="hero-atmosphere" aria-hidden="true"><span class="tonal-shape shape-orbit"></span><span class="tonal-shape shape-petal"></span><span class="tonal-shape shape-disc"></span></div>
      <div class="hero-content">
      <h1 id="title">Your Android<sup class="trademark">™</sup> apps.<br><span>A little more at home.</span></h1>
      <p class="intro">Bring supported Android™ apps to your Linux or macOS desktop.<br class="desktop-break"> No virtual machine. No full Android™ system.</p>
      <div class="actions"><a class="button primary" href="#builder">Open the builder <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a><a class="button secondary" href="https://github.com/spectre-oss/shiraz">Explore the source <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></div>
      <a class="hero-release" href={`https://github.com/spectre-oss/shiraz/releases/tag/v${version}`}><span class="release-dot" aria-hidden="true"></span>Latest release <strong>v{version}</strong><span class="release-status">Preview</span><svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a>
      <p class="local-note">Package your APK locally. Your files stay with you.</p>
      </div>
    </section>
    <section class="experimental-notice" aria-labelledby="experimental-title">
      <div class="experimental-inner"><h2 id="experimental-title">Still experimental.</h2><p>Shiraz is an experimental project. App compatibility is growing, and some features may be incomplete or unsupported.</p><p class="experimental-macos">The Apple Silicon macOS port is earlier still: an app’s native libraries load and talk to its Java code, but full apps are not yet supported on macOS.</p><p class="experimental-security">Run only APKs you trust. Apps run with your user’s permissions, without an Android™-style sandbox.</p></div>
    </section>
    <section class="shiraz-web" id="web" aria-labelledby="web-title">
      <div class="web-heading"><p class="eyebrow">Shiraz Web</p><h2 id="web-title">Built for<br><span>Linux mobile.</span></h2></div>
      <div class="web-description"><p>Your Android™ apps, a browser away. Shiraz Web runs supported APKs on a Linux host and streams the app to your browser over WebRTC.</p><p>Designed with Linux phones in mind, with touch and keyboard controls. The app runs on the host; your browser is the window.</p><a class="button secondary web-link" href="https://github.com/spectre-oss/shiraz/blob/main/docs/WEB.md">Set up Shiraz Web <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a><small>Experimental · App compatibility is still growing.</small></div>
    </section>
    <section class="architecture" id="approach" aria-labelledby="architecture-title">
      <header class="architecture-heading"><p class="eyebrow">Under the surface</p><h2 id="architecture-title">Android™ on the inside.<br><span>Linux or macOS underneath.</span></h2><p>Keep the app. Translate what it needs.<br>Scroll through the pieces that make it possible.</p></header>
      <div class="stack-story">
        <figure class="stack-figure" aria-labelledby="stack-caption">
          <figcaption id="stack-caption">The Shiraz stack, simplified.</figcaption>
          <ol class="stack-diagram">
            <li data-layer="0"><span class="layer-number">01</span><span>Your APK<small>App code · resources · native libraries</small></span><span class="layer-marker" aria-hidden="true"><svg class="icon" data-icon="down" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M19.5 13.5 12 21m0 0-7.5-7.5"/><path d="M12 21V3"/></svg></span></li>
            <li data-layer="1"><span class="layer-number">02</span><span>ART<small>Android™ Runtime</small></span><span class="layer-marker" aria-hidden="true"><svg class="icon" data-icon="down" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M19.5 13.5 12 21m0 0-7.5-7.5"/><path d="M12 21V3"/></svg></span></li>
            <li data-layer="2"><span class="layer-number">03</span><span>Android™ API layer<small>Framework · resources · services</small></span><span class="layer-marker" aria-hidden="true"><svg class="icon" data-icon="down" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M19.5 13.5 12 21m0 0-7.5-7.5"/><path d="M12 21V3"/></svg></span></li>
            <li data-layer="3"><span class="layer-number">04</span><span>Native bridges<small>JNI · Bionic compatibility · SDL · EGL/GLES</small></span><span class="layer-marker" aria-hidden="true"><svg class="icon" data-icon="down" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M19.5 13.5 12 21m0 0-7.5-7.5"/><path d="M12 21V3"/></svg></span></li>
            <li data-layer="4"><span class="layer-number">05</span><span>Your Linux or macOS desktop<small>Windows · graphics · input · files</small></span><span class="layer-marker" aria-hidden="true"><svg class="icon" data-icon="turn" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m16.49 12 3.75 3.75m0 0-3.75 3.75m3.75-3.75H3.74V4.499"/></svg></span></li>
          </ol>
        </figure>
        <div class="stack-explanations">
          <article class="stack-step" data-step="0"><span class="step-index">01 / The app</span><h3>Start with an APK.</h3><p>An Android™ package carries the app’s code, assets, and sometimes native libraries. Shiraz starts with that original package.</p><a href="https://github.com/spectre-oss/shiraz/tree/main/src/shiraz">Package handling <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" data-step="1"><span class="step-index">02 / Execution</span><h3>ART runs the code.</h3><p>ART is Android™’s runtime. Its standalone host build for Linux and macOS executes the app’s DEX bytecode, with ahead-of-time compilation where supported.</p><a href="https://github.com/spectre-oss/shiraz/blob/main/scripts/bootstrap_runtime.py">Runtime setup <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" data-step="2"><span class="step-index">03 / Compatibility</span><h3>Speak the app’s language.</h3><p>Apps expect Android™ APIs for views, resources, and services. Shiraz implements a growing subset of those APIs, so supported app code can keep making familiar calls.</p><a href="https://github.com/spectre-oss/shiraz/tree/main/java/android">Java framework code <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" data-step="3"><span class="step-index">04 / Translation</span><h3>Connect it to the host.</h3><p>JNI connects Java code to native C/C++ bridges. Bionic compatibility helps Android™ native libraries run; SDL and EGL/GLES provide window, input, and graphics paths. On Apple Silicon Macs, a guest ELF loader and bionic ABI adapters do the same job.</p><a href="https://github.com/spectre-oss/shiraz/tree/main/native">Native bridge code <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
          <article class="stack-step" data-step="4"><span class="step-index">05 / At home</span><h3>A window on your desktop.</h3><p>Linux or macOS supplies the host environment. The app gets a desktop window and host input, without booting an Android™ image or running a virtual machine.</p><a href="https://github.com/spectre-oss/shiraz/blob/main/native/presentation.cpp">Window presentation <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></article>
        </div>
      </div>

    </section>
    <section class="comparison" id="compare" aria-labelledby="comparison-title">
      <header class="comparison-heading"><p class="eyebrow">Different paths to Android™ apps</p><h2 id="comparison-title">Choose the approach<br><span>that fits your desktop.</span></h2><p>A translation layer, a container, or a virtual device.<br>Each makes a different tradeoff.</p></header>
      <p class="table-hint" id="comparison-hint">Swipe or scroll sideways to see the full comparison.</p>
      <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
      <div class="comparison-scroll" role="region" aria-labelledby="comparison-title" aria-describedby="comparison-hint" tabindex="0">
        <table class="comparison-table"><caption class="visually-hidden">Android™ app execution approaches compared</caption><thead><tr><th scope="col">Project</th><th scope="col">Approach</th><th scope="col">Android™ environment</th><th scope="col">What to keep in mind</th></tr></thead><tbody>
          <tr class="shiraz-row"><th scope="row"><a href="https://github.com/spectre-oss/shiraz">Shiraz</a><small>This project</small></th><td>API translation<small>ART + native Linux and macOS bridges</small></td><td>No full Android™ image<small>SDL / EGL / GLES</small></td><td>AppImage packaging and browser access on Linux; experimental Apple Silicon macOS port. Experimental API coverage; no Android™-style app sandbox.</td></tr>
          <tr><th scope="row"><a href="https://docs.waydro.id/">Waydroid <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Container<small>Shares the Linux kernel</small></td><td>Full Android™ system<small>LineageOS-based image</small></td><td>A full Android™ environment integrated with Linux. Requires container setup and suitable host support.</td></tr>
          <tr><th scope="row"><a href="https://github.com/denialwm/droidloom">Droidloom <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Namespaced userspace<small>Shares the Linux kernel</small></td><td>Full Android™ userspace<small>ART · Binder · Zygote · system_server</small></td><td>Separate Wayland windows with GPU acceleration. Includes Teto for ARM64 apps on x86_64; requires Binder and compatible Wayland protocols. Early preview.</td></tr>
          <tr><th scope="row"><a href="https://gitlab.com/android_translation_layer/android_translation_layer">ATL <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Android™ Translation Layer</small></th><td>API translation<small>Shiraz’s architectural starting point</small></td><td>Android™ APIs on Linux<small>App-focused compatibility</small></td><td>A related translation approach. Check its project documentation for supported apps and build requirements.</td></tr>
          <tr><th scope="row"><a href="https://developer.android.com/studio/run/emulator">Android™ Emulator <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a></th><td>Virtual device<small>Simulated Android™ hardware</small></td><td>Android™ system image<small>Configured through an AVD</small></td><td>Designed for development and testing across device profiles, Android™ versions, and simulated sensors.</td></tr>
          <tr><th scope="row"><a href="https://github.com/anbox/anbox">Anbox <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><small>Archived project</small></th><td>Container<small>Shares the Linux kernel</small></td><td>Full Android™ system</td><td>A historical container approach. The original repository is archived; distinct from Anbox Cloud.</td></tr>
        </tbody></table>
      </div>
      <p class="comparison-note">Architecture comparison, not a performance ranking. Compatibility depends on the app, device, and configuration. Project names link to their own documentation.</p>
    </section>
      <section class="packaging-path" aria-label="APK plus Shiraz runtime produces an AppImage"><p>APK <span aria-hidden="true"><svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></span> Shiraz runtime <span aria-hidden="true"><svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></span> AppImage</p></section>
    <Builder />
    <section class="project-info" aria-label="About Shiraz">
      <div class="principles"><span>Linux &amp; macOS</span><span>GTK-free</span><span>Open source · GPLv3</span></div>
      <details class="source-build"><summary>Build from source<span class="disclosure-icon" aria-hidden="true"><svg class="icon" data-icon="plus" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 12h14"/><path d="M12 5v14"/></svg></span></summary><div class="build-content"><p>With the runtime dependencies prepared:</p><div class="code-area"><pre id="build-command"><code>make build
make appimage APK=/path/to/app.apk</code></pre><button class="copy-button" type="button" aria-label="Copy build commands"><svg class="icon" data-icon="copy" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M8.25 7.5V6.10822C8.25 4.97324 9.09499 4.01015 10.2261 3.91627C10.5994 3.88529 10.9739 3.85858 11.3495 3.83619M15.75 18H18C19.2426 18 20.25 16.9926 20.25 15.75V6.10822C20.25 4.97324 19.405 4.01015 18.2739 3.91627C17.9006 3.88529 17.5261 3.85858 17.1505 3.83619M17.1505 3.83619C16.8672 2.91757 16.0116 2.25 15 2.25H13.5C12.4884 2.25 11.6328 2.91757 11.3495 3.83619M17.1505 3.83619C17.2152 4.04602 17.25 4.26894 17.25 4.5V5.25H11.25V4.5C11.25 4.26894 11.2848 4.04602 11.3495 3.83619"/><path d="M6.75 7.5H4.875C4.25368 7.5 3.75 8.00368 3.75 8.625V20.625C3.75 21.2463 4.25368 21.75 4.875 21.75H14.625C15.2463 21.75 15.75 21.2463 15.75 20.625V16.5C15.75 11.5294 11.7206 7.5 6.75 7.5Z"/><path d="M15.75 18.75V16.875C15.75 15.011 14.239 13.5 12.375 13.5H10.875C10.2537 13.5 9.75 12.9963 9.75 12.375V10.875C9.75 9.01104 8.23896 7.5 6.375 7.5H5.25"/></svg></button></div><a class="guide-link" href="https://github.com/spectre-oss/shiraz/blob/main/docs/DEVELOPMENT.md">Development guide <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><p class="copy-status" role="status" aria-live="polite"></p></div></details>
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
