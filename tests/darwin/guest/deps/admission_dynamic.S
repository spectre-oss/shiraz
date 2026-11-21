// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.global admission_dynamic_walk
.type admission_dynamic_walk,%function
admission_dynamic_walk:
    stp x29, x30, [sp, #-32]!
    str x19, [sp, #16]
    mov x29, sp
    mov x19, x0
    mov x0, #0
    adrp x1, iterator_name
    add x1, x1, :lo12:iterator_name
    bl dlsym
    cbz x0, failure
    mov x8, x0
    adrp x0, callback
    add x0, x0, :lo12:callback
    cbz x19, 1f
    mov x0, #1
1:
    mov x1, #0
.global admission_dynamic_transfer
.type admission_dynamic_transfer,%function
admission_dynamic_transfer:
    blr x8
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
failure:
    mov w0, #-1
    ldr x19, [sp, #16]
    ldp x29, x30, [sp], #32
    ret
.size admission_dynamic_walk, .-admission_dynamic_walk
.type callback,%function
callback:
    cmp x1, #64
    b.ne callback_bad
    ldrh w8, [x0, #24]
    cbz w8, callback_bad
    mov w0, #23
    ret
callback_bad:
    mov w0, #-2
    ret
.section .rodata
iterator_name: .asciz "dl_iterate_phdr"
.text
.p2align 2
.global admission_dynamic_pthread_self
.type admission_dynamic_pthread_self,%function
admission_dynamic_pthread_self:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    bl pthread_self
    ldp x29, x30, [sp], #16
    ret
.size admission_dynamic_pthread_self, .-admission_dynamic_pthread_self
.text
.p2align 2
.global admission_dynamic_once
.type admission_dynamic_once,%function
admission_dynamic_once:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    adrp x0, once_control
    add x0, x0, :lo12:once_control
    adrp x1, admission_dynamic_once_callback
    add x1, x1, :lo12:admission_dynamic_once_callback
    bl pthread_once
    cbnz w0, 1f
    adrp x8, once_count
    ldr w0, [x8, :lo12:once_count]
1:  ldp x29, x30, [sp], #16
    ret
.size admission_dynamic_once, .-admission_dynamic_once
.global admission_dynamic_once_callback
.protected admission_dynamic_once_callback
.type admission_dynamic_once_callback,%function
admission_dynamic_once_callback:
    adrp x8, once_count
    ldr w9, [x8, :lo12:once_count]
    add w9, w9, #1
    str w9, [x8, :lo12:once_count]
    ret
.size admission_dynamic_once_callback, .-admission_dynamic_once_callback
.bss
.p2align 2
once_control: .word 0
once_count: .word 0
.text
.p2align 2
.global admission_dynamic_thread
.type admission_dynamic_thread,%function
admission_dynamic_thread:
    stp x29, x30, [sp, #-32]!
    mov x29, sp
    adrp x0, thread_key
    add x0, x0, :lo12:thread_key
    adrp x1, admission_dynamic_key_destructor
    add x1, x1, :lo12:admission_dynamic_key_destructor
    bl pthread_key_create
    cbnz w0, 4f
    add x0, sp, #16
    mov x1, #0
    adrp x2, admission_dynamic_thread_callback
    add x2, x2, :lo12:admission_dynamic_thread_callback
    mov x3, #0
    bl pthread_create
    cbnz w0, 3f
    ldr x0, [sp, #16]
    add x1, sp, #24
    bl pthread_join
    cbnz w0, 3f
    ldr x8, [sp, #24]
    cmp x8, #42
    b.ne 3f
    adrp x8, thread_destructed
    ldr w8, [x8, :lo12:thread_destructed]
    cmp w8, #1
    b.ne 3f
    adrp x8, thread_key
    ldr w0, [x8, :lo12:thread_key]
    bl pthread_key_delete
    cmp w0, #0
    cset w0, eq
    b 4f
3:  mov w0, #0
4:  ldp x29, x30, [sp], #32
    ret
.size admission_dynamic_thread, .-admission_dynamic_thread
.global admission_dynamic_thread_callback
.protected admission_dynamic_thread_callback
.type admission_dynamic_thread_callback,%function
admission_dynamic_thread_callback:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    bl pthread_self
    cbz x0, 5f
    adrp x8, thread_key
    ldr w0, [x8, :lo12:thread_key]
    mov x1, #42
    bl pthread_setspecific
    cbnz w0, 5f
    adrp x0, admission_dynamic_cxx_destructor
    add x0, x0, :lo12:admission_dynamic_cxx_destructor
    mov x1, #42
    mov x2, #0
    bl __cxa_thread_atexit_impl
    cbnz w0, 5f
    mov x0, #42
    b 6f
5:  mov x0, #0
6:  ldp x29, x30, [sp], #16
    ret
.size admission_dynamic_thread_callback, .-admission_dynamic_thread_callback
.global admission_dynamic_key_destructor
.protected admission_dynamic_key_destructor
.type admission_dynamic_key_destructor,%function
admission_dynamic_key_destructor:
    cmp x0, #42
    b.ne 7f
    adrp x8, thread_cxx_destructed
    ldr w9, [x8, :lo12:thread_cxx_destructed]
    cmp w9, #1
    b.ne 7f
    adrp x8, thread_destructed
    ldr w9, [x8, :lo12:thread_destructed]
    add w9, w9, #1
    str w9, [x8, :lo12:thread_destructed]
7:  ret
.size admission_dynamic_key_destructor, .-admission_dynamic_key_destructor
.global admission_dynamic_cxx_destructor
.protected admission_dynamic_cxx_destructor
.type admission_dynamic_cxx_destructor,%function
admission_dynamic_cxx_destructor:
    cmp x0, #42
    b.ne 8f
    adrp x8, thread_cxx_destructed
    mov w9, #1
    str w9, [x8, :lo12:thread_cxx_destructed]
8:  ret
.size admission_dynamic_cxx_destructor, .-admission_dynamic_cxx_destructor
.bss
.p2align 2
thread_key: .word 0
thread_destructed: .word 0
thread_cxx_destructed: .word 0
