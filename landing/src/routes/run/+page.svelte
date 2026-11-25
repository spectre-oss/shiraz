<script>
  // SPDX-License-Identifier: GPL-3.0-only
  // Client-side Android APK runner in the browser: Shiraz aarch64 runtime on pithings/unkernel.
  // Zero telemetry, zero external runtime dependencies; all APK bytes stay on this device.
  import { onMount } from 'svelte';
  import { browser } from '$app/environment';
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import SiteNav from '#lib/SiteNav.svelte';
  import { initIcons } from '#lib/icons.js';
  import { ROBOTS_INDEX, runFaqs, runJsonLd, serializeJsonLd } from '#lib/seo.js';
  import {
    checkBrowserSupport,
    clearPersistentStorage,
    inspectApkForBrowser,
    keyboardEventToLiveCommands,
    startShirazBrowserSession
  } from '#lib/unkernel-runner.js';
  import '../../styles.css';

  const base = resolve('/').replace(/\/$/, '');
  const home = `${base}/`;

  const LOAD_STEPS = [
    { id: 'inspect', label: 'Inspect APK & ABIs' },
    { id: 'kernel', label: 'Boot Linux 7.3 kernel' },
    { id: 'mount', label: 'Mount Shiraz runtime' },
    { id: 'art', label: 'Start ARM64 ART & paint' }
  ];

  let canvasEl = $state(null);
  let phoneHostEl = $state(null);
  let phoneFrameEl = $state(null);
  let apkInput = $state(null);
  let localRuntimeInput = $state(null);

  let supportState = $state({ checked: false, ok: true, problems: [] });
  let releaseManifest = $state(null);
  let runtimeVersion = $state(__SHIRAZ_VERSION__);
  let runtimeBadge = $state('Checking runtime…');
  let runtimeDetail = $state('Looking for the published aarch64 browser runtime.');

  let apkFile = $state(null);
  let apkInfo = $state(null);
  let localRuntimeFile = $state(null);
  let dragging = $state(false);

  let artMode = $state('interpreter');
  let memoryMiB = $state(768);
  let orientation = $state('portrait');
  let persistData = $state(true);
  let muted = $state(false);

  let phase = $state('idle'); // idle | inspecting | booting | running | stopped | error
  let statusMessage = $state('Drop an Android™ APK or pick a file to run it locally in your browser.');
  let errorMessage = $state('');
  let frameCount = $state(0);
  let screenWidth = $state(360);
  let screenHeight = $state(640);
  let logText = $state('');
  let logsOpen = $state(false);
  let clearingData = $state(false);

  // Structured loading progress shown inside the phone viewport until frame #1 paints.
  let loadStep = $state('idle'); // idle | inspect | kernel | mount | art
  let loadPercent = $state(0);
  let loadDetail = $state('');
  let bootElapsedSec = $state(0);

  // Pop-out window (Document Picture-in-Picture with popup fallback) & PWA install state.
  function detectInstalledMode() {
    if (!browser) return false;
    return Boolean(
      window.matchMedia('(display-mode: standalone)').matches ||
        window.matchMedia('(display-mode: window-controls-overlay)').matches ||
        navigator.standalone === true
    );
  }
  function detectStandaloneMode() {
    if (!browser) return false;
    return new URLSearchParams(window.location.search).get('standalone') === '1' || detectInstalledMode();
  }
  let poppedOut = $state(false);
  let isStandalone = $state(detectStandaloneMode());
  let isInstalledPwa = $state(detectInstalledMode());
  let deferredInstallPrompt = $state(null);
  let installBusy = $state(false);

  let session = null;
  let pipWindow = null;
  let activePointerId = null;
  let lastSentMove = null;
  let pendingMove = null;
  let lastMoveSentAt = 0;
  let pendingFrame = null;
  let rafId = 0;
  let activeRunId = 0;
  let bootTimerId = 0;
  let bootStartedAt = 0;
  const MOVE_COALESCE_MS = 33;

  function appendLog(chunk) {
    logText = (logText + chunk).slice(-32000);
  }

  function formatSize(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
    if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MiB`;
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KiB`;
    return `${bytes} B`;
  }

  function formatElapsed(sec) {
    const mins = Math.floor(sec / 60);
    const rem = sec % 60;
    return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
  }

  function stepState(stepId) {
    const currentIdx = LOAD_STEPS.findIndex(s => s.id === loadStep);
    const idx = LOAD_STEPS.findIndex(s => s.id === stepId);
    if (currentIdx === -1 || idx === -1) return 'pending';
    if (idx < currentIdx) return 'done';
    if (idx === currentIdx) return 'active';
    return 'pending';
  }

  function startBootTimer() {
    stopBootTimer();
    bootStartedAt = Date.now();
    bootElapsedSec = 0;
    bootTimerId = window.setInterval(() => {
      bootElapsedSec = Math.max(0, Math.floor((Date.now() - bootStartedAt) / 1000));
    }, 500);
  }

  function stopBootTimer() {
    if (bootTimerId) {
      window.clearInterval(bootTimerId);
      bootTimerId = 0;
    }
  }

  function updateLoadProgress(step, detail, percent) {
    if (step) loadStep = step;
    if (detail) loadDetail = detail;
    if (typeof percent === 'number' && Number.isFinite(percent)) {
      loadPercent = Math.max(loadPercent, Math.min(100, Math.round(percent)));
    }
  }

  function paintFrame() {
    rafId = 0;
    const frame = pendingFrame;
    pendingFrame = null;
    if (!frame || !canvasEl) return;
    if (canvasEl.width !== frame.width || canvasEl.height !== frame.height) {
      canvasEl.width = frame.width;
      canvasEl.height = frame.height;
      screenWidth = frame.width;
      screenHeight = frame.height;
    }
    const ctx = canvasEl.getContext('2d', { alpha: false });
    if (!ctx) return;
    ctx.putImageData(new ImageData(frame.data, frame.width, frame.height), 0, 0);
  }

  function queueFrame(frame) {
    pendingFrame = frame;
    frameCount = frame.seq;
    if (phase === 'booting' || phase === 'inspecting') {
      phase = 'running';
      loadPercent = 100;
      stopBootTimer();
      statusMessage = `${apkInfo?.label || apkInfo?.package || 'App'} is running locally in your browser.`;
    }
    if (!rafId) rafId = requestAnimationFrame(paintFrame);
  }

  async function loadPublishedRuntime() {
    try {
      const response = await fetch(`${base}/release.json`, { cache: 'no-store' });
      if (!response.ok) throw new Error('No published release manifest.');
      const data = await response.json();
      if (!/^\d+\.\d+\.\d+$/.test(data.version || '')) throw new Error('Invalid release manifest.');
      runtimeVersion = data.version;
      if (data.browser_runtime?.file) {
        releaseManifest = {
          kind: 'http-image',
          url: `${base}/${data.browser_runtime.file}`,
          format: data.browser_runtime.format || 'erofs',
          size: data.browser_runtime.size,
          sha256: data.browser_runtime.sha256,
          version: data.version
        };
        runtimeBadge = `v${data.version} · aarch64 ${releaseManifest.format.toUpperCase()}`;
        runtimeDetail = `${formatSize(releaseManifest.size)} lazy-streamed over HTTP Range requests (only used blocks download).`;
        return;
      }
      const arm = data.architectures?.aarch64;
      if (arm?.base_file) {
        releaseManifest = {
          kind: 'http-appimage',
          url: `${base}/${arm.base_file}`,
          size: arm.base_size,
          sha256: arm.base_sha256,
          version: data.version
        };
        runtimeBadge = `v${data.version} · aarch64 AppImage base`;
        runtimeDetail = `${formatSize(arm.base_size)} fallback base · verified by SHA-256 before launch.`;
        return;
      }
      throw new Error('No aarch64 runtime published in release.json.');
    } catch {
      releaseManifest = null;
      runtimeBadge = 'Local runtime required';
      runtimeDetail =
        'No published aarch64 browser runtime was found at this origin. Select a local Shiraz runtime image (.erofs, .img) or aarch64 base AppImage below.';
    }
  }

  function resolveRuntimeSource() {
    if (localRuntimeFile) {
      const name = localRuntimeFile.name.toLowerCase();
      if (name.endsWith('.erofs')) {
        return { kind: 'local-image', file: localRuntimeFile, format: 'erofs' };
      }
      if (name.endsWith('.img') || name.endsWith('.ext4')) {
        return { kind: 'local-image', file: localRuntimeFile, format: 'ext4' };
      }
      if (name.endsWith('.appimage')) {
        return { kind: 'local-appimage', file: localRuntimeFile };
      }
      throw new Error('Select a .erofs, .img/.ext4 runtime disk image, or an aarch64 .AppImage.');
    }
    if (releaseManifest) return releaseManifest;
    throw new Error(
      'No published aarch64 runtime is available here yet. Open "Runtime image override" and choose a local Shiraz runtime (.erofs, .img, or aarch64 .AppImage).'
    );
  }

  function handleApkSelected(file) {
    if (!file) return;
    apkFile = file;
    apkInfo = null;
    errorMessage = '';
    statusMessage = `Selected ${file.name} (${formatSize(file.size)}). Click Run APK to launch locally.`;
  }

  function onApkInputChange(event) {
    const file = event.currentTarget.files?.[0] || null;
    handleApkSelected(file);
  }

  function onRuntimeInputChange(event) {
    const file = event.currentTarget.files?.[0] || null;
    localRuntimeFile = file;
    if (file) {
      const match = /(\d+\.\d+\.\d+)/.exec(file.name);
      if (match) runtimeVersion = match[1];
    }
  }

  function onDrop(event) {
    event.preventDefault();
    dragging = false;
    const file = event.dataTransfer?.files?.[0];
    if (!file) return;
    if (file.name.toLowerCase().endsWith('.apk')) {
      handleApkSelected(file);
    } else {
      errorMessage = 'Please drop an Android .apk file.';
      phase = 'error';
    }
  }

  async function stopSession(quiet = false) {
    activeRunId += 1;
    stopBootTimer();
    const current = session;
    session = null;
    activePointerId = null;
    if (current) {
      await current.stop();
    }
    if (!quiet) {
      phase = 'stopped';
      loadStep = 'idle';
      loadPercent = 0;
      loadDetail = '';
      statusMessage = 'Session stopped. Drop another APK or click Run APK to start again.';
    }
  }

  async function launchApk() {
    if (!apkFile) {
      errorMessage = 'Select or drop an Android .apk file first.';
      phase = 'error';
      return;
    }
    if (supportState.checked && !supportState.ok) {
      errorMessage = `This browser lacks required WebAssembly features: ${supportState.problems.join('; ')}`;
      phase = 'error';
      return;
    }

    await stopSession(true);
    const runId = ++activeRunId;
    errorMessage = '';
    logText = '';
    frameCount = 0;
    loadPercent = 0;
    loadStep = 'inspect';
    loadDetail = 'Inspecting Android manifest and native ABIs in Go WebAssembly…';

    const targetW = orientation === 'landscape' ? 640 : 360;
    const targetH = orientation === 'landscape' ? 360 : 640;
    screenWidth = targetW;
    screenHeight = targetH;

    try {
      const runtimeSource = resolveRuntimeSource();
      phase = 'inspecting';
      startBootTimer();
      updateLoadProgress('inspect', 'Inspecting Android manifest and arm64-v8a libraries…', 10);
      statusMessage = 'Inspecting APK manifest and native ABIs locally in WebAssembly…';
      const apkBytes = new Uint8Array(await apkFile.arrayBuffer());
      const prepared = await inspectApkForBrowser(base, apkBytes, runtimeVersion);
      if (runId !== activeRunId) return;
      apkInfo = prepared;
      if (pipWindow && !pipWindow.closed) {
        pipWindow.document.title = `${prepared.label || prepared.package} — Shiraz`;
      }
      updateLoadProgress('inspect', `Verified ${prepared.label || prepared.package}`, 18);
      appendLog(
        `[shiraz] package=${prepared.package} activity=${prepared.activity} native=${prepared.native} (${prepared.native_libs.length} arm64-v8a libs)\n`
      );

      phase = 'booting';
      const started = await startShirazBrowserSession({
        base,
        runtimeSource,
        apkBytes,
        prepared,
        width: targetW,
        height: targetH,
        artMode,
        memoryMiB: Number(memoryMiB),
        persistData,
        enableAudio: !muted,
        onStage: msg => {
          if (runId === activeRunId && phase === 'booting') statusMessage = msg;
        },
        onProgress: progress => {
          if (runId !== activeRunId) return;
          updateLoadProgress(progress.step, progress.detail, progress.percent);
        },
        onFrame: frame => {
          if (runId === activeRunId) queueFrame(frame);
        },
        onLog: chunk => {
          if (runId === activeRunId) appendLog(chunk);
        },
        onExit: info => {
          if (runId !== activeRunId) return;
          stopBootTimer();
          session = null;
          if (info.code && info.code !== 0) {
            phase = 'error';
            errorMessage = `The app exited with code ${info.code}. Open Runtime console to see the ART output.`;
            logsOpen = true;
          } else {
            phase = 'stopped';
            statusMessage = 'App exited cleanly.';
          }
        }
      });

      if (runId !== activeRunId) {
        await started.stop();
        return;
      }
      session = started;
      session.setMuted(muted);
      session.resumeAudio();
    } catch (error) {
      if (runId !== activeRunId) return;
      stopBootTimer();
      phase = 'error';
      loadStep = 'idle';
      errorMessage = error?.message || String(error);
      statusMessage = 'Launch did not complete.';
      appendLog(`\n[error] ${errorMessage}\n`);
    }
  }

  function mapCanvasCoords(event) {
    if (!canvasEl) return null;
    const rect = canvasEl.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return null;
    const x = Math.max(0, Math.min(screenWidth - 1, Math.round(((event.clientX - rect.left) * screenWidth) / rect.width)));
    const y = Math.max(0, Math.min(screenHeight - 1, Math.round(((event.clientY - rect.top) * screenHeight) / rect.height)));
    return { x, y };
  }

  function onCanvasPointerDown(event) {
    if (!session || frameCount === 0 || event.button !== 0) return;
    session.resumeAudio();
    canvasEl?.focus({ preventScroll: true });
    const pt = mapCanvasCoords(event);
    if (!pt) return;
    activePointerId = event.pointerId;
    lastSentMove = pt;
    pendingMove = null;
    lastMoveSentAt = performance.now();
    canvasEl?.setPointerCapture?.(event.pointerId);
    session.sendCommand(`down ${pt.x} ${pt.y}`);
  }

  function onCanvasPointerMove(event) {
    if (!session || frameCount === 0 || activePointerId !== event.pointerId) return;
    const pt = mapCanvasCoords(event);
    if (!pt) return;
    if (lastSentMove && pt.x === lastSentMove.x && pt.y === lastSentMove.y) return;
    const now = performance.now();
    if (now - lastMoveSentAt < MOVE_COALESCE_MS) {
      pendingMove = pt;
      return;
    }
    pendingMove = null;
    lastSentMove = pt;
    lastMoveSentAt = now;
    session.sendCommand(`move ${pt.x} ${pt.y}`);
  }

  function onCanvasPointerUp(event) {
    if (!session || activePointerId !== event.pointerId) return;
    activePointerId = null;
    const pt = mapCanvasCoords(event);
    if (
      pendingMove &&
      (!lastSentMove || pendingMove.x !== lastSentMove.x || pendingMove.y !== lastSentMove.y) &&
      (!pt || pendingMove.x !== pt.x || pendingMove.y !== pt.y)
    ) {
      session.sendCommand(`move ${pendingMove.x} ${pendingMove.y}`);
    }
    pendingMove = null;
    lastSentMove = null;
    if (pt) session.sendCommand(`up ${pt.x} ${pt.y}`);
  }

  function onCanvasWheel(event) {
    if (!session || frameCount === 0) return;
    const pt = mapCanvasCoords(event);
    if (!pt) return;
    event.preventDefault();
    const dx = Math.max(-12, Math.min(12, -event.deltaX / 40));
    const dy = Math.max(-12, Math.min(12, -event.deltaY / 40));
    session.sendCommand(`wheel ${pt.x} ${pt.y} ${dx.toFixed(2)} ${dy.toFixed(2)}`);
  }

  function onCanvasKey(event, isDown) {
    if (!session || frameCount === 0) return;
    const cmds = keyboardEventToLiveCommands(event, isDown);
    if (!cmds.length) return;
    event.preventDefault();
    for (const cmd of cmds) session.sendCommand(cmd);
  }

  function sendBack() {
    if (!session) return;
    session.sendCommand('keydown "Escape"');
    session.sendCommand('keyup "Escape"');
  }

  function toggleRotate() {
    orientation = orientation === 'portrait' ? 'landscape' : 'portrait';
    const nextW = orientation === 'landscape' ? 640 : 360;
    const nextH = orientation === 'landscape' ? 360 : 640;
    if (session) {
      session.sendCommand(`resize ${nextW} ${nextH}`);
    } else {
      screenWidth = nextW;
      screenHeight = nextH;
    }
  }

  function toggleMute() {
    muted = !muted;
    session?.setMuted(muted);
  }

  function saveScreenshot() {
    if (!canvasEl || frameCount === 0) return;
    const link = document.createElement('a');
    link.href = canvasEl.toDataURL('image/png');
    link.download = `${apkInfo?.package || 'shiraz-app'}-browser.png`;
    link.click();
  }

  function copyDocumentStyles(targetDoc) {
    const scheme = targetDoc.createElement('meta');
    scheme.name = 'color-scheme';
    scheme.content = 'dark';
    targetDoc.head.append(scheme);
    for (const node of document.querySelectorAll('link[rel="stylesheet"], style')) {
      if (node.tagName === 'LINK') {
        const link = targetDoc.createElement('link');
        link.rel = 'stylesheet';
        link.href = node.href;
        targetDoc.head.append(link);
      } else {
        const style = targetDoc.createElement('style');
        style.textContent = node.textContent;
        targetDoc.head.append(style);
      }
    }
  }

  function dockBack() {
    if (phoneHostEl && phoneFrameEl && phoneFrameEl.parentNode !== phoneHostEl) {
      phoneHostEl.append(phoneFrameEl);
    }
    const win = pipWindow;
    pipWindow = null;
    poppedOut = false;
    if (win && !win.closed) {
      try {
        win.close();
      } catch {
        // Already closing.
      }
    }
  }

  function openStandalonePopup() {
    const w = Math.max(440, screenWidth + 80);
    const h = Math.max(760, screenHeight + 170);
    const popup = window.open(
      `${base}/run/?standalone=1`,
      'shiraz-browser-runner',
      `popup=yes,width=${w},height=${h},resizable=yes`
    );
    if (!popup) {
      statusMessage = 'Pop-out window was blocked by the browser. Allow pop-ups or open the standalone app view.';
      return;
    }
    popup.focus?.();
  }

  async function togglePopout() {
    if (poppedOut) {
      dockBack();
      return;
    }
    // Prefer Document Picture-in-Picture when available: moves the live app canvas
    // into a floating always-on-top window without restarting unkernel or losing state.
    const canPip =
      typeof window !== 'undefined' &&
      'documentPictureInPicture' in window &&
      typeof window.documentPictureInPicture?.requestWindow === 'function' &&
      phoneFrameEl;
    if (canPip) {
      try {
        const width = Math.min(Math.max(screenWidth + 56, 420), 760);
        const height = Math.min(Math.max(screenHeight + 190, 620), 880);
        const pip = await window.documentPictureInPicture.requestWindow({ width, height });
        pip.document.title = apkInfo?.label ? `${apkInfo.label} — Shiraz` : 'Shiraz — Android™ App';
        pip.document.documentElement.classList.add('is-pip-window');
        copyDocumentStyles(pip.document);
        pip.document.body.classList.add('pip-body');
        pip.document.body.append(phoneFrameEl);
        pipWindow = pip;
        poppedOut = true;
        pip.addEventListener('pagehide', dockBack, { once: true });
        return;
      } catch {
        // Fall through to standard standalone popup window.
      }
    }
    openStandalonePopup();
  }

  async function installWebApp() {
    if (!deferredInstallPrompt || installBusy) return;
    installBusy = true;
    try {
      await deferredInstallPrompt.prompt();
      const choice = await deferredInstallPrompt.userChoice;
      if (choice?.outcome === 'accepted') {
        deferredInstallPrompt = null;
        statusMessage = 'Shiraz Browser Runner installed as a web app.';
      }
    } catch {
      // User dismissed or browser declined prompt.
    } finally {
      installBusy = false;
    }
  }

  async function resetStorage() {
    clearingData = true;
    try {
      await stopSession(true);
      await clearPersistentStorage();
      statusMessage = 'Cleared saved IndexedDB app storage for this browser.';
      phase = 'idle';
    } catch (error) {
      errorMessage = error?.message || String(error);
      phase = 'error';
    } finally {
      clearingData = false;
    }
  }

  onMount(() => {
    const stopIcons = initIcons();
    loadPublishedRuntime();

    // Detect standalone pop-out (?standalone=1) or installed PWA display mode.
    const standaloneParam = new URLSearchParams(window.location.search).get('standalone') === '1';
    const displayMq = window.matchMedia('(display-mode: standalone)');
    const wcoMq = window.matchMedia('(display-mode: window-controls-overlay)');
    const syncDisplayMode = () => {
      const installed = Boolean(displayMq.matches || wcoMq.matches || navigator.standalone === true);
      isInstalledPwa = installed;
      isStandalone = standaloneParam || installed;
    };
    syncDisplayMode();
    displayMq.addEventListener?.('change', syncDisplayMode);
    wcoMq.addEventListener?.('change', syncDisplayMode);

    // Capture browser PWA installability prompt (no telemetry, strictly local browser API).
    const onBeforeInstall = event => {
      event.preventDefault();
      deferredInstallPrompt = event;
    };
    const onAppInstalled = () => {
      deferredInstallPrompt = null;
      isInstalledPwa = true;
      isStandalone = true;
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    window.addEventListener('appinstalled', onAppInstalled);

    // Register minimal zero-telemetry same-origin service worker for PWA installability.
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register(`${base}/sw.js`, { scope: `${base}/` }).catch(() => {});
    }

    // Support opening .apk files directly via installed PWA OS file_handlers.
    if ('launchQueue' in window && typeof window.launchQueue?.setConsumer === 'function') {
      window.launchQueue.setConsumer(async launchParams => {
        const handle = launchParams?.files?.[0];
        if (!handle) return;
        try {
          const file = await handle.getFile();
          if (file && file.name.toLowerCase().endsWith('.apk')) {
            handleApkSelected(file);
          }
        } catch {
          // Ignore file-handle permission errors.
        }
      });
    }

    checkBrowserSupport()
      .then(result => {
        supportState = { checked: true, ok: result.ok, problems: result.problems || [] };
      })
      .catch(error => {
        supportState = { checked: true, ok: false, problems: [error?.message || String(error)] };
      });

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      stopBootTimer();
      dockBack();
      displayMq.removeEventListener?.('change', syncDisplayMode);
      wcoMq.removeEventListener?.('change', syncDisplayMode);
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
      window.removeEventListener('appinstalled', onAppInstalled);
      stopIcons();
      stopSession(true);
    };
  });
