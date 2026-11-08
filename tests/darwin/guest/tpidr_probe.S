// SPDX-License-Identifier: GPL-3.0-only
// Relocatable first-party test template: no external calls or data references.
// Output is 31 GPRs, SP/NZCV/FPCR/FPSR/padding, 32 full Q registers, and two
// pre-instruction references (SP and x18). x18 is read but never seeded.
.text
.p2align 4
.globl _shiraz_tpidr_test_start
.globl _shiraz_tpidr_test_site
.globl _shiraz_tpidr_test_end
_shiraz_tpidr_test_start:
    paciasp
    sub sp, sp, #1072
    str x0, [sp, #1040]
    mov x9, sp
    str x9, [sp, #800]
    str x18, [sp, #808]
    add x9, sp, #816
    stp x19, x20, [x9, #0]
    stp x21, x22, [x9, #16]
    stp x23, x24, [x9, #32]
    stp x25, x26, [x9, #48]
    stp x27, x28, [x9, #64]
    stp x29, x30, [x9, #80]
    stp q8, q9, [x9, #96]
    stp q10, q11, [x9, #128]
    stp q12, q13, [x9, #160]
    stp q14, q15, [x9, #192]
    mrs x10, fpcr
    str x10, [sp, #1048]
    mrs x10, fpsr
    str x10, [sp, #1056]
    mrs x10, nzcv
    str x10, [sp, #1064]
    movi v0.16b, #64
    movi v1.16b, #65
    movi v2.16b, #66
    movi v3.16b, #67
    movi v4.16b, #68
    movi v5.16b, #69
    movi v6.16b, #70
    movi v7.16b, #71
    movi v8.16b, #72
    movi v9.16b, #73
    movi v10.16b, #74
    movi v11.16b, #75
    movi v12.16b, #76
    movi v13.16b, #77
    movi v14.16b, #78
    movi v15.16b, #79
    movi v16.16b, #80
    movi v17.16b, #81
    movi v18.16b, #82
    movi v19.16b, #83
    movi v20.16b, #84
    movi v21.16b, #85
    movi v22.16b, #86
    movi v23.16b, #87
    movi v24.16b, #88
    movi v25.16b, #89
    movi v26.16b, #90
    movi v27.16b, #91
    movi v28.16b, #92
    movi v29.16b, #93
    movi v30.16b, #94
    movi v31.16b, #95
    mov x9, #0xa0000000
    msr nzcv, x9
    mov x9, #0x02c00000
    msr fpcr, x9
    movz x9, #0x001f
    movk x9, #0x0800, lsl #16
    msr fpsr, x9
    movz x0, #4096
    movk x0, #0x2233, lsl #16
    movk x0, #0x4455, lsl #32
    movk x0, #0x6677, lsl #48
    movz x1, #4097
    movk x1, #0x2233, lsl #16
    movk x1, #0x4455, lsl #32
    movk x1, #0x6677, lsl #48
    movz x2, #4098
    movk x2, #0x2233, lsl #16
    movk x2, #0x4455, lsl #32
    movk x2, #0x6677, lsl #48
    movz x3, #4099
    movk x3, #0x2233, lsl #16
    movk x3, #0x4455, lsl #32
    movk x3, #0x6677, lsl #48
    movz x4, #4100
    movk x4, #0x2233, lsl #16
    movk x4, #0x4455, lsl #32
    movk x4, #0x6677, lsl #48
    movz x5, #4101
    movk x5, #0x2233, lsl #16
    movk x5, #0x4455, lsl #32
    movk x5, #0x6677, lsl #48
    movz x6, #4102
    movk x6, #0x2233, lsl #16
    movk x6, #0x4455, lsl #32
    movk x6, #0x6677, lsl #48
    movz x7, #4103
    movk x7, #0x2233, lsl #16
    movk x7, #0x4455, lsl #32
    movk x7, #0x6677, lsl #48
    movz x8, #4104
    movk x8, #0x2233, lsl #16
    movk x8, #0x4455, lsl #32
    movk x8, #0x6677, lsl #48
    movz x9, #4105
    movk x9, #0x2233, lsl #16
    movk x9, #0x4455, lsl #32
    movk x9, #0x6677, lsl #48
    movz x10, #4106
    movk x10, #0x2233, lsl #16
    movk x10, #0x4455, lsl #32
    movk x10, #0x6677, lsl #48
    movz x11, #4107
    movk x11, #0x2233, lsl #16
    movk x11, #0x4455, lsl #32
    movk x11, #0x6677, lsl #48
    movz x12, #4108
    movk x12, #0x2233, lsl #16
    movk x12, #0x4455, lsl #32
    movk x12, #0x6677, lsl #48
    movz x13, #4109
    movk x13, #0x2233, lsl #16
    movk x13, #0x4455, lsl #32
    movk x13, #0x6677, lsl #48
    movz x14, #4110
    movk x14, #0x2233, lsl #16
    movk x14, #0x4455, lsl #32
    movk x14, #0x6677, lsl #48
    movz x15, #4111
    movk x15, #0x2233, lsl #16
    movk x15, #0x4455, lsl #32
    movk x15, #0x6677, lsl #48
    movz x16, #4112
    movk x16, #0x2233, lsl #16
    movk x16, #0x4455, lsl #32
    movk x16, #0x6677, lsl #48
    movz x17, #4113
    movk x17, #0x2233, lsl #16
    movk x17, #0x4455, lsl #32
    movk x17, #0x6677, lsl #48
    movz x19, #4115
    movk x19, #0x2233, lsl #16
    movk x19, #0x4455, lsl #32
    movk x19, #0x6677, lsl #48
    movz x20, #4116
    movk x20, #0x2233, lsl #16
    movk x20, #0x4455, lsl #32
    movk x20, #0x6677, lsl #48
    movz x21, #4117
    movk x21, #0x2233, lsl #16
    movk x21, #0x4455, lsl #32
    movk x21, #0x6677, lsl #48
    movz x22, #4118
    movk x22, #0x2233, lsl #16
    movk x22, #0x4455, lsl #32
    movk x22, #0x6677, lsl #48
    movz x23, #4119
    movk x23, #0x2233, lsl #16
    movk x23, #0x4455, lsl #32
    movk x23, #0x6677, lsl #48
    movz x24, #4120
    movk x24, #0x2233, lsl #16
    movk x24, #0x4455, lsl #32
    movk x24, #0x6677, lsl #48
    movz x25, #4121
    movk x25, #0x2233, lsl #16
    movk x25, #0x4455, lsl #32
    movk x25, #0x6677, lsl #48
    movz x26, #4122
    movk x26, #0x2233, lsl #16
    movk x26, #0x4455, lsl #32
    movk x26, #0x6677, lsl #48
    movz x27, #4123
    movk x27, #0x2233, lsl #16
    movk x27, #0x4455, lsl #32
    movk x27, #0x6677, lsl #48
    movz x28, #4124
    movk x28, #0x2233, lsl #16
    movk x28, #0x4455, lsl #32
    movk x28, #0x6677, lsl #48
    movz x29, #4125
    movk x29, #0x2233, lsl #16
    movk x29, #0x4455, lsl #32
    movk x29, #0x6677, lsl #48
    movz x30, #4126
    movk x30, #0x2233, lsl #16
    movk x30, #0x4455, lsl #32
    movk x30, #0x6677, lsl #48
_shiraz_tpidr_test_site:
    mrs x0, tpidr_el0
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
    mov x9, sp
    str x9, [sp, #248]
    mrs x9, nzcv
    str x9, [sp, #256]
    mrs x9, fpcr
    str x9, [sp, #264]
    mrs x9, fpsr
    str x9, [sp, #272]
    str xzr, [sp, #280]
    stp q0, q1, [sp, #288]
    stp q2, q3, [sp, #320]
    stp q4, q5, [sp, #352]
    stp q6, q7, [sp, #384]
    stp q8, q9, [sp, #416]
    stp q10, q11, [sp, #448]
    stp q12, q13, [sp, #480]
    stp q14, q15, [sp, #512]
    stp q16, q17, [sp, #544]
    stp q18, q19, [sp, #576]
    stp q20, q21, [sp, #608]
    stp q22, q23, [sp, #640]
    stp q24, q25, [sp, #672]
    stp q26, q27, [sp, #704]
    stp q28, q29, [sp, #736]
    stp q30, q31, [sp, #768]
    ldr x0, [sp, #1040]
    mov x1, sp
    mov x2, #816
1:
    ldp x3, x4, [x1], #16
    stp x3, x4, [x0], #16
    subs x2, x2, #16
    b.ne 1b
    ldr x10, [sp, #1048]
    msr fpcr, x10
    ldr x10, [sp, #1056]
    msr fpsr, x10
    ldr x10, [sp, #1064]
    msr nzcv, x10
    add x9, sp, #816
    ldp q8, q9, [x9, #96]
    ldp q10, q11, [x9, #128]
    ldp q12, q13, [x9, #160]
    ldp q14, q15, [x9, #192]
    ldp x19, x20, [x9, #0]
    ldp x21, x22, [x9, #16]
    ldp x23, x24, [x9, #32]
    ldp x25, x26, [x9, #48]
    ldp x27, x28, [x9, #64]
    ldp x29, x30, [x9, #80]
    add sp, sp, #1072
    autiasp
    ret
_shiraz_tpidr_test_end:

// A real two-read stack-guard pattern, with a deliberate corruption argument.
.p2align 4
.globl _shiraz_tpidr_guard_start
.globl _shiraz_tpidr_guard_first
.globl _shiraz_tpidr_guard_second
.globl _shiraz_tpidr_guard_failure
.globl _shiraz_tpidr_guard_end
_shiraz_tpidr_guard_start:
    paciasp
    stp x29, x30, [sp, #-32]!
    mov x29, sp
_shiraz_tpidr_guard_first:
    mrs x1, tpidr_el0
    ldr x1, [x1, #40]
    str x1, [sp, #16]
    cbz x0, 1f
    str xzr, [sp, #16]
1:
_shiraz_tpidr_guard_second:
    mrs x1, tpidr_el0
    ldr x1, [x1, #40]
    ldr x2, [sp, #16]
    cmp x1, x2
    b.ne 2f
    mov w0, #73
    ldp x29, x30, [sp], #32
    autiasp
    ret
2:
    ldr x16, _shiraz_tpidr_guard_failure
    blr x16
    brk #0
.p2align 3
_shiraz_tpidr_guard_failure:
    .quad 0
_shiraz_tpidr_guard_end:

// Stress accessor calls the real TLS getter, then deliberately overwrites
// caller registers and FP status/control. The production trampoline must
// restore the guest state before returning to its original instruction stream.
.p2align 2
.globl _shiraz_tpidr_stress_access
_shiraz_tpidr_stress_access:
.cfi_startproc
    stp x29, x30, [sp, #-32]!
.cfi_def_cfa_offset 32
.cfi_offset x29, -32
.cfi_offset x30, -24
    mov x29, sp
    bl _shiraz_guest_tls_access
    str x0, [sp, #16]
    mov x0, #8192
    mov x1, #8193
    mov x2, #8194
    mov x3, #8195
    mov x4, #8196
    mov x5, #8197
    mov x6, #8198
    mov x7, #8199
    mov x8, #8200
    mov x9, #8201
    mov x10, #8202
    mov x11, #8203
    mov x12, #8204
    mov x13, #8205
    mov x14, #8206
    mov x15, #8207
    mov x16, #8208
    mov x17, #8209
    movi v0.16b, #0xee
    movi v1.16b, #0xee
    movi v2.16b, #0xee
    movi v3.16b, #0xee
    movi v4.16b, #0xee
    movi v5.16b, #0xee
    movi v6.16b, #0xee
    movi v7.16b, #0xee
    movi v16.16b, #0xee
    movi v17.16b, #0xee
    movi v18.16b, #0xee
    movi v19.16b, #0xee
    movi v20.16b, #0xee
    movi v21.16b, #0xee
    movi v22.16b, #0xee
    movi v23.16b, #0xee
    movi v24.16b, #0xee
    movi v25.16b, #0xee
    movi v26.16b, #0xee
    movi v27.16b, #0xee
    movi v28.16b, #0xee
    movi v29.16b, #0xee
    movi v30.16b, #0xee
    movi v31.16b, #0xee
    ins v8.d[1], v31.d[0]
    ins v9.d[1], v31.d[0]
    ins v10.d[1], v31.d[0]
    ins v11.d[1], v31.d[0]
    ins v12.d[1], v31.d[0]
    ins v13.d[1], v31.d[0]
    ins v14.d[1], v31.d[0]
    ins v15.d[1], v31.d[0]
    msr nzcv, xzr
    msr fpcr, xzr
    msr fpsr, xzr
    ldr x0, [sp, #16]
    ldp x29, x30, [sp], #32
.cfi_def_cfa_offset 0
    ret
.cfi_endproc

