// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.global literal_bits
.type literal_bits,%function
literal_bits:
    adrp x0, guard_literal
    add x0, x0, :lo12:guard_literal
    ldr w0, [x0]
    ret
.size literal_bits,.-literal_bits
.p2align 2
.global guard_literal
.protected guard_literal
.type guard_literal,%object
guard_literal:
    .word 0xd53bd040
.size guard_literal,4

.p2align 2
.global guard_checked
.type guard_checked,%function
guard_checked:
    paciasp
    stp x29, x30, [sp, #-32]!
    mov x29, sp
    mrs x9, tpidr_el0
    ldr x9, [x9, #40]
    str x9, [sp, #16]
    cbz w0, 1f
    str xzr, [sp, #16]
1:
    mrs x10, tpidr_el0
    ldr x10, [x10, #40]
    ldr x9, [sp, #16]
    cmp x9, x10
    b.ne 2f
    mov w0, #73
    ldp x29, x30, [sp], #32
    autiasp
    ret
2:
    bl __stack_chk_fail
    brk #0
.size guard_checked,.-guard_checked

// A stripped-style JNI callback: no dynamic function symbol and no FDE.
// Its only code provenance is the registration table used by reachable code.
.p2align 2
.global register_fixture
.type register_fixture,%function
register_fixture:
    stp x29, x30, [sp, #-16]!
    mov x29, sp
    adrp x2, jni_method_table
    add x2, x2, :lo12:jni_method_table
    mov w3, #1
    ldr x8, [x0]
    ldr x8, [x8, #(215 * 8)]
    blr x8
    ldp x29, x30, [sp], #16
    ret
.size register_fixture,.-register_fixture
.p2align 2
.type private_jni_callback,%function
private_jni_callback:
    mrs x9, tpidr_el0
    ldr x9, [x9, #40]
    mov w0, #97
    ret
.size private_jni_callback,.-private_jni_callback

.section .rodata,"a",%progbits
jni_method_name:
    .asciz "nativeFixture"
jni_method_signature:
    .asciz "()I"
.section .data.rel.ro,"aw",%progbits
.p2align 3
.global jni_method_table
.protected jni_method_table
.type jni_method_table,%object
jni_method_table:
    .xword jni_method_name
    .xword jni_method_signature
    .xword private_jni_callback
.size jni_method_table,.-jni_method_table
