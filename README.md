# Shiraz

**Android™ apps on Linux, without virtualization.**

> **Experimental — no app sandbox.** Shiraz does not provide Android-style app
> isolation. APKs run with the permissions of the user running Shiraz and may
> access that user's files and resources. Only run APKs you trust. For testing
> unfamiliar APKs, use a separate, restricted environment such as an unprivileged
> LXC container; avoid exposing personal files or sensitive host resources.

[Shiraz](https://www.openstreetmap.org/node/26082452) is an experimental compatibility layer that brings Android apps to the
Linux desktop. The long-term goal is to bring the same experience to Linux phones.

[Try Builder](https://spectre-oss.github.io/shiraz/#builder) · [Set up Web](docs/WEB.md) · [License](LICENSE)

## One runtime, three ways to use it

| Part | What it does | Use it to… |
| --- | --- | --- |
| **Shiraz** | The core runtime that runs supported Android apps on Linux. | Run apps on your desktop and develop Android compatibility. |
| **[Shiraz Builder](https://spectre-oss.github.io/shiraz/#builder)** | Packages an APK with the runtime into a Linux AppImage, locally in your browser. Your APK is not uploaded. | Turn a supported APK into a standalone Linux application. |
| **[Shiraz Web](docs/WEB.md)** | Runs an APK on a Linux host and streams its screen and controls to your browser over WebRTC. | Try and interact with apps through a browser, locally or on a configured remote host. |

Builder creates an application you run on Linux. Web runs the application on
the host serving it. Both use the same Shiraz runtime and share its compatibility
limits.

## How it works

Shiraz runs Android app code with ART, Android's runtime, and translates Android
APIs into Linux functionality. Windows, input, and graphics use SDL and EGL/GLES,
without booting an Android system or running a virtual machine.

Optional ARM64 translation, powered by [Digitalis](https://digitalisx64.github.io/),
lets supported ARM64 app code run on x86-64 Linux.

## Status

Selected apps and game rendering already work. Compatibility is still growing:
apps may encounter missing APIs, graphics issues, or unsupported media features.
Linux phone support is a future goal.

## Get started

Use the [APK builder](https://spectre-oss.github.io/shiraz/#builder) to package an APK
as an AppImage, or follow the [Web setup guide](docs/WEB.md) to run apps
through your browser.

For source builds, with the runtime dependencies prepared:

```sh
make build
make appimage APK=/absolute/path/application.apk
```

See the [development guide](docs/DEVELOPMENT.md) for setup, project structure, testing,
and contributions, or the [Makefile](Makefile) for all build targets.

## Contributing

Contributions are always welcome: bug reports, compatibility fixes, tests, and
clearer documentation all help Shiraz grow.

If you have access to a capable AI coding assistant—such as GPT 6.1 Sol, Astra,
Opus 5.5, or Sonnet 5.5—we would especially appreciate your help finding and fixing
bugs. Start with an app you care about and trust, and follow the
[AI-assisted contribution guide](docs/AI_CONTRIBUTING.md). It covers isolated
execution, collecting evidence, reporting an issue, and preparing a pull request.
You do not need an AI assistant to contribute.

See the [documentation](docs/README.md) and [development guide](docs/DEVELOPMENT.md)
to get started. Please review and test any AI-generated changes before submitting.

### Help fund Shiraz

Shiraz is expensive to develop. AI tools, compute, and testing cost real money.
We have plenty of ideas and future work planned, but funding is a real limit on
how much we can do.

So far, we have received no financial support from OpenAI or Anthropic. All
funding comes from us: people who love open source, believe in Linux phones,
and want everyone to have the freedom to control their own hardware.

If you like Shiraz and believe in that future, please consider donating.
Your support helps us keep building. Thank you.

## Acknowledgments

Shiraz is built with the help of AI coding assistants: OpenAI Codex, primarily
using GPT Astra and GPT 6.1 Sol, and Grok CLI for general tasks.

## Credits & license

ARM64 support builds on Digitalis and AOSP Berberis. Thanks to
[utzcoz](https://github.com/utzcoz), the Digitalis contributors, and the Android
Open Source Project. See the [Digitalis attribution](LICENSES/DIGITALIS-NOTICE.txt).

Shiraz's original code is licensed under [GPL-3.0-only](LICENSE).
Third-party code, libraries, and fonts retain their own licenses and copyright
notices; Shiraz's license does not replace them. Thanks also to the projects
documented in [LICENSES](LICENSES):

- [AOSP contributions](LICENSES/AOSP-NOTICE.txt) and
  [ripple rendering adaptations](LICENSES/RIPPLE-NOTICE.txt) —
  [Apache-2.0](LICENSES/Apache-2.0.txt).
- [Bionic translation linker](LICENSES/BIONIC-LINKER.txt), from the
  android_translation_layer project — its original BSD-style notice.
- [Skia](LICENSES/SKIA-NOTICE.txt) — BSD-3-Clause;
  [libsndfile](LICENSES/SNDFILE-NOTICE.txt) — LGPL-2.1-or-later.
- [Noto fonts](LICENSES/NOTO-FONTS.txt) — SIL Open Font License 1.1.
- [macOS text, path and image libraries](LICENSES/MACOS-GRAPHICS-NOTICE.txt):
  FreeType (FreeType License), HarfBuzz, fontconfig and pixman (MIT-style),
  cairo (LGPL-2.1), libwebp (BSD-3-Clause and patent grant) and libjpeg-turbo
  (IJG, BSD-3-Clause, zlib). This software is based in part on the work of the
  Independent JPEG Group.
- [macOS OpenGL ES and GPU canvas libraries](LICENSES/MACOS-GPU-NOTICE.txt):
  ANGLE (BSD-3-Clause) with Abseil (Apache-2.0), Chromium zlib (Zlib),
  Chromium zlib/google and anglebase (BSD-3-Clause) and xxHash (BSD-2-Clause);
  Skia with skcms (BSD-3-Clause).

These are acknowledgments, not a complete license inventory for every build.
Redistributed components must retain their applicable license texts and notices.

[Android is a trademark of Google LLC.](https://developer.android.com/legal)
Other product names and trademarks belong to their respective owners. Shiraz is
an independent project; references to other projects or companies do not imply
affiliation or endorsement.

---

<p dir="rtl" lang="fa">
بس بگردید و بگردد روزگار<br>
دل به دنیا در نبندد هوشیار<br>
ای که دستت می‌رسد کاری بکن<br>
پیش از آن کز تو نیاید هیچ کار
</p>

<p dir="rtl" lang="fa"><a href="https://ganjoor.net/saadi/mavaez/ghasides/sh28">سعدی — قصیدهٔ شمارهٔ ۲۸</a></p>

To block direct internet access for a packaged app, run:

```sh
./YourApp.AppImage --no-internet
```

This Linux seccomp policy blocks non-Unix socket creation in Java/native code and
child processes, closes inherited sockets, and disables io_uring. It fails rather
than launching unrestricted if the policy cannot be installed. Local Unix IPC
remains available for display/audio; this is not a complete sandbox and does not
prevent network access through an external local service. Apps requiring servers
may not function offline. `--diagnose` reports the selected policy without launching.

The development CLI accepts a matching native-only ABI split without modifying
its APKs: `python3 -m shiraz run base.apk --native-split split_config.arm64_v8a.apk`
(with `PYTHONPATH=src`). Package/version mismatches and code/resource splits are
rejected. This is not general APKM installation support.

Experimental ARM64 developer runs on x86_64 select the built bridge through
`SHIRAZ_DIGITALIS_LIBRARY=/absolute/path/libberberis_arm64.so`. Build its core with
`make runtime-digitalis` and its platform proxies with `make digitalis-platform-proxies`.
The pinned Digitalis guest-support libraries must be visible under `/system`
inside the test process's private namespace; do not install them over the host.
`make digitalis-platform-smoke DIGITALIS_GUEST_SYSTEM=/path/to/guest/system`
checks translated math, thread attributes/signals/exit, and the existing NDK media
subset. Unsupported media APIs fail explicitly. Digitalis is not yet wired into
the AppImage packager.

Set `SHIRAZ_MOUSE_AS_TOUCH=1` to expose a virtual single-touch device and translate
left-button mouse drags into Android finger gestures. Physical touch input and
host mouse capture remain available; unset it for normal mouse events.
