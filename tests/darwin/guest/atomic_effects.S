// SPDX-License-Identifier: GPL-3.0-only
// First-party architectural effect fixture. No Android library is copied here.
// The protected entry binds locally so a PLT cannot clobber test sentinels.
.text
.balign 16
.global atomic_effects_add32
.protected atomic_effects_add32
.type atomic_effects_add32, %function
atomic_effects_add32:
 .cfi_startproc
 .inst 0xd503245f                    // bti c
 adrp x16, atomic_effects_flag
 ldrb w16, [x16, :lo12:atomic_effects_flag]
 cbz w16, .Latomic_llsc
 .inst 0xb8e00020                    // ldaddal w0,w0,[x1]
 ret
.Latomic_llsc:
 mov w16, w0
.Latomic_retry:
 ldaxr w0, [x1]
 add w17, w0, w16
 stlxr w15, w17, [x1]
 cbnz w15, .Latomic_retry
 ret
 .cfi_endproc
.size atomic_effects_add32, .-atomic_effects_add32

// AAPCS64 arguments: x0=data, w1=increment, x2=Capture*, w3=LSE selector.
// Capture offsets are independently asserted in atomic_effects_probe.cpp.
.balign 16
.global atomic_effects_capture
.type atomic_effects_capture, %function
atomic_effects_capture:
 .cfi_startproc
 stp x29, x30, [sp, #-64]!
 .cfi_def_cfa_offset 64
 .cfi_offset 29, -64
 .cfi_offset 30, -56
 stp x19, x20, [sp, #16]
 .cfi_offset 19, -48
 .cfi_offset 20, -40
 stp x21, x22, [sp, #32]
 .cfi_offset 21, -32
 .cfi_offset 22, -24
 mov x29, sp
 mov x19, x2
 mov x20, sp
 mov x9, x0
 mov w0, w1
 mov x1, x9
 adrp x9, atomic_effects_flag
 strb w3, [x9, :lo12:atomic_effects_flag]
 movz x15, #0x7788
 movk x15, #0x5566, lsl #16
 movk x15, #0x3344, lsl #32
 movk x15, #0x1122, lsl #48
 movz x17, #0x2211
 movk x17, #0x4433, lsl #16
 movk x17, #0x6655, lsl #32
 movk x17, #0x8877, lsl #48
 movz x16, #0xbeef
 movk x16, #0xdead, lsl #16
 movk x16, #0xbeef, lsl #32
 movk x16, #0xdead, lsl #48
 movz x9, #0xa000, lsl #16
 msr nzcv, x9
 str x9, [x19, #56]
 mrs x9, fpcr
 str x9, [x19, #80]
 mrs x9, fpsr
 str x9, [x19, #96]
 movi v0.16b, #0x5a
 movi v31.16b, #0xa5
 bl atomic_effects_add32
.Latomic_after:
 stp x0, x15, [x19]
 stp x16, x17, [x19, #16]
 str x1, [x19, #32]
 mov x9, sp
 sub x9, x9, x20
 str x9, [x19, #40]
 adr x9, .Latomic_after
 sub x9, x30, x9
 str x9, [x19, #48]
 mrs x9, nzcv
 str x9, [x19, #64]
 mrs x9, fpcr
 str x9, [x19, #88]
 mrs x9, fpsr
 str x9, [x19, #104]
 str q0, [x19, #112]
 str q31, [x19, #128]
 mov x0, #0
 ldp x19, x20, [sp, #16]
 .cfi_restore 19
 .cfi_restore 20
 ldp x21, x22, [sp, #32]
 .cfi_restore 21
 .cfi_restore 22
 ldp x29, x30, [sp]
 .cfi_restore 29
 .cfi_restore 30
 add sp, sp, #64
 .cfi_def_cfa_offset 0
 ret
 .cfi_endproc
.size atomic_effects_capture, .-atomic_effects_capture

.bss
.balign 1
.global atomic_effects_flag
.hidden atomic_effects_flag
.type atomic_effects_flag, %object
atomic_effects_flag:
 .zero 1
.size atomic_effects_flag, 1
.section .note.GNU-stack,"",%progbits

// Exact CAS64 forms, selected by explicit ordering rather than a symbol rule.
.text
.arch armv8.1-a+lse
.macro cas_helper name, operation, load, store
.balign 16
.global \name
.protected \name
.type \name, %function
\name:
 .cfi_startproc
 .inst 0xd503245f
 adrp x16, atomic_effects_flag
 ldrb w16, [x16, :lo12:atomic_effects_flag]
 cbz w16, .Lcas_llsc\@
 \operation x0, x1, [x2]
 ret
.Lcas_llsc\@:
 mov x16, x0
.Lcas_retry\@:
 \load x0, [x2]
 cmp x0, x16
 b.ne .Lcas_done\@
 \store w17, x1, [x2]
 cbnz w17, .Lcas_retry\@
.Lcas_done\@:
 ret
 .cfi_endproc
.size \name, .-\name
.endm
cas_helper atomic_effects_cas64, cas, ldxr, stxr
cas_helper atomic_effects_casa64, casa, ldaxr, stxr
cas_helper atomic_effects_casal64, casal, ldaxr, stlxr

// x0=data, x1=compare, x2=desired, x3=Capture*, w4=LSE, w5=ordering.
.balign 16
.global atomic_effects_cas_capture
.type atomic_effects_cas_capture, %function
atomic_effects_cas_capture:
 .cfi_startproc
 stp x29, x30, [sp, #-48]!
 .cfi_def_cfa_offset 48
 .cfi_offset 29, -48
 .cfi_offset 30, -40
 stp x19, x20, [sp, #16]
 .cfi_offset 19, -32
 .cfi_offset 20, -24
 stp x21, x22, [sp, #32]
 .cfi_offset 21, -16
 .cfi_offset 22, -8
 mov x29, sp
 mov x19, x3
 mov x20, sp
 mov x21, x5
 mov x9, x0
 mov x0, x1
 mov x1, x2
 mov x2, x9
 adrp x9, atomic_effects_flag
 strb w4, [x9, :lo12:atomic_effects_flag]
 movz x15, #0x7788
 movk x15, #0x5566, lsl #16
 movk x15, #0x3344, lsl #32
 movk x15, #0x1122, lsl #48
 movz x17, #0x2211
 movk x17, #0x4433, lsl #16
 movk x17, #0x6655, lsl #32
 movk x17, #0x8877, lsl #48
 movz x9, #0xa000, lsl #16
 msr nzcv, x9
 str x9, [x19, #56]
 mrs x9, fpcr
 str x9, [x19, #80]
 mrs x9, fpsr
 str x9, [x19, #96]
 movi v0.16b, #0x5a
 movi v31.16b, #0xa5
 cbz w21, .Lcas_plain
 sub w21, w21, #1
 cbz w21, .Lcas_acquire
 adr x22, .Lcas_after_release
 bl atomic_effects_casal64
.Lcas_after_release:
 b .Lcas_capture_done
.Lcas_plain:
 adr x22, .Lcas_after_plain
 bl atomic_effects_cas64
.Lcas_after_plain:
 b .Lcas_capture_done
.Lcas_acquire:
 adr x22, .Lcas_after_acquire
 bl atomic_effects_casa64
.Lcas_after_acquire:
.Lcas_capture_done:
 stp x0, x15, [x19]
 stp x16, x17, [x19, #16]
 str x2, [x19, #32]
 str x1, [x19, #72]
 mov x9, sp
 sub x9, x9, x20
 str x9, [x19, #40]
 sub x9, x30, x22
 str x9, [x19, #48]
 mrs x9, nzcv
 str x9, [x19, #64]
 mrs x9, fpcr
 str x9, [x19, #88]
 mrs x9, fpsr
 str x9, [x19, #104]
 str q0, [x19, #112]
 str q31, [x19, #128]
 mov x0, #0
 ldp x19, x20, [sp, #16]
 .cfi_restore 19
 .cfi_restore 20
 ldp x21, x22, [sp, #32]
 .cfi_restore 21
 .cfi_restore 22
 ldp x29, x30, [sp]
 .cfi_restore 29
 .cfi_restore 30
 add sp, sp, #48
 .cfi_def_cfa_offset 0
 ret
 .cfi_endproc
.size atomic_effects_cas_capture, .-atomic_effects_cas_capture
