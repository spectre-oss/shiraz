// SPDX-License-Identifier: GPL-3.0-only
// Link-only declaration; execution traps.
.text
.global eglGetProcAddress
.type eglGetProcAddress, %function
eglGetProcAddress:
 brk #0
