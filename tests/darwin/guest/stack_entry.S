// SPDX-License-Identifier: GPL-3.0-only
// Test-only synchronous stack pivot. The callback catches its own exceptions;
// no unwind across this pivot or asynchronous-signal behavior is claimed.
.text
.p2align 2
.globl _shiraz_test_other_stack
_shiraz_test_other_stack:
    stp x29, x30, [sp, #-32]!
    stp x19, x20, [sp, #16]
    mov x19, sp
    mov x20, x1
    mov sp, x0
    mov x0, x2
    blr x20
    mov sp, x19
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
