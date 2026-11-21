// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.type loader_constructor,%function
loader_constructor:
    adrp x8, loader_lifecycle
    add x8, x8, :lo12:loader_lifecycle
    ldr w9, [x8]
    add w9, w9, #1
    str w9, [x8]
    ret
.size loader_constructor, .-loader_constructor
.type loader_finalizer,%function
loader_finalizer:
    adrp x8, loader_lifecycle
    add x8, x8, :lo12:loader_lifecycle
    ldr w9, [x8]
    add w9, w9, #10
    str w9, [x8]
    ret
.size loader_finalizer, .-loader_finalizer
.global loader_entry
.type loader_entry,%function
loader_entry:
    stp x29, x30, [sp, #-32]!
    str x19, [sp, #16]
    mov x29, sp
    mov x0, #16
    bl malloc
    cbz x0, 1f
    mov x19, x0
    mov w8, #0x0201
    movk w8, #0x0403, lsl #16
    str w8, [x19]
    add x0, x19, #4
    mov x1, x19
    mov x2, #4
    bl memcpy
    add x0, x19, #1
    mov x1, x19
    mov x2, #7
    bl memmove
    ldrb w8, [x19, #1]
    cmp w8, #1
    b.ne 2f
    ldrb w8, [x19, #4]
    cmp w8, #4
    b.ne 2f
    ldrb w8, [x19, #7]
    cmp w8, #3
    b.ne 2f
    mov x0, x19
    bl free
    bl getpid
    add w0, w0, #1
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
2:
    mov x0, x19
    bl free
1:
    mov w0, #0
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
.size loader_entry, .-loader_entry
.section .init_array,"aw",%init_array
.p2align 3
.xword loader_constructor
.section .fini_array,"aw",%fini_array
.p2align 3
.xword loader_finalizer
.data
.p2align 2
.global loader_lifecycle
.protected loader_lifecycle
.type loader_lifecycle,%object
loader_lifecycle:
    .word 0
.size loader_lifecycle, .-loader_lifecycle
