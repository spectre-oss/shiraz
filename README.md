# Shiraz

Shiraz runs selected Android APKs on Linux using a translated Android framework,
ART, SDL, and EGL/GLES. App compatibility is incremental.

## Graphics and animation

The renderer uses the available Linux GPU through Mesa/EGL. Framework graphics
retain a software fallback when GPU initialization is unavailable. Android frame
callbacks run on the window presentation loop; background Looper threads receive
timed frame callbacks too. `SHIRAZ_GPU` in the run log identifies the renderer.
GPU rendering and hardware video decoding are separate capabilities.

## System video codecs

The optional `libshiraz-video` adapter uses **the operating system's GStreamer
installation and decoder plugins**, rather than bundling FFmpeg or video codec
plugins in every AppImage. A Linux installation does not necessarily include
video decoders. Install GStreamer core, its app/video base plugins, and the
required video decoder plugins through your distribution's package manager.
For example, the libav plugin supplies a software H.264 decoder; GPU decoding
also requires the matching GStreamer hardware plugin and a working GPU driver.

Use `gst-inspect-1.0 avdec_h264` to check the software H.264 decoder. Hardware
plugin names depend on the GPU and distribution. Decoder selection follows OS
plugin priorities; an available software decoder provides a fallback when the
OS cannot select a working hardware decoder. A missing decoder is reported as
unsupported, not advertised as successful playback. Run logs include
`SHIRAZ_VIDEO` decoder diagnostics. Restart the app after installing plugins.

Decoded frames currently cross Shiraz's shared-memory surface transport and are
uploaded to an EGL external texture for GPU sampling. This path is **not
zero-copy**, even when an OS plugin decodes on the GPU. Hardware decoding must
be verified from the selected decoder, not inferred from the GPU renderer log.
Encrypted/DRM video is not supported by this adapter.

Firefox also ships its own software audio/video codecs inside its APK. Shiraz
exposes NDK media-format entry points so those libraries can load, even when
native NDK MediaCodec instances are unavailable. Native codec creation returns
null in that case; the Java MediaCodec API remains the OS decoder bridge.
This enables Firefox's bundled Opus decoder without downloading another codec.
YouTube streams using H.264 still require an OS H.264 decoder as described above.

Build the optional adapter with `make system-video`; this requires GStreamer
app/video development headers. `make appimage APK=/absolute/path/app.apk`
includes it when available. Runtime GStreamer libraries and codec plugins remain
OS-managed. If the adapter or plugins are absent, applications may use their
own built-in codecs; formats requiring a missing OS codec cannot play.

Audio output uses the host ALSA/PipeWire device through Shiraz's OpenSL ES
bridge. Opus decoding, when available, loads the OS `libopus.so.0`; the optional
adapter needs Opus development headers at build time. Currently this supports
mono/stereo Opus mapping family zero. Microphone capture is a separate,
permission-controlled path; this playback bridge does not open capture devices.

Validation on the development host includes Intel VA-API H.264 decoding and
forced software H.264 decoding, successive video frames sampled through EGL,
background animation callbacks, and a PCM signal/callback test for OpenSL ES.
These checks do not imply that every GPU driver, codec profile, or website works.

Firefox compatibility is still in progress: the tested local H.264 clip plays,
but YouTube's Opus decoder selection remains unresolved in this build. Do not
interpret the native Opus smoke test as successful YouTube playback.

Firefox's Android font defaults are provided by bundled Roboto, Noto Serif,
Droid Sans Mono, and Noto Naskh Arabic, alongside the existing Noto script and
emoji fonts. These prevent ordinary digits and spaces from falling back to
emoji metrics when a requested Android font is absent. Font downloads are
version-pinned and SHA-256 checked by `scripts/bootstrap_fonts.py`; Apache/OFL
notices ship with the fonts. Host font configuration is not modified.
