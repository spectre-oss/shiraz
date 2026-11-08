// SPDX-License-Identifier: GPL-3.0-only
// Link-only API declarations. Every executable body traps.
.text
.macro declaration name
.global \name
.type \name, %function
\name:
 brk #0
.endm
declaration AndroidBitmap_getInfo
declaration AndroidBitmap_lockPixels
declaration AndroidBitmap_unlockPixels
