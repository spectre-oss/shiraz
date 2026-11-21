// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.global looper_fixture_poll
.type looper_fixture_poll,%function
looper_fixture_poll:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    bl ALooper_pollOnce
    ldp x29, x30, [sp], #16
    ret
.size looper_fixture_poll, .-looper_fixture_poll
.global looper_fixture_callback
.type looper_fixture_callback,%function
looper_fixture_callback:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    str w0, [x2]
    str w1, [x2, #4]
    ldr w8, [x2, #8]
    add w8, w8, #1
    str w8, [x2, #8]
    bl looper_fixture_observe
    mov w0, #0
    ldp x29, x30, [sp], #16
    ret
.size looper_fixture_callback, .-looper_fixture_callback
