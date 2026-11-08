// SPDX-License-Identifier: GPL-3.0-only
// Host fixed-signature call -> an AAPCS64 scalar register/stack frame.
.text
.p2align 2
.globl _shiraz_guest_invoke_asm
_shiraz_guest_invoke_asm:
.cfi_startproc
    stp x29, x30, [sp, #-32]!
.cfi_def_cfa_offset 32
.cfi_offset x29, -32
.cfi_offset x30, -24
    stp x19, x20, [sp, #16]
.cfi_offset x19, -16
.cfi_offset x20, -8
    mov x29, sp
.cfi_def_cfa x29, 32
    mov x19, x0
    mov x20, x1
    sub sp, sp, x3
    mov x9, sp
    cbz x3, 2f
1:
    ldp x10, x11, [x2], #16
    stp x10, x11, [x9], #16
    subs x3, x3, #16
    b.ne 1b
2:
    ldp q0, q1, [x19, #64]
    ldp q2, q3, [x19, #96]
    ldp q4, q5, [x19, #128]
    ldp q6, q7, [x19, #160]
    ldp x0, x1, [x19]
    ldp x2, x3, [x19, #16]
    ldp x4, x5, [x19, #32]
    ldp x6, x7, [x19, #48]
    blr x20
    stp x0, x1, [x19, #192]
    stp q0, q1, [x19, #208]
    mov sp, x29
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #32
.cfi_def_cfa sp, 0
    ret
.cfi_endproc

// A leaf closure tail-branches here with its immutable context in x17.
.p2align 2
.globl _shiraz_guest_closure_entry
_shiraz_guest_closure_entry:
.cfi_startproc
    sub sp, sp, #272
.cfi_def_cfa_offset 272
    stp x29, x30, [sp, #256]
.cfi_offset x29, -16
.cfi_offset x30, -8
    add x29, sp, #256
.cfi_def_cfa x29, 16
    stp x0, x1, [sp]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    stp q0, q1, [sp, #64]
    stp q2, q3, [sp, #96]
    stp q4, q5, [sp, #128]
    stp q6, q7, [sp, #160]
    stp xzr, xzr, [sp, #192]
    stp xzr, xzr, [sp, #208]
    stp xzr, xzr, [sp, #224]
    add x9, sp, #272
    str x9, [sp, #240]
    mov x0, x17
    mov x1, sp
    bl _shiraz_guest_closure_dispatch
    ldp x0, x1, [sp, #192]
    ldp q0, q1, [sp, #208]
    ldp x29, x30, [sp, #256]
    add sp, sp, #272
.cfi_def_cfa sp, 0
    ret
.cfi_endproc
