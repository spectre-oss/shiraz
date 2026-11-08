// SPDX-License-Identifier: GPL-3.0-only
// Fixed integer-only AAPCS64 handler call. No C++ vector/closure allocation,
// no raw native sigaction layout, and no host x18 writes.
.text
.p2align 2
.globl _shiraz_guest_signal_call
_shiraz_guest_signal_call:
.cfi_startproc
    stp x29,x30,[sp,#-16]!
.cfi_def_cfa_offset 16
.cfi_offset x29,-16
.cfi_offset x30,-8
    mov x29,sp
.cfi_def_cfa x29,16
    mov x16,x0
    mov w0,w1
    blr x16
    ldp x29,x30,[sp],#16
.cfi_def_cfa sp,0
    ret
.cfi_endproc
