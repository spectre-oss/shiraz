// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.global fixture_entry
.type fixture_entry,%function
fixture_entry:
    adrp x8, fixture_table
    add x8, x8, :lo12:fixture_table
    ldr x8, [x8]
    br x8
.size fixture_entry, .-fixture_entry
.p2align 2
.global fixture_set_table
.type fixture_set_table,%function
fixture_set_table:
    adrp x8, fixture_table
    add x8, x8, :lo12:fixture_table
    str x0, [x8]
    ret
.size fixture_set_table, .-fixture_set_table
.p2align 2
.type private_tls_target,%function
private_tls_target:
    mrs x9, tpidr_el0
    mov w0, #41
    ret
.size private_tls_target, .-private_tls_target
.p2align 2
.type private_syscall_target,%function
private_syscall_target:
    mov x8, #172
    svc #0
    ret
.size private_syscall_target, .-private_syscall_target
.p2align 2
.type private_call_target,%function
private_call_target:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    adrp x8, fixture_nested
    add x8, x8, :lo12:fixture_nested
    ldr x8, [x8]
    blr x8
    add w0, w0, #1
    ldp x29, x30, [sp], #16
    ret
.size private_call_target, .-private_call_target
.p2align 2
.type private_nested_target,%function
private_nested_target:
    mov w0, #90
    ret
.size private_nested_target, .-private_nested_target
.p2align 2
.type private_host_call_target,%function
private_host_call_target:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    adrp x8, fixture_host_slot
    add x8, x8, :lo12:fixture_host_slot
    ldr x8, [x8]
    blr x8
    add w0, w0, #1
    ldp x29, x30, [sp], #16
    ret
.size private_host_call_target, .-private_host_call_target
.p2align 2
.type private_integer_target,%function
private_integer_target:
    add x0, x0, x1
    add x0, x0, x2
    add x0, x0, x3
    add x0, x0, x4
    add x0, x0, x5
    add x0, x0, x6
    add x0, x0, x7
    ldr x9, [sp]
    ldr x10, [sp, #8]
    add x0, x0, x9
    add x0, x0, x10
    ret
.size private_integer_target, .-private_integer_target
.p2align 2
.type private_float_target,%function
private_float_target:
    fadd d0, d0, d1
    ret
.size private_float_target, .-private_float_target
.p2align 2
.type private_direct_entry,%function
private_direct_entry:
    mov w0, #7
    ret
.size private_direct_entry, .-private_direct_entry
.p2align 2
.type private_jni_target,%function
private_jni_target:
    mov w0, #6
    movk w0, #1, lsl #16
    ret
.size private_jni_target, .-private_jni_target
.p2align 2
.type private_register_target,%function
private_register_target:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    adrp x2, fixture_methods
    add x2, x2, :lo12:fixture_methods
    mov w3, #1
    ldr x8, [x0]
    ldr x8, [x8, #(215 * 8)]
    blr x8
    ldp x29, x30, [sp], #16
    ret
.size private_register_target, .-private_register_target
.p2align 2
.type private_property_callback,%function
private_property_callback:
    ldr w8, [x1]
    add w8, w8, #1
    str w8, [x1]
    ret
.size private_property_callback, .-private_property_callback
.section .rodata,"a",%progbits
fixture_method_name:
    .asciz "nativeFixture"
fixture_method_signature:
    .asciz "()I"
.section .data.rel.ro,"aw",%progbits
.p2align 3
fixture_methods:
    .xword fixture_method_name
    .xword fixture_method_signature
    .xword private_jni_target
.data
.p2align 3
.global fixture_table
.protected fixture_table
.type fixture_table,%object
fixture_table:
    .xword private_tls_target
.size fixture_table, .-fixture_table
.global fixture_second
.protected fixture_second
.type fixture_second,%object
fixture_second:
    .xword private_syscall_target
.size fixture_second, .-fixture_second
.global fixture_third
.protected fixture_third
.type fixture_third,%object
fixture_third:
    .xword private_call_target
.size fixture_third, .-fixture_third
.global fixture_nested
.protected fixture_nested
.type fixture_nested,%object
fixture_nested:
    .xword private_nested_target
.size fixture_nested, .-fixture_nested
.global fixture_fourth
.protected fixture_fourth
.type fixture_fourth,%object
fixture_fourth:
    .xword private_host_call_target
.size fixture_fourth, .-fixture_fourth
.global fixture_host_slot
.protected fixture_host_slot
.type fixture_host_slot,%object
fixture_host_slot:
    .xword 0
.size fixture_host_slot, .-fixture_host_slot
.global fixture_fifth
.protected fixture_fifth
.type fixture_fifth,%object
fixture_fifth:
    .xword private_integer_target
.size fixture_fifth, .-fixture_fifth
.global fixture_sixth
.protected fixture_sixth
.type fixture_sixth,%object
fixture_sixth:
    .xword private_float_target
.size fixture_sixth, .-fixture_sixth
.global fixture_seventh
.protected fixture_seventh
.type fixture_seventh,%object
fixture_seventh:
    .xword private_direct_entry
.size fixture_seventh, .-fixture_seventh
.global fixture_eighth
.protected fixture_eighth
.type fixture_eighth,%object
fixture_eighth:
    .xword private_jni_target
.size fixture_eighth, .-fixture_eighth
.global fixture_ninth
.protected fixture_ninth
.type fixture_ninth,%object
fixture_ninth:
    .xword private_register_target
.size fixture_ninth, .-fixture_ninth
.global fixture_tenth
.protected fixture_tenth
.type fixture_tenth,%object
fixture_tenth:
    .xword private_property_callback
.size fixture_tenth, .-fixture_tenth
