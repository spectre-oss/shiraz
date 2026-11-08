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

// Typed size-only malloc boundary. Save guest callee state in one explicit
// frame, then quarantine register copies before any C/C++ dispatcher runs.
// The saved state remains visible to unwinding; no native-memory purity or
// pre-write stack-safety claim follows from this entry.
.p2align 2
.globl _shiraz_guest_managed_malloc_entry
_shiraz_guest_managed_malloc_entry:
.cfi_startproc
    sub sp, sp, #160
.cfi_def_cfa sp, 160
    stp x19, x20, [sp, #0]
.cfi_offset x19, -160
.cfi_offset x20, -152
    stp x21, x22, [sp, #16]
.cfi_offset x21, -144
.cfi_offset x22, -136
    stp x23, x24, [sp, #32]
.cfi_offset x23, -128
.cfi_offset x24, -120
    stp x25, x26, [sp, #48]
.cfi_offset x25, -112
.cfi_offset x26, -104
    stp x27, x28, [sp, #64]
.cfi_offset x27, -96
.cfi_offset x28, -88
    stp x29, x30, [sp, #80]
.cfi_offset x29, -80
.cfi_offset x30, -72
    stp d8, d9, [sp, #96]
.cfi_offset 72, -64
.cfi_offset 73, -56
    stp d10, d11, [sp, #112]
.cfi_offset 74, -48
.cfi_offset 75, -40
    stp d12, d13, [sp, #128]
.cfi_offset 76, -32
.cfi_offset 77, -24
    stp d14, d15, [sp, #144]
.cfi_offset 78, -16
.cfi_offset 79, -8
    // x0 is size; x18 remains Darwin platform state.
    mov x1, xzr
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
    mov x19, xzr
    mov x20, xzr
    mov x21, xzr
    mov x22, xzr
    mov x23, xzr
    mov x24, xzr
    mov x25, xzr
    mov x26, xzr
    mov x27, xzr
    mov x28, xzr
    mov x29, xzr
    eor v0.16b, v0.16b, v0.16b
    eor v1.16b, v1.16b, v1.16b
    eor v2.16b, v2.16b, v2.16b
    eor v3.16b, v3.16b, v3.16b
    eor v4.16b, v4.16b, v4.16b
    eor v5.16b, v5.16b, v5.16b
    eor v6.16b, v6.16b, v6.16b
    eor v7.16b, v7.16b, v7.16b
    eor v8.16b, v8.16b, v8.16b
    eor v9.16b, v9.16b, v9.16b
    eor v10.16b, v10.16b, v10.16b
    eor v11.16b, v11.16b, v11.16b
    eor v12.16b, v12.16b, v12.16b
    eor v13.16b, v13.16b, v13.16b
    eor v14.16b, v14.16b, v14.16b
    eor v15.16b, v15.16b, v15.16b
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
    // Only the original stack address accompanies the clean size; never
    // expose the saved-register frame as a Capture to the typed body.
    add x1, sp, #160
    bl _shiraz_guest_managed_malloc_body
    // Malloc returns x0 only. Retain the v2 normal-return scratch guarantee.
    mov x1, xzr
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
    eor v0.16b, v0.16b, v0.16b
    eor v1.16b, v1.16b, v1.16b
    eor v2.16b, v2.16b, v2.16b
    eor v3.16b, v3.16b, v3.16b
    eor v4.16b, v4.16b, v4.16b
    eor v5.16b, v5.16b, v5.16b
    eor v6.16b, v6.16b, v6.16b
    eor v7.16b, v7.16b, v7.16b
    eor v8.16b, v8.16b, v8.16b
    eor v9.16b, v9.16b, v9.16b
    eor v10.16b, v10.16b, v10.16b
    eor v11.16b, v11.16b, v11.16b
    eor v12.16b, v12.16b, v12.16b
    eor v13.16b, v13.16b, v13.16b
    eor v14.16b, v14.16b, v14.16b
    eor v15.16b, v15.16b, v15.16b
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
    ldp d8, d9, [sp, #96]
.cfi_restore 72
.cfi_restore 73
    ldp d10, d11, [sp, #112]
.cfi_restore 74
.cfi_restore 75
    ldp d12, d13, [sp, #128]
.cfi_restore 76
.cfi_restore 77
    ldp d14, d15, [sp, #144]
.cfi_restore 78
.cfi_restore 79
    ldp x19, x20, [sp, #0]
.cfi_restore x19
.cfi_restore x20
    ldp x21, x22, [sp, #16]
.cfi_restore x21
.cfi_restore x22
    ldp x23, x24, [sp, #32]
.cfi_restore x23
.cfi_restore x24
    ldp x25, x26, [sp, #48]
.cfi_restore x25
.cfi_restore x26
    ldp x27, x28, [sp, #64]
.cfi_restore x27
.cfi_restore x28
    ldp x29, x30, [sp, #80]
.cfi_restore x29
.cfi_restore x30
    add sp, sp, #160
.cfi_def_cfa sp, 0
    ret
.cfi_endproc
