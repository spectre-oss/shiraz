// SPDX-License-Identifier: GPL-3.0-only
// Link-only declaration; executable provider replaces this trap.
.text
.global log2l
.type log2l, %function
log2l:
 brk #0
.global sqrtl
.type sqrtl, %function
sqrtl:
 brk #0
.global powl
.type powl, %function
powl:
 brk #0
