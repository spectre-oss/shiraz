// SPDX-License-Identifier: GPL-3.0-only
// Link-time declarations only; these bodies must never execute.
.text
.global __android_log_print
.type __android_log_print, %function
__android_log_print:
 brk #0
.global __android_log_vprint
.type __android_log_vprint, %function
__android_log_vprint:
 brk #0
