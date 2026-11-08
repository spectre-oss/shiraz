// SPDX-License-Identifier: GPL-3.0-only
// First-party fixture only. The actual malloc entry comes from production
// invoke.S; this file interposes only its typed body to capture raw inputs.
// Link a private providers.cpp object with its body symbol renamed to
// shiraz_test_actual_managed_malloc_body. No production entry is copied here.
.text
.p2align 2
.globl _shiraz_guest_managed_malloc_body
_shiraz_guest_managed_malloc_body:
.cfi_startproc
    // Capture every input before any C++ instruction or scratch use.
    // The last16 bytes stay untouched until the lower entry canary is read.
    sub sp, sp, #800
.cfi_def_cfa_offset 800
    stp x0, x1, [sp, #0]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    stp x8, x9, [sp, #64]
    stp x10, x11, [sp, #80]
    stp x12, x13, [sp, #96]
    stp x14, x15, [sp, #112]
    stp x16, x17, [sp, #128]
    stp x18, x19, [sp, #144]
    stp x20, x21, [sp, #160]
    stp x22, x23, [sp, #176]
    stp x24, x25, [sp, #192]
    stp x26, x27, [sp, #208]
    stp x28, x29, [sp, #224]
    str x30, [sp, #240]
    add x9, sp, #800
    str x9, [sp, #248]
    stp q0, q1, [sp, #256]
    stp q2, q3, [sp, #288]
    stp q4, q5, [sp, #320]
    stp q6, q7, [sp, #352]
    stp q8, q9, [sp, #384]
    stp q10, q11, [sp, #416]
    stp q12, q13, [sp, #448]
    stp q14, q15, [sp, #480]
    stp q16, q17, [sp, #512]
    stp q18, q19, [sp, #544]
    stp q20, q21, [sp, #576]
    stp q22, q23, [sp, #608]
    stp q24, q25, [sp, #640]
    stp q26, q27, [sp, #672]
    stp q28, q29, [sp, #704]
    stp q30, q31, [sp, #736]
    str x29, [sp, #768]
    str x30, [sp, #776]
.cfi_offset x29, -32
.cfi_offset x30, -24
    // Entry lower canary: body incoming SP-16, not entry frame storage.
    ldr x1, [sp, #784]
    mov x0, sp
    bl _shiraz_test_malloc_record_body
Lshiraz_test_malloc_return_from_record:
    ldr x29, [sp, #768]
.cfi_restore x29
    ldr x30, [sp, #776]
.cfi_restore x30
    add sp, sp, #800
.cfi_def_cfa_offset 0
    ret
.cfi_endproc

.p2align 2
.globl _shiraz_test_malloc_probe_invoke
_shiraz_test_malloc_probe_invoke:
.cfi_startproc
    // Save host ABI state above future guest SP; native body grows below it.
    sub sp, sp, #192
.cfi_def_cfa_offset 192
    stp x19, x20, [sp, #0]
    stp x21, x22, [sp, #16]
    stp x23, x24, [sp, #32]
    stp x25, x26, [sp, #48]
    stp x27, x28, [sp, #64]
    stp x29, x30, [sp, #80]
    stp d8, d9, [sp, #96]
    stp d10, d11, [sp, #112]
    stp d12, d13, [sp, #128]
    stp d14, d15, [sp, #144]
.cfi_offset x19, -192
.cfi_offset x20, -184
.cfi_offset x21, -176
.cfi_offset x22, -168
.cfi_offset x23, -160
.cfi_offset x24, -152
.cfi_offset x25, -144
.cfi_offset x26, -136
.cfi_offset x27, -128
.cfi_offset x28, -120
.cfi_offset x29, -112
.cfi_offset x30, -104
.cfi_offset 72, -96
.cfi_offset 73, -88
.cfi_offset 74, -80
.cfi_offset 75, -72
.cfi_offset 76, -64
.cfi_offset 77, -56
.cfi_offset 78, -48
.cfi_offset 79, -40
    str x1, [sp, #160]
    str x0, [sp, #168]
    mov x9, sp
    str x9, [x1, #0]
    str x18, [x1, #8]
    mov x9, #0x6b6b
    str x9, [sp, #176]
    // Reserve before writing lower canary; restore SP directly before entry.
    sub sp, sp, #192
.cfi_def_cfa_offset 384
    mov x9, #0x5a5a
    str x9, [sp, #16]
    add sp, sp, #192
.cfi_def_cfa_offset 192
    // x2 is the live prepared guest TP. Never alter native x18.
    mov x1, x2
    mov x3, x1
    mov x4, x1
    mov x5, x1
    mov x6, x1
    mov x7, x1
    mov x8, x1
    mov x9, x1
    mov x10, x1
    mov x11, x1
    mov x12, x1
    mov x13, x1
    mov x14, x1
    mov x15, x1
    mov x16, x1
    mov x17, x1
    mov x19, x1
    mov x20, x1
    mov x21, x1
    mov x22, x1
    mov x23, x1
    mov x24, x1
    mov x25, x1
    mov x26, x1
    mov x27, x1
    mov x28, x1
    mov x29, x1
    dup v0.2d, x1
    dup v1.2d, x1
    dup v2.2d, x1
    dup v3.2d, x1
    dup v4.2d, x1
    dup v5.2d, x1
    dup v6.2d, x1
    dup v7.2d, x1
    dup v8.2d, x1
    dup v9.2d, x1
    dup v10.2d, x1
    dup v11.2d, x1
    dup v12.2d, x1
    dup v13.2d, x1
    dup v14.2d, x1
    dup v15.2d, x1
    dup v16.2d, x1
    dup v17.2d, x1
    dup v18.2d, x1
    dup v19.2d, x1
    dup v20.2d, x1
    dup v21.2d, x1
    dup v22.2d, x1
    dup v23.2d, x1
    dup v24.2d, x1
    dup v25.2d, x1
    dup v26.2d, x1
    dup v27.2d, x1
    dup v28.2d, x1
    dup v29.2d, x1
    dup v30.2d, x1
    dup v31.2d, x1
    mov x2, x1
    bl _shiraz_guest_managed_malloc_entry
Lshiraz_test_malloc_return_from_entry:
    // Snapshot scratch before the recorder itself reuses scratch GPRs.
    str x9, [sp, #168]
    ldr x9, [sp, #160]
    str x1, [x9, #184]
    str x2, [x9, #192]
    str x3, [x9, #200]
    str x4, [x9, #208]
    str x5, [x9, #216]
    str x6, [x9, #224]
    str x7, [x9, #232]
    str x8, [x9, #240]
    str x10, [x9, #256]
    str x11, [x9, #264]
    str x12, [x9, #272]
    str x13, [x9, #280]
    str x14, [x9, #288]
    str x15, [x9, #296]
    str x16, [x9, #304]
    str x17, [x9, #312]
    ldr x10, [sp, #168]
    str x10, [x9, #248]
    stp q0, q1, [x9, #320]
    stp q2, q3, [x9, #352]
    stp q4, q5, [x9, #384]
    stp q6, q7, [x9, #416]
    stp q8, q9, [x9, #448]
    stp q10, q11, [x9, #480]
    stp q12, q13, [x9, #512]
    stp q14, q15, [x9, #544]
    stp q16, q17, [x9, #576]
    stp q18, q19, [x9, #608]
    stp q20, q21, [x9, #640]
    stp q22, q23, [x9, #672]
    stp q24, q25, [x9, #704]
    stp q26, q27, [x9, #736]
    stp q28, q29, [x9, #768]
    stp q30, q31, [x9, #800]
    ldr x9, [sp, #160]
    str x0, [x9, #16]
    str x18, [x9, #832]
    stp x19, x20, [x9, #24]
    stp x21, x22, [x9, #40]
    stp x23, x24, [x9, #56]
    stp x25, x26, [x9, #72]
    stp x27, x28, [x9, #88]
    str x29, [x9, #104]
    stp d8, d9, [x9, #112]
    stp d10, d11, [x9, #128]
    stp d12, d13, [x9, #144]
    stp d14, d15, [x9, #160]
    ldr x10, [sp, #176]
    str x10, [x9, #176]
    ldp d8, d9, [sp, #96]
.cfi_restore 72
.cfi_restore 73
    ldp d10, d11, [sp, #112]
.cfi_restore 74
.cfi_restore 75
    ldp d12, d13, [sp, #128]
.cfi_restore 76
.cfi_restore 77
    ldp d14, d15, [sp, #144]
.cfi_restore 78
.cfi_restore 79
    ldp x19, x20, [sp, #0]
.cfi_restore x19
.cfi_restore x20
    ldp x21, x22, [sp, #16]
.cfi_restore x21
.cfi_restore x22
    ldp x23, x24, [sp, #32]
.cfi_restore x23
.cfi_restore x24
    ldp x25, x26, [sp, #48]
.cfi_restore x25
.cfi_restore x26
    ldp x27, x28, [sp, #64]
.cfi_restore x27
.cfi_restore x28
    ldp x29, x30, [sp, #80]
.cfi_restore x29
.cfi_restore x30
    add sp, sp, #192
.cfi_def_cfa_offset 0
    ret
.cfi_endproc

.section __DATA,__const
.p2align 3
.globl _shiraz_test_malloc_return_from_record
_shiraz_test_malloc_return_from_record:
    .quad Lshiraz_test_malloc_return_from_record
.globl _shiraz_test_malloc_return_from_entry
_shiraz_test_malloc_return_from_entry:
    .quad Lshiraz_test_malloc_return_from_entry
