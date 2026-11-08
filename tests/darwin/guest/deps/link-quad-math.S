// SPDX-License-Identifier: GPL-3.0-only
// Link-only declaration; executable provider replaces this trap.
.text
.global log2l
.type log2l, %function
log2l:
 brk #0
