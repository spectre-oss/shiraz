// SPDX-License-Identifier: GPL-3.0-only
// MRS replacement entry. x16=context, x17=entry; originals are at [sp].
// No system thread-pointer register is modified. The DWARF return column is
// PC (32), independent of original x30: an MRS is not a call, so the interrupted
// guest PC must be restored without overwriting a frameless function's LR.
// Exact synchronous accessor backtrace coverage is verified by --islands; this
// does not claim asynchronous-signal or guest exception/LSDA coverage.
.text
.p2align 2
.globl _shiraz_guest_tpidr_entry
_shiraz_guest_tpidr_entry:
.cfi_startproc
.cfi_return_column 32
.cfi_def_cfa_offset 16
    sub sp, sp, #832
.cfi_def_cfa_offset 848
    stp x0, x1, [sp, #0]
    stp x2, x3, [sp, #16]
    stp x4, x5, [sp, #32]
    stp x6, x7, [sp, #48]
    stp x8, x9, [sp, #64]
    stp x10, x11, [sp, #80]
    stp x12, x13, [sp, #96]
    stp x14, x15, [sp, #112]
    stp x18, x19, [sp, #144]
    stp x20, x21, [sp, #160]
    stp x22, x23, [sp, #176]
    stp x24, x25, [sp, #192]
    stp x26, x27, [sp, #208]
    stp x28, x29, [sp, #224]
    str x30, [sp, #240]
    str x16, [sp, #784]
    add x9, sp, #832
    ldp x9, x10, [x9]
    stp x9, x10, [sp, #128]
    mrs x9, nzcv
    str x9, [sp, #248]
    mrs x9, fpcr
    str x9, [sp, #256]
    mrs x9, fpsr
    str x9, [sp, #264]
    stp q0, q1, [sp, #272]
    stp q2, q3, [sp, #304]
    stp q4, q5, [sp, #336]
    stp q6, q7, [sp, #368]
    stp q8, q9, [sp, #400]
    stp q10, q11, [sp, #432]
    stp q12, q13, [sp, #464]
    stp q14, q15, [sp, #496]
    stp q16, q17, [sp, #528]
    stp q18, q19, [sp, #560]
    stp q20, q21, [sp, #592]
    stp q22, q23, [sp, #624]
    stp q24, q25, [sp, #656]
    stp q26, q27, [sp, #688]
    stp q28, q29, [sp, #720]
    stp q30, q31, [sp, #752]
    // The island's unused literal at context-16 holds exact guest site+4.
    // Keep that PC distinct from the saved original x30 in the frame record.
    ldr x9, [x16, #-16]
    str x9, [sp, #816]
.cfi_offset 32, -32
    add x9, sp, #800
    stp x29, x30, [x9]
.cfi_offset x29, -48
.cfi_offset x30, -40
    add x29, sp, #800
.cfi_def_cfa x29, 48
    ldr x16, [sp, #784]
    ldr x16, [x16, #16]
    // SVC dispatch takes Linux x8 as its seventh integer argument. TLS
    // accessors ignore arguments. Original x6 is restored from the frame.
    mov x6, x8
    blr x16
    ldr x16, [sp, #784]
    ldr w17, [x16, #8]
    str x0, [sp, x17, lsl #3]
    ldp x9, x10, [sp, #128]
    add x11, sp, #832
    stp x9, x10, [x11]
    ldr x9, [sp, #256]
    msr fpcr, x9
    ldr x9, [sp, #264]
    msr fpsr, x9
    ldr x9, [sp, #248]
    msr nzcv, x9
    ldp q0, q1, [sp, #272]
    ldp q2, q3, [sp, #304]
    ldp q4, q5, [sp, #336]
    ldp q6, q7, [sp, #368]
    ldp q8, q9, [sp, #400]
    ldp q10, q11, [sp, #432]
    ldp q12, q13, [sp, #464]
    ldp q14, q15, [sp, #496]
    ldp q16, q17, [sp, #528]
    ldp q18, q19, [sp, #560]
    ldp q20, q21, [sp, #592]
    ldp q22, q23, [sp, #624]
    ldp q24, q25, [sp, #656]
    ldp q26, q27, [sp, #688]
    ldp q28, q29, [sp, #720]
    ldp q30, q31, [sp, #752]
    ldr x16, [sp, #784]
    ldr x16, [x16]
.cfi_def_cfa sp, 848
    ldp x0, x1, [sp, #0]
    ldp x2, x3, [sp, #16]
    ldp x4, x5, [sp, #32]
    ldp x6, x7, [sp, #48]
    ldp x8, x9, [sp, #64]
    ldp x10, x11, [sp, #80]
    ldp x12, x13, [sp, #96]
    ldp x14, x15, [sp, #112]
    ldp x18, x19, [sp, #144]
    ldp x20, x21, [sp, #160]
    ldp x22, x23, [sp, #176]
    ldp x24, x25, [sp, #192]
    ldp x26, x27, [sp, #208]
    ldp x28, x29, [sp, #224]
    ldr x30, [sp, #240]
    add sp, sp, #832
.cfi_def_cfa_offset 16
    br x16
.cfi_endproc

