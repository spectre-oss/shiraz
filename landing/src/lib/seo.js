// SPDX-License-Identifier: GPL-3.0-only
// Shared SEO, GEO (Generative Engine Optimization) and AEO (Answer Engine Optimization)
// metadata, FAQ content, and Schema.org JSON-LD graph builders.
// Visible FAQ blocks render from these exact arrays so on-page text and JSON-LD stay identical.

export const SITE_URL = 'https://spectre-oss.github.io/shiraz';
export const REPO_URL = 'https://github.com/spectre-oss/shiraz';
export const SOCIAL_IMAGE = `${SITE_URL}/social-card.png`;
export const ROBOTS_INDEX = 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1';

export const homeFaqs = [
  {
    q: 'How does Shiraz run Android apps without a virtual machine or Waydroid?',
    a: 'Shiraz runs Android DEX bytecode on a standalone host build of ART (Android Runtime) and translates Android framework, resource, and NDK calls directly to host APIs using SDL2, EGL/GLES (GBM/DMA-BUF zero-copy on Linux, ANGLE-Metal IOSurface on macOS), and native audio and IPC bridges. Unlike Waydroid or Android Emulator, it does not boot an Android OS image, require a VM, or need the Linux binder kernel module or root.'
  },
  {
    q: 'How do I convert an Android APK into a Linux AppImage or Flatpak?',
    a: 'Open Shiraz Builder on this page, pick your CPU architecture (x86_64 or ARM64) and package format (AppImage or sandboxed Flatpak), choose your local APK file, and click Build. Assembly runs locally in your browser using Go WebAssembly against a SHA-256-verified runtime base so your APK never leaves your device. From source, run make appimage APK=/path/to/app.apk or make flatpak APK=/path/to/app.apk.'
  },
  {
    q: 'Which operating systems and CPU architectures does Shiraz support?',
    a: 'Linux x86_64 and ARM64 are the primary targets, with prebuilt browser bases, AppImages, Flatpak bundles, and WebRTC streaming. Apple Silicon macOS (ARM64) is an active experimental port with native Metal/ANGLE zero-copy graphics, VideoToolbox/AudioToolbox media, opt-in guest ELF loading, and signed .app bundling. Windows x64 and ARM64 have a CI-verified Win32, DXGI, WASAPI, Media Foundation, and MSIX native foundation while Win32 ART integration continues.'
  },
  {
    q: 'Are Android apps sandboxed in Shiraz?',
    a: 'Yes, OS-level process sandboxing is enabled by default across shiraz run, shiraz install-desktop, shiraz daemon, and Linux AppImages (--no-sandbox to opt out; --no-network or --no-internet to deny sockets), using Linux Landlock LSM + PR_SET_NO_NEW_PRIVS + seccomp-BPF, macOS Seatbelt + App Sandbox entitlements, and Windows Job Objects + Restricted Tokens, alongside Flatpak bubblewrap bundles. Because host process confinement is not Android’s full per-UID/SELinux sandbox, still only run APKs you trust.'
  },
  {
    q: 'Does Shiraz include Google Play Services or the Play Store?',
    a: 'No. Shiraz does not ship the Google Play Store or Google Play Services. Apps that depend strictly on Play Integrity or proprietary Google Play Services APIs will not have those services provided.'
  }
];

