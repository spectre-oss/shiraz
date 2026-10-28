# Shiraz

**Android™ apps on Linux, without virtualization.**

> **Experimental — no app sandbox.** Shiraz does not provide Android-style app
> isolation. APKs run with the permissions of the user running Shiraz and may
> access that user's files and resources. Only run APKs you trust. For testing
> unfamiliar APKs, use a separate, restricted environment such as an unprivileged
> LXC container; avoid exposing personal files or sensitive host resources.

[Shiraz](https://www.openstreetmap.org/node/26082452) is an experimental compatibility layer that brings Android apps to the
Linux desktop. The long-term goal is to bring the same experience to Linux phones.

[Try Builder](https://spectre-oss.github.io/shiraz/) · [Set up Web](docs/WEB.md) · [License](LICENSE)

## One runtime, three ways to use it

| Part | What it does | Use it to… |
| --- | --- | --- |
| **Shiraz** | The core runtime that runs supported Android apps on Linux. | Run apps on your desktop and develop Android compatibility. |
| **[Shiraz Builder](https://spectre-oss.github.io/shiraz/)** | Packages an APK with the runtime into a Linux AppImage, locally in your browser. Your APK is not uploaded. | Turn a supported APK into a standalone Linux application. |
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

Use the [APK builder](https://spectre-oss.github.io/shiraz/) to package an APK
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
