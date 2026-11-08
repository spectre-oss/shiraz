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
.macro SHIRAZ_CLOSURE_ENTRY name, scalar
.p2align 2
.globl \name
\name:
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
.cfi_def_cfa sp, 272
    ldp x29, x30, [sp, #256]
.cfi_restore x29
.cfi_restore x30
    add sp, sp, #272
.cfi_def_cfa sp, 0
.if \scalar
    // Only the explicit scalar-result entry owns these clobber guarantees.
    // No calls or memory writes follow. Keep x18 and the result carriers.
    mov x2, xzr
    mov x3, xzr
    mov x4, xzr
    mov x5, xzr
    mov x6, xzr
    mov x7, xzr
    mov x8, xzr
    mov x9, xzr
    mov x10, xzr
    mov x11, xzr
    mov x12, xzr
    mov x13, xzr
    mov x14, xzr
    mov x15, xzr
    mov x16, xzr
    mov x17, xzr
    eor v2.16b, v2.16b, v2.16b
    eor v3.16b, v3.16b, v3.16b
    eor v4.16b, v4.16b, v4.16b
    eor v5.16b, v5.16b, v5.16b
    eor v6.16b, v6.16b, v6.16b
    eor v7.16b, v7.16b, v7.16b
    eor v16.16b, v16.16b, v16.16b
    eor v17.16b, v17.16b, v17.16b
    eor v18.16b, v18.16b, v18.16b
    eor v19.16b, v19.16b, v19.16b
    eor v20.16b, v20.16b, v20.16b
    eor v21.16b, v21.16b, v21.16b
    eor v22.16b, v22.16b, v22.16b
    eor v23.16b, v23.16b, v23.16b
    eor v24.16b, v24.16b, v24.16b
    eor v25.16b, v25.16b, v25.16b
    eor v26.16b, v26.16b, v26.16b
    eor v27.16b, v27.16b, v27.16b
    eor v28.16b, v28.16b, v28.16b
    eor v29.16b, v29.16b, v29.16b
    eor v30.16b, v30.16b, v30.16b
    eor v31.16b, v31.16b, v31.16b
    mov v8.d[1], xzr
    mov v9.d[1], xzr
    mov v10.d[1], xzr
    mov v11.d[1], xzr
    mov v12.d[1], xzr
    mov v13.d[1], xzr
    mov v14.d[1], xzr
    mov v15.d[1], xzr
.endif
    ret
.cfi_endproc
.endm
SHIRAZ_CLOSURE_ENTRY _shiraz_guest_closure_entry, 0
SHIRAZ_CLOSURE_ENTRY _shiraz_guest_scalar_closure_entry, 1