export const compatibilityFaqs = [
  {
    q: 'Does 4,445 of 4,445 API 35 classes mean every Android app works?',
    a: 'No. The 4,445-class inventory confirms that every non-resource android.* class name from Android 15 / API 35 compiles into the framework and ART boot jars, and the 15-library / 887-symbol NDK audit confirms exported C symbols. Method-level behavior, hardware drivers, and end-to-end app workflows remain bounded and are tracked separately per subsystem and per app.'
  },
  {
    q: 'Which real Android apps and engines have been tested in Shiraz?',
    a: 'The public catalog records 14 real applications across 8 frameworks and engines: OsmAnd+ and Telegram (Android framework + JNI), 2048 and F-Droid (Android framework), Obtainium and Ember Quest (Flutter), Joplin and SEN-214 Calculator (React Native), Lucas Doggiehood (Unity), Unciv (libGDX), Tiny Crate and Abra-Ca Bubble (Godot), SuperTux (SDL), and Mindustry (Arc), each with observed flows and open limitations documented.'
  },
  {
    q: 'How far along are the native macOS and Windows ports?',
    a: 'On Apple Silicon macOS, 46 of 91 tracked native host checklist items are verified and 19 are partial, with live OsmAnd+ vector map, offline search, turn-by-turn pedestrian routing/navigation, 468 MB live HTTPS offline map downloads, resize, and zoom-persistence walkthroughs on Metal/ANGLE plus a pre-warmed WarmArtPool standby. On Windows x64/ARM64, 32 of 37 native host items pass 3-OS CI and the remaining 5 are partial foundations (including ANGLE-D3D11 Per-Monitor V2 DPI, Win32 guest ELF/SysV↔Win64 thunks, and bootstrap_windows.py ART orchestrator) while live Win32 libart.dll execution remains open.'
  },
  {
    q: 'Why does Shiraz not publish a single whole-Android compatibility percentage?',
    a: 'Android compatibility spans class presence, NDK symbol exports, host OS backends, hardware drivers, and interactive app behavior. Collapsing those into one number hides real limits, so Shiraz keeps class inventories, NDK audits, host OS checklists, behavioral probes, and per-app walkthroughs strictly separate.'
  }
];

export const compareFaqs = [
  {
    q: 'How is Shiraz different from Waydroid and Android containers?',
    a: 'Waydroid boots a full LineageOS Android system inside an LXC container and requires Linux binder kernel support. Shiraz is an app-only translation layer that executes only the APK on standalone ART over host libraries, with no Android OS image, no container boot, no kernel binder module, and no root requirement.'
  },
  {
    q: 'How does Shiraz compare to Android Translation Layer (ATL)?',
    a: 'ATL is the pioneering standalone-ART Linux translation layer that Shiraz learned from first (and Shiraz ships ATL’s bionic_translation linker under its original notice). Unlike ATL’s GTK4 widget mapping on Linux, Shiraz is GTK-free and uses SDL2 with native graphics bridges across Linux, macOS, and Windows, adding a browser AppImage/Flatpak builder, WebRTC streaming, and warm ART standby pre-warming.'
  },
  {
    q: 'How does Shiraz compare to Mac.apk on Apple Silicon Macs?',
    a: 'Mac.apk is Apple Silicon macOS-only, converts DEX to JVM bytecode (dex2jvm on OpenJDK), uses AGPL-3.0 plus commercial licensing, and currently reports more fully working games (52 of 66 tested). Shiraz runs DEX directly on standalone ART under GPL-3.0-only across Linux, Apple Silicon macOS, and Windows from one codebase.'
  },
  {
    q: 'Does Shiraz require root permissions or the Linux binder kernel module?',
    a: 'No. Shiraz runs entirely in user space without root privileges, without kernel binder or ashmem modules, and without a hypervisor.'
  }
];

const orgNode = {
  '@type': 'Organization',
  '@id': `${SITE_URL}/#organization`,
  name: 'spectre-oss',
  url: 'https://github.com/spectre-oss',
  logo: `${SITE_URL}/spectre.svg`,
  sameAs: ['https://github.com/spectre-oss', REPO_URL]
};

const websiteNode = {
  '@type': 'WebSite',
  '@id': `${SITE_URL}/#website`,
  name: 'Shiraz',
  url: `${SITE_URL}/`,
  inLanguage: 'en',
  description: 'Open-source, GTK-free Android compatibility layer and local browser AppImage/Flatpak APK builder for Linux, macOS, and Windows.',
  publisher: { '@id': `${SITE_URL}/#organization` }
};

function faqNode(id, faqs) {
  return {
    '@type': 'FAQPage',
    '@id': id,
    mainEntity: faqs.map(({ q, a }) => ({
      '@type': 'Question',
      name: q,
      acceptedAnswer: { '@type': 'Answer', text: a }
    }))
  };
}

function breadcrumbNode(id, items) {
  return {
    '@type': 'BreadcrumbList',
    '@id': id,
    itemListElement: items.map(([name, item], idx) => ({
      '@type': 'ListItem',
      position: idx + 1,
      name,
      item
    }))
  };
}

