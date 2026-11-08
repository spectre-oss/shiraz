// SPDX-License-Identifier: GPL-3.0-only
// Original AAPCS64 guest context save/restore. Words 2..23 use the ordering
// documented in Android14's opaque arm64 buffer, except x18 is never touched.
.text
.p2align 2
.globl _shiraz_guest_setjmp
_shiraz_guest_setjmp:
    mov w1, #1
    b _shiraz_guest_sigsetjmp
.p2align 2
.globl _shiraz_guest__setjmp
_shiraz_guest__setjmp:
    mov w1, #0
    b _shiraz_guest_sigsetjmp
.p2align 2
.globl _shiraz_guest_sigsetjmp
_shiraz_guest_sigsetjmp:
.cfi_startproc
    cbz x0, Ljump_bad_buffer
    tst x0, #7
    b.ne Ljump_bad_buffer
    mov x9, sp
    stp x30, x9, [x0, #16]
    stp x28, x29, [x0, #32]
    stp x26, x27, [x0, #48]
    stp x24, x25, [x0, #64]
    stp x22, x23, [x0, #80]
    stp x20, x21, [x0, #96]
    stp xzr, x19, [x0, #112]
    stp d14, d15, [x0, #128]
    stp d12, d13, [x0, #144]
    stp d10, d11, [x0, #160]
    stp d8, d9, [x0, #176]
    stp x29, x30, [sp, #-16]!
.cfi_def_cfa_offset 16
.cfi_offset x29, -16
.cfi_offset x30, -8
    mov x29, sp
.cfi_def_cfa x29, 16
    bl _shiraz_guest_jump_record
    ldp x29, x30, [sp], #16
.cfi_def_cfa sp, 0
    mov w0, #0
    ret
.cfi_endproc
.p2align 2
.globl _shiraz_guest_longjmp
_shiraz_guest_longjmp:
.cfi_startproc
    stp x0, x1, [sp, #-32]!
.cfi_def_cfa_offset 32
    stp x29, x30, [sp, #16]
.cfi_offset x29, -16
.cfi_offset x30, -8
    add x29, sp, #16
.cfi_def_cfa x29, 16
    add x1, sp, #32
    bl _shiraz_guest_jump_validate
    ldp x9, x1, [sp]
    add sp, sp, #32
.cfi_def_cfa sp, 0
    ldp x30, x10, [x9, #16]
    ldp x28, x29, [x9, #32]
    ldp x26, x27, [x9, #48]
    ldp x24, x25, [x9, #64]
    ldp x22, x23, [x9, #80]
    ldp x20, x21, [x9, #96]
    ldr x19, [x9, #120]
    ldp d14, d15, [x9, #128]
    ldp d12, d13, [x9, #144]
    ldp d10, d11, [x9, #160]
    ldp d8, d9, [x9, #176]
    cmp w1, #0
    csinc w0, w1, wzr, ne
    mov sp, x10
    ret
.cfi_endproc

.p2align 2
Ljump_bad_buffer:
    b _shiraz_guest_jump_bad_buffer
