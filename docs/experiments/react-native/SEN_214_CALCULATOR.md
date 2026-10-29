# SEN-214 Calculator

Status: **interactive basic and scientific calculations; resizing needs work**.
This is a bounded walkthrough, not a complete calculator correctness test.

## Artifact and environment

- Public source and APK: [samkiell/SEN-214-Calculator v1.0.2](https://github.com/samkiell/SEN-214-Calculator/releases/tag/v1.0.2).
- Artifact: `Calculator-v1.0.2.apk`, version name `1.0.2`, version code `1`.
- SHA-256: `ab9cdf3a69966976cc47d9662b577ff733a6383daac5bd70a73154057eb328e6`.
- APK includes x86, x86-64, ARM32 and ARM64 libraries, including Hermes.
  This walkthrough uses x86-64 ART on the separate Linux test VM.
- Expo/React Native app; the release's display component uses an embedded
  Orbitron font and `fontVariant: ['tabular-nums']`.
- Shiraz source baseline: 0.333.1 plus the OpenType and numeric-input fixes described below.
  The APK was not modified. No app execution or compilation occurred on the
  operator's desktop.

## Startup failure and runtime fix

The first run loaded Expo and React Native, then terminated while measuring
text. React Native's `CustomStyleSpan` called `Paint.setFontFeatureSettings`,
which threw `UnsupportedOperationException` for every explicit setting.

Paint now preserves feature settings across copies and resets. The native
HarfBuzz shaping path applies bounded feature lists consistently to measurement,
cluster boundaries, line breaking, bounds and Canvas drawing, including fallback
font runs. Existing callers without explicit settings retain their defaults.
This implements the requested shaping behavior instead of ignoring the setting.

A bundled-font regression fixture reproduces the former exception on ART.
With the fix, OpenJDK and ART both verify ligature-dependent advances, clusters,
pixels and bounds, plus copying, resetting and a settings length bound. The
full VM `make check` suite passes.

## Numeric dialog failure and fix

Opening the scientific permutation dialog initially terminated in
`TextView.setInputType`: numeric input required a missing key listener.
Shiraz now supplies an ASCII `DigitsKeyListener` with optional sign and decimal
handling. Editing, paste and committed text pass through its filter; replacing
a selection can replace an existing sign or point, while duplicate points and
misplaced signs are rejected. Numeric passwords are masked. Unrelated keyboard
flags are preserved rather than incorrectly rejected as numeric flags.

The focused OpenJDK and ART regression covers filtering, replacement, committed
text, backspace, password masking and switching back to ordinary text. The full
VM check suite passes. Locale-specific numeric characters are not covered by
this ASCII implementation.

## Actual app walkthrough

At 360 × 640, the unchanged APK renders its Basic keypad and digital display.
Pointer input enters `7 + 5`; pressing equals displays `12` with the expression
above it. Switching to Sci, clearing, entering `sin(30)` and pressing equals
shows `0.5` in degree mode. These are real app interactions through its normal
input path, not fixture screenshots.

At 360 × 800, the permutation dialog now opens with both numeric fields.
Entering `5` and `2`, correcting an accidental extra digit with Backspace, and
pressing Compute dismisses the dialog and displays `20` with the label
“Permutations (nPr)”. The Mean dialog accepts comma-separated `2,4,6`
and displays `4`. Submitting `abc` displays the app’s `Error` result; AC then
restores ordinary numeric entry without a crash.

A resize to 640 × 360 retains the expression/result but compresses the keypad
until buttons overlap. The upstream button component imposes a 46-unit minimum
height inside flexible rows; this is relevant to the short-window limitation,
but Android visual parity has not been tested.

Resizing back to 360 × 800 gives the keypad more room, but the unchanged result
text disappears until an input updates it. Pressing AC redraws `0`, so input
and evaluation remain live. This is an unresolved rendering/resize issue;
responsive resizing is **not** a pass. Follow-up view-tree inspection shows
that the horizontal viewport shrinks from 576 to 296 pixels while its content
remains 576 pixels wide, placing the result outside the visible area. Temporary
layout tracing found Fabric content-layout calls for the initial and expanded
widths, but none for the narrower content. The source of that missing update
remains under investigation; no app-specific geometry override was added. Closing the window returns exit status 0.
A fresh launch renders Basic mode with `0`; it does not restore the previous
scientific selection. No persistent-state feature is established by this check.

## Remaining work

Investigate stale result rendering after resize, compare compact layouts against
Android, and exercise the remaining statistical dialogs, long expressions, error
recovery and repeated restarts. No claim is made for every scientific operation,
state persistence, accessibility or general Expo/React Native compatibility.