export function serializeJsonLd(data) {
  // Escape '<' so closing script tags can never break out of the JSON-LD block.
  const json = JSON.stringify(data).replace(/</g, '\\u003c');
  return `<script type="application/ld+json">${json}</` + 'script>';
}

export function homeJsonLd(version) {
  return {
    '@context': 'https://schema.org',
    '@graph': [
      orgNode,
      websiteNode,
      {
        '@type': 'SoftwareApplication',
        '@id': `${SITE_URL}/#software`,
        name: 'Shiraz',
        alternateName: 'Shiraz Android Compatibility Layer',
        softwareVersion: version,
        applicationCategory: 'UtilitiesApplication',
        applicationSubCategory: 'Android Compatibility Layer',
        operatingSystem: 'Linux (x86_64, ARM64), macOS (Apple Silicon ARM64), Windows (x64, ARM64)',
        license: 'https://www.gnu.org/licenses/gpl-3.0.html',
        isAccessibleForFree: true,
        url: `${SITE_URL}/`,
        downloadUrl: `${REPO_URL}/releases`,
        codeRepository: REPO_URL,
        image: SOCIAL_IMAGE,
        author: { '@id': `${SITE_URL}/#organization` },
        description:
          'Run supported Android APKs natively on Linux, Apple Silicon macOS, and Windows without a virtual machine, container, or kernel binder module. Includes a local Go/WASM browser builder for Linux AppImages and sandboxed Flatpak bundles.',
        keywords:
          'Android on Linux, Android compatibility layer, run APK on Linux, APK to AppImage, APK to Flatpak, Android on Apple Silicon macOS, Waydroid alternative, GTK-free Android runtime, standalone ART',
        featureList: [
          'Standalone ART execution with pre-warmed WarmArtPool standby on Linux and macOS',
          'All 4,445 Android 15 / API 35 java framework class surfaces across 49 packages',
          'All 15 tracked API 35 NDK C/C++ libraries (887 audited symbols)',
          'Zero-copy GBM/DMA-BUF graphics on Linux and ANGLE-Metal IOSurface on macOS, plus guest/host Vulkan bridging',
          'Client-side Go WebAssembly Builder for Linux AppImage and sandboxed Flatpak (.flatpak) bundles (CLI --digitalis ARM64 bundling)',
          'WebRTC browser streaming (Shiraz Web) for desktop and Linux phones',
          'GTK-free SDL2 presentation across Linux, macOS, and Windows',
          'Default-on cross-platform OS sandboxing (Landlock/seccomp, Seatbelt, Job Object, and Flatpak bubblewrap)'
        ],
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
        sameAs: [REPO_URL]
      },
      {
        '@type': 'HowTo',
        '@id': `${SITE_URL}/#builder-howto`,
        name: 'How to package an Android APK into a Linux AppImage or Flatpak in your browser',
        description: 'Assemble a standalone Linux AppImage or sandboxed Flatpak bundle locally in your browser without uploading your APK.',
        totalTime: 'PT2M',
        tool: [{ '@type': 'HowToTool', name: 'Modern web browser with WebAssembly support' }],
        step: [
          { '@type': 'HowToStep', position: 1, name: 'Choose target CPU architecture', text: 'Select x86_64 (Intel / AMD) or ARM64 (aarch64 laptops and Linux phones) in Shiraz Builder.' },
          { '@type': 'HowToStep', position: 2, name: 'Select package format', text: 'Pick AppImage for a single portable executable or Flatpak (.flatpak) for a sandboxed desktop bundle.' },
          { '@type': 'HowToStep', position: 3, name: 'Select a local APK file', text: 'Choose an unmodified Android APK on your computer; the APK stays inside browser memory and is never uploaded.' },
          { '@type': 'HowToStep', position: 4, name: 'Build and download locally', text: 'Click Build to verify the SHA-256 checksum of the Shiraz runtime base and assemble the standalone Linux package in WebAssembly.' }
        ]
      },
      faqNode(`${SITE_URL}/#faq`, homeFaqs)
    ]
  };
}