</script>

<svelte:head>
  <title>Run Android™ APKs in your browser — Shiraz + unkernel</title>
  <meta
    name="description"
    content="Drop a supported Android™ APK and run it 100% client-side in your browser tab using Shiraz aarch64 and pithings/unkernel Linux on WebAssembly. Zero backend compute, zero telemetry."
  />
  <meta name="robots" content={ROBOTS_INDEX} />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/run/" />
  <meta property="og:type" content="website" />
  <meta property="og:locale" content="en_US" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:title" content="Run Android™ APKs in your browser — Shiraz + unkernel" />
  <meta
    property="og:description"
    content="Run supported Android™ APKs directly inside your browser tab with zero backend servers and zero telemetry — Shiraz standalone ARM64 ART running inside pithings/unkernel."
  />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/run/" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="Run Android™ APKs in your browser — Shiraz + unkernel" />
  <meta
    name="twitter:description"
    content="100% client-side Android™ APK runner in WebAssembly powered by Shiraz + pithings/unkernel. Zero telemetry."
  />
  <meta name="twitter:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  {@html serializeJsonLd(runJsonLd(__SHIRAZ_VERSION__))}
</svelte:head>

<a class="skip-link" href="#main">Skip to content</a>

{#if isStandalone}
  <header id="top" class="standalone-header wrap">
    <div class="standalone-brand">
      <img src="{home}spectre.svg" width="32" height="32" alt="Spectre" />
      <h1 class="standalone-title">{apkInfo?.label || 'Shiraz Browser Runner'}</h1>
    </div>
    <div class="standalone-actions">
      {#if deferredInstallPrompt && !isInstalledPwa}
        <button
          type="button"
          class="control-btn control-install"
          onclick={installWebApp}
          disabled={installBusy}
        >
          Install web app
        </button>
      {/if}
      <a class="control-btn standalone-link" href="{base}/run/">Full page</a>
    </div>
  </header>
{:else}
  <header id="top" class="compat-header wrap">
    <a class="brand" href={home} aria-label="Shiraz home">
      <img src="{home}spectre.svg" width="42" height="42" alt="Spectre" />
      <span>shiraz</span>
    </a>
    <SiteNav />
  </header>
{/if}

<main id="main" class="wrap runner-page" class:is-standalone={isStandalone}>
  {#if !isStandalone}
    <nav class="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        <li><a href={home}>Home</a></li>
        <li><span aria-current="page">Run in browser</span></li>
      </ol>
    </nav>

    <section class="runner-hero" aria-labelledby="runner-title">
      <p class="eyebrow">Experimental · 100% client-side · Zero backend · Zero telemetry</p>
      <h1 id="runner-title">Run an APK.<br /><span>Right in this tab.</span></h1>
      <p class="runner-intro">
        Drop a supported Android™ APK below. Shiraz inspects it in Go WebAssembly and executes it on standalone ARM64 ART
        inside <a class="inline-accent" href="https://github.com/pithings/unkernel">unkernel</a> — a real Linux 7.3 kernel
        compiled to WebAssembly. Your APK never leaves your device, with zero tracking or telemetry.
      </p>
    </section>
  {/if}

  {#if supportState.checked && !supportState.ok}
    <div class="runner-alert" role="alert">
      <strong>This browser is missing required WebAssembly support.</strong>
      <p>{supportState.problems.join(' ')}</p>
      <p>
        Client-side execution via unkernel requires WebAssembly JSPI: Chrome or Edge 137+, Firefox 153+, or Safari 27+.
      </p>
    </div>
  {/if}

  <section class="runner-workspace" aria-label="In-browser Android runner">
    <div class="runner-stage-col">
      <!-- Dedicated host wrapper with no conditional siblings so live DOM transfer to/from Document PiP stays clean. -->
      <div class="phone-host" bind:this={phoneHostEl} hidden={poppedOut}>
        <div
          bind:this={phoneFrameEl}
          class="phone-frame"
          class:is-landscape={orientation === 'landscape'}
          class:is-dragging={dragging}
          role="region"
          aria-label="Android app screen"
          ondragover={event => {
            event.preventDefault();
            dragging = true;
          }}
          ondragleave={() => {
            dragging = false;
          }}
          ondrop={onDrop}
        >
          <div class="phone-topbar">
            <span class="phone-pill" data-phase={phase}>
              <i aria-hidden="true"></i>
              {#if phase === 'running'}
                Live · {frameCount} {frameCount === 1 ? 'frame' : 'frames'}
              {:else if phase === 'booting'}
                Starting · {loadPercent}%
              {:else if phase === 'inspecting'}
                Inspecting…
              {:else if phase === 'error'}
                Error
              {:else if phase === 'stopped'}
                Stopped
              {:else}
                Ready
              {/if}
            </span>
            <div class="phone-topbar-right">
              <span class="phone-dims">{screenWidth}×{screenHeight}</span>
              <button
                type="button"
                class="phone-popout-icon"
                onclick={togglePopout}
                aria-label={poppedOut ? 'Dock app back into page' : 'Pop out app into floating window'}
                title={poppedOut ? 'Dock back' : 'Pop out window'}
              >
                {#if poppedOut}
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M9 14 4 9l5-5" /><path d="M20 20v-7a4 4 0 0 0-4-4H4" />
                  </svg>
                {:else}
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M15 3h6v6" /><path d="M10 14 21 3" /><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                  </svg>
                {/if}
              </button>
            </div>
          </div>

          <div class="phone-viewport" style="aspect-ratio: {screenWidth} / {screenHeight};">
            <canvas
              bind:this={canvasEl}
              class="phone-canvas"
              class:is-visible={frameCount > 0}
              width={screenWidth}
              height={screenHeight}
              tabindex="0"
              aria-label="Interactive Android application canvas. Click or drag to interact; focus and type for keyboard input."
              onpointerdown={onCanvasPointerDown}
              onpointermove={onCanvasPointerMove}
              onpointerup={onCanvasPointerUp}
              onpointercancel={onCanvasPointerUp}
              onwheel={onCanvasWheel}
              onkeydown={event => onCanvasKey(event, true)}
              onkeyup={event => onCanvasKey(event, false)}
            ></canvas>

            {#if frameCount === 0}
              {#if phase === 'booting' || phase === 'inspecting'}
                <div class="phone-loading" role="status" aria-live="polite">
                  <div class="loader-emblem" aria-hidden="true">
                    <span class="loader-ring"></span>
                    <img src="{home}spectre.svg" width="34" height="34" alt="" />
                  </div>

                  <div class="loader-heading">
                    <p class="loader-app">{apkInfo?.label || apkFile?.name || 'Starting APK'}</p>
                    {#if apkInfo?.package}
                      <p class="loader-pkg">{apkInfo.package}</p>
                    {/if}
                  </div>

                  <div class="loader-progress">
                    <div
                      class="loader-bar"
                      role="progressbar"
                      aria-label="Application startup progress"
                      aria-valuemin="0"
                      aria-valuemax="100"
                      aria-valuenow={loadPercent}
                    >
                      <div class="loader-fill" style="width: {Math.max(6, loadPercent)}%;"></div>
                    </div>
                    <div class="loader-meta">
                      <span>{loadPercent}%</span>
                      <span>Elapsed {formatElapsed(bootElapsedSec)}</span>
                    </div>
                  </div>

                  <p class="loader-detail">{loadDetail || statusMessage}</p>

                  <ol class="loader-steps" aria-label="Startup steps">
                    {#each LOAD_STEPS as step (step.id)}
                      {@const state = stepState(step.id)}
                      <li data-state={state}>
                        <span class="loader-dot" aria-hidden="true">
                          {#if state === 'done'}
                            <svg viewBox="0 0 16 16" width="11" height="11" focusable="false">
                              <path d="M3.2 8.4 6.4 11.4 12.8 4.8" />
                            </svg>
                          {/if}
                        </span>
                        <span>{step.label}</span>
                      </li>
                    {/each}
                  </ol>

                  <p class="loader-footer">100% local in WebAssembly · Zero telemetry</p>
                </div>
              {:else}
                <div class="phone-placeholder">
                  <p class="placeholder-title">Drop an <code>.apk</code> here</p>
                  <p class="placeholder-sub">
                    Pure Java/Kotlin and <code>arm64-v8a</code> native APKs run locally inside WebAssembly Linux.
                  </p>
                  <button
                    type="button"
                    class="button secondary placeholder-pick"
                    onclick={() => apkInput?.click()}
                  >
                    Choose an APK file
                  </button>
                </div>
              {/if}
            {/if}
          </div>

          <div class="phone-controls" role="toolbar" aria-label="App screen controls">
            <button type="button" class="control-btn" onclick={sendBack} disabled={phase !== 'running'}>
              Back
            </button>
            <button type="button" class="control-btn" onclick={toggleRotate}>
              Rotate
            </button>
            <button type="button" class="control-btn" onclick={toggleMute} aria-pressed={muted}>
              {muted ? 'Unmute' : 'Mute'}
            </button>
            <button type="button" class="control-btn" onclick={saveScreenshot} disabled={frameCount === 0}>
              Screenshot
            </button>
            <button
              type="button"
              class="control-btn control-popout"
              onclick={togglePopout}
              aria-pressed={poppedOut}
            >
              {poppedOut ? 'Dock back' : 'Pop out'}
            </button>
            {#if deferredInstallPrompt && !isInstalledPwa}
              <button
                type="button"
                class="control-btn control-install"
                onclick={installWebApp}
                disabled={installBusy}
              >
                {installBusy ? 'Installing…' : 'Install app'}
              </button>
            {/if}
            <button
              type="button"
              class="control-btn control-stop"
              onclick={() => stopSession(false)}
              disabled={phase !== 'running' && phase !== 'booting' && phase !== 'inspecting'}
            >
              Stop
            </button>
          </div>
        </div>
      </div>

      {#if poppedOut}
        <div class="phone-popped-card" role="status">
          <p class="placeholder-title">{apkInfo?.label || 'App'} is popped out</p>
          <p class="placeholder-sub">
            The live Android screen is running in a floating window. Close it or click below to bring it back here.
          </p>
          <button type="button" class="button secondary" onclick={dockBack}>
            Bring window back
          </button>
        </div>
      {/if}

      <details class="runner-logs" bind:open={logsOpen}>
        <summary>
          <span>Runtime console</span>
          <span class="disclosure-icon" aria-hidden="true">
            <svg class="icon" data-icon="plus" viewBox="0 0 24 24" focusable="false">
              <path d="M5 12h14" /><path d="M12 5v14" />
            </svg>
          </span>
        </summary>
        <div class="runner-log-body">
          <pre><code>{logText || 'No runtime output yet. Start an APK to stream standalone ART and Shiraz logs here.'}</code></pre>
        </div>
      </details>
    </div>

    <form
      class="builder-panel runner-panel"
      onsubmit={event => {
        event.preventDefault();
        launchApk();
      }}
      aria-busy={phase === 'inspecting' || phase === 'booting'}
    >
      <fieldset disabled={phase === 'inspecting' || phase === 'booting'}>
        <legend class="visually-hidden">Browser APK runner configuration</legend>

        <div class="builder-base">
          <strong>{localRuntimeFile ? `Local: ${localRuntimeFile.name}` : runtimeBadge}</strong>
          <p>{localRuntimeFile ? `${formatSize(localRuntimeFile.size)} · read locally in browser` : runtimeDetail}</p>
        </div>

        <label for="runner-apk">Android™ APK</label>
        <input
          id="runner-apk"
          bind:this={apkInput}
          type="file"
          accept=".apk"
          onchange={onApkInputChange}
        />

        {#if apkInfo}
          <dl class="apk-summary">
            <div>
              <dt>Package</dt>
              <dd><code>{apkInfo.package}</code></dd>
            </div>
            <div>
              <dt>Activity</dt>
              <dd><code>{apkInfo.activity}</code></dd>
            </div>
            <div>
              <dt>Native ABI</dt>
              <dd>
                {#if apkInfo.native}
                  <code>arm64-v8a</code> ({apkInfo.native_libs.length} {apkInfo.native_libs.length === 1 ? 'library' : 'libraries'})
                {:else}
                  Pure DEX (no native libraries)
                {/if}
              </dd>
            </div>
          </dl>
        {/if}

        <div class="runner-option-grid">
          <div>
            <label for="runner-art-mode">ART execution</label>
            <select id="runner-art-mode" bind:value={artMode}>
              <option value="interpreter">Interpreter (fast)</option>
              <option value="jit">ART JIT (exp.)</option>
            </select>
          </div>
          <div>
            <label for="runner-memory">Guest RAM</label>
            <select id="runner-memory" bind:value={memoryMiB}>
              <option value={512}>512 MiB</option>
              <option value={768}>768 MiB (rec.)</option>
              <option value={1024}>1024 MiB</option>
              <option value={1536}>1536 MiB</option>
            </select>
          </div>
        </div>

        <label class="builder-checkbox" for="runner-persist">
          <input id="runner-persist" type="checkbox" bind:checked={persistData} />
          <span>Persist app data locally in IndexedDB</span>
        </label>

        <details class="runner-override">
          <summary>
            <span>Runtime image override (.erofs, .img, or aarch64 .AppImage)</span>
            <span class="disclosure-icon" aria-hidden="true">
              <svg class="icon" data-icon="plus" viewBox="0 0 24 24" focusable="false">
                <path d="M5 12h14" /><path d="M12 5v14" />
              </svg>
            </span>
          </summary>
          <label for="runner-runtime-file">Local Shiraz aarch64 runtime</label>
          <input
            id="runner-runtime-file"
            bind:this={localRuntimeInput}
            type="file"
            accept=".erofs,.img,.ext4,.AppImage"
            onchange={onRuntimeInputChange}
          />
          <p class="builder-note">
            Build a trimmed runtime locally with <code>python3 scripts/make_browser_runtime.py</code> or select a
            <code>Shiraz-Browser-Base-*-aarch64.AppImage</code> from GitHub Releases.
          </p>
        </details>

        <div class="runner-submit-row">
          <button
            class="button primary"
            id="runner-launch"
            type="submit"
            disabled={supportState.checked && !supportState.ok}
          >
            {#if phase === 'inspecting'}
              Inspecting APK…
            {:else if phase === 'booting'}
              Starting app…
            {:else if phase === 'running'}
              Restart APK
            {:else}
              Run APK in browser
            {/if}
            <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M13.5 4.5 21 12m0 0-7.5 7.5" /><path d="M21 12H3" />
            </svg>
          </button>

          <button
            type="button"
            class="button secondary runner-clear-btn"
            onclick={resetStorage}
            disabled={clearingData}
          >
            {clearingData ? 'Clearing…' : 'Clear saved data'}
          </button>
        </div>
      </fieldset>

      <p
        class="builder-status"
        id="runner-status"
        data-state={phase === 'error' ? 'error' : phase === 'running' ? 'success' : ''}
        role="status"
        aria-live="polite"
      >
        {errorMessage || statusMessage}
      </p>

      <p class="builder-note">
        Experimental client-side execution. Runs software-composited Android apps locally inside your browser tab
        (offline by default, zero telemetry; no Google Play Services; 32-bit-only APKs are rejected). Only run APKs you
        trust.
      </p>
    </form>
  </section>

  {#if isStandalone}
    <p class="standalone-credit">
      Powered by Shiraz &amp;
      <a href="https://github.com/pithings/unkernel">pithings/unkernel</a> (Pooya Parsa) ·
      <a href="{home}licenses/unkernel.txt">Licenses</a> · Zero telemetry
    </p>
  {:else}
    <section class="unkernel-credit" aria-labelledby="unkernel-credit-title">
      <div class="unkernel-credit-head">
        <p class="eyebrow">Powered by open source</p>
        <h2 id="unkernel-credit-title">Built on <span>unkernel</span>.</h2>
      </div>
      <div class="unkernel-credit-body">
        <p>
          This in-browser runner is made possible by
          <a href="https://github.com/pithings/unkernel"><strong>unkernel</strong></a> by
          <strong>Pooya Parsa (pi0)</strong> — an open-source project that compiles a real Linux 7.3 kernel with a real
          MMU directly to WebAssembly and runs unmodified <code>aarch64</code> user-space binaries via
          <code>kwemu</code> (an ARM64 CPU emulator and WebAssembly JIT).
        </p>
        <p>
          Shiraz uses only the local self-hosted <code>unkernel</code> WebAssembly bundle and SDK over standard Linux
          syscalls — offline by default, with zero telemetry, no Wisp relay connections, and no requests to
          <code>unkernel.dev</code>.
        </p>
        <div class="unkernel-links">
          <a class="button secondary" href="https://github.com/pithings/unkernel">
            pithings/unkernel on GitHub
            <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" />
            </svg>
          </a>
          <a class="button secondary" href="https://unkernel.dev/">
            unkernel.dev
            <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" />
            </svg>
          </a>
          <a class="guide-link" href="{home}licenses/unkernel.txt">
            unkernel license &amp; third-party notices (MIT / GPL-2.0)
            <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" />
            </svg>
          </a>
        </div>
      </div>
    </section>

    <section class="faq-subpage" aria-labelledby="run-faq-title">
      <div class="faq-block faq-wide">
        <p class="eyebrow">Frequently asked</p>
        <h2 id="run-faq-title">How in-browser execution works.</h2>
        {#each runFaqs as item (item.q)}
          <details class="source-build faq-item">
            <summary>
              {item.q}
              <span class="disclosure-icon" aria-hidden="true">
                <svg class="icon" data-icon="plus" viewBox="0 0 24 24" focusable="false">
                  <path d="M5 12h14" /><path d="M12 5v14" />
                </svg>
              </span>
            </summary>
            <div class="build-content"><p>{item.a}</p></div>
          </details>
        {/each}
      </div>
    </section>
  {/if}
</main>

{#if !isStandalone}
  <Footer />
{/if}
