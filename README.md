# Shiraz

Shiraz runs selected Android APKs on Linux using standalone ART, an Android
framework compatibility layer, SDL, and EGL/GLES. Compatibility is experimental.

Use the [browser builder](https://spectre-oss.github.io/shiraz/) to package an APK
locally in your browser, or build from source with the [Makefile](Makefile).

- `make build` builds the runtime with prepared dependencies.
- `make appimage APK=/absolute/path/application.apk` packages an application.
- `make runtime-digitalis` builds optional ARM64 translation from pinned sources;
  prepared ART, compiler dependencies, and matching guest libraries are required.
- `make system-video` builds optional support for OS-provided GStreamer codecs.
- `make check` runs the repository checks.

ARM64 support uses [Digitalis](https://digitalisx64.github.io/), built on AOSP
Berberis. Credit to [utzcoz](https://github.com/utzcoz), Digitalis contributors,
and the Android Open Source Project. Shiraz supplies the ART/JNI and native API
adapters; no full Android system or QEMU is required for this translation path.

Graphics, native APIs, and media support remain incremental. Video decoding
requires suitable system plugins; hardware acceleration depends on the driver.

Shiraz's original code is [GPL-3.0-only](LICENSE). Dependencies retain their
licenses and notices. See [LICENSES](LICENSES), including the
[Digitalis credits and provenance](LICENSES/DIGITALIS-NOTICE.txt).