export function compatibilityJsonLd({ version, updated, appCount, classPresent, classReference, ndkLibs, ndkSymbols }) {
  return {
    '@context': 'https://schema.org',
    '@graph': [
      orgNode,
      websiteNode,
      {
        '@type': 'WebPage',
        '@id': `${SITE_URL}/compatibility/#webpage`,
        url: `${SITE_URL}/compatibility/`,
        name: 'App compatibility and Android API 35 coverage — Shiraz',
        description: `Recorded Shiraz compatibility across ${appCount} apps, 3 desktop OS backends, ${classPresent.toLocaleString('en')}/${classReference.toLocaleString('en')} API 35 framework classes, and ${ndkLibs}/${ndkLibs} NDK libraries (${ndkSymbols} C symbols).`,
        dateModified: updated,
        inLanguage: 'en',
        isPartOf: { '@id': `${SITE_URL}/#website` },
        breadcrumb: { '@id': `${SITE_URL}/compatibility/#breadcrumb` }
      },
      breadcrumbNode(`${SITE_URL}/compatibility/#breadcrumb`, [
        ['Home', `${SITE_URL}/`],
        ['Compatibility', `${SITE_URL}/compatibility/`]
      ]),
      {
        '@type': 'Dataset',
        '@id': `${SITE_URL}/compatibility/#dataset`,
        name: 'Shiraz Android Compatibility & API 35 Coverage Catalog',
        description:
          'Public, versioned measurements of Shiraz compatibility across real Android applications, Linux/macOS/Windows host OS backends, Android 15 (API 35) Java class-name presence, NDK shared-library symbol exports, and behavioral probe assertions.',
        url: `${SITE_URL}/compatibility/`,
        version,
        dateModified: updated,
        license: 'https://www.gnu.org/licenses/gpl-3.0.html',
        isAccessibleForFree: true,
        creator: { '@id': `${SITE_URL}/#organization` },
        variableMeasured: [
          'Recorded Android app compatibility status',
          'Host OS native backend checklist progress (Linux, macOS ARM64, Windows x64/ARM64)',
          'Android 15 / API 35 Java framework class-name presence',
          'Android 15 / API 35 NDK C symbol exports',
          'Behavioral API probe assertion counts'
        ],
        distribution: [
          { '@type': 'DataDownload', encodingFormat: 'application/json', contentUrl: `${REPO_URL}/blob/main/landing/src/lib/compatibility.json` },
          { '@type': 'DataDownload', encodingFormat: 'text/plain', contentUrl: `${SITE_URL}/llms-full.txt` }
        ]
      },
      faqNode(`${SITE_URL}/compatibility/#faq`, compatibilityFaqs)
    ]
  };
}

export function compareJsonLd({ version, updated, projects }) {
  return {
    '@context': 'https://schema.org',
    '@graph': [
      orgNode,
      websiteNode,
      {
        '@type': 'WebPage',
        '@id': `${SITE_URL}/compare/#webpage`,
        url: `${SITE_URL}/compare/`,
        name: 'Shiraz vs ATL, ACL, a4l, Mac.apk, apklayer and Innkeeper — Shiraz',
        description: 'Technical comparison of seven open-source projects running Android apps on Linux, macOS, and Windows: architecture, graphics, sandboxing, packaging, and tested apps.',
        dateModified: updated,
        inLanguage: 'en',
        isPartOf: { '@id': `${SITE_URL}/#website` },
        breadcrumb: { '@id': `${SITE_URL}/compare/#breadcrumb` }
      },
      breadcrumbNode(`${SITE_URL}/compare/#breadcrumb`, [
        ['Home', `${SITE_URL}/`],
        ['Compare', `${SITE_URL}/compare/`]
      ]),
      {
        '@type': 'ItemList',
        '@id': `${SITE_URL}/compare/#projects`,
        name: `Open-source Android-on-desktop projects compared beside Shiraz v${version}`,
        numberOfItems: projects.length,
        itemListElement: projects.map((p, idx) => ({
          '@type': 'ListItem',
          position: idx + 1,
          item: {
            '@type': 'SoftwareApplication',
            name: p.name,
            url: p.url,
            description: `${p.tagline}. ${p.host[0]}`
          }
        }))
      },
      faqNode(`${SITE_URL}/compare/#faq`, compareFaqs)
    ]
  };
}
