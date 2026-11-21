// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.globl _shiraz_admission_fixture_call_asm
.globl _shiraz_admission_fixture_return
_shiraz_admission_fixture_call_asm:
    stp x29, x30, [sp, #-32]!
    stp x19, x20, [sp, #16]
    mov x29, sp
    mov x19, x0
    mov x0, x1
    adr x1, _shiraz_admission_fixture_return
    mov x2, sp
    bl _shiraz_admission_fixture_arm
    blr x19
_shiraz_admission_fixture_return:
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
