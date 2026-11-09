// SPDX-License-Identifier: GPL-3.0-only
// Fixture interposes only the default accessor body, never the production MRS
// assembly entry. Private tls.cpp renames its actual cached accessor.
.text
.p2align 2
.globl _shiraz_guest_tls_access
_shiraz_guest_tls_access:
.cfi_startproc
    // Capture every input before any C++ instruction or scratch use.
    // Snapshot storage and saved shim return state stay below incoming SP.
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
    mov x0, sp
    bl _shiraz_test_mrs_record_body
Lshiraz_test_mrs_return_from_record:
    ldr x29, [sp, #768]
.cfi_restore x29
    ldr x30, [sp, #776]
.cfi_restore x30
    add sp, sp, #800
.cfi_def_cfa_offset 0
    ret
.cfi_endproc

.section __DATA,__const
.p2align 3
.globl _shiraz_test_mrs_return_from_record
_shiraz_test_mrs_return_from_record:
    .quad Lshiraz_test_mrs_return_from_record
