// SPDX-License-Identifier: GPL-3.0-only
// Deliberately corrupt ONLY this dedicated child frame's saved stack canary.
// The actual bionic/guest __stack_chk_fail implementation must terminate it.
.text
.p2align 2
.global differential_corrupt_guard
.type differential_corrupt_guard, %function
differential_corrupt_guard:
.cfi_startproc
    stp x29, x30, [sp, #-32]!
.cfi_def_cfa_offset 32
.cfi_offset x29, -32
.cfi_offset x30, -24
    mov x29, sp
    mrs x9, tpidr_el0
    ldr x9, [x9, #40]
    str x9, [sp, #16]
    ldr x10, [sp, #16]
    eor x10, x10, #1
    str x10, [sp, #16]
    ldr x10, [sp, #16]
    mrs x9, tpidr_el0
    ldr x9, [x9, #40]
    cmp x10, x9
    b.eq 1f
    bl __stack_chk_fail
    brk #0
1:
    ldp x29, x30, [sp], #32
.cfi_def_cfa_offset 0
    ret
.cfi_endproc
.size differential_corrupt_guard, .-differential_corrupt_guard
