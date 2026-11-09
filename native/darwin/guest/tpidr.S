// SPDX-License-Identifier: GPL-3.0-only
// MRS replacement entry. x16=context, x17=entry; originals are at [sp].
// No system thread-pointer register is modified. The DWARF return column is
// PC (32), independent of original x30: an MRS is not a call, so the interrupted
// guest PC must be restored without overwriting a frameless function's LR.
// Synchronous accessor backtraces are checked by the fixture gates. This entry
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
.cfi_offset x19, -696
    stp x20, x21, [sp, #160]
.cfi_offset x20, -688
.cfi_offset x21, -680
    stp x22, x23, [sp, #176]
.cfi_offset x22, -672
.cfi_offset x23, -664
    stp x24, x25, [sp, #192]
.cfi_offset x24, -656
.cfi_offset x25, -648
    stp x26, x27, [sp, #208]
.cfi_offset x26, -640
.cfi_offset x27, -632
    stp x28, x29, [sp, #224]
.cfi_offset x28, -624
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
.cfi_offset 72, -448
.cfi_offset 73, -432
    stp q10, q11, [sp, #432]
.cfi_offset 74, -416
.cfi_offset 75, -400
    stp q12, q13, [sp, #464]
.cfi_offset 76, -384
.cfi_offset 77, -368
    stp q14, q15, [sp, #496]
.cfi_offset 78, -352
.cfi_offset 79, -336
    stp q16, q17, [sp, #528]
    stp q18, q19, [sp, #560]
    stp q20, q21, [sp, #592]
    stp q22, q23, [sp, #624]
    stp q24, q25, [sp, #656]
    stp q26, q27, [sp, #688]
    stp q28, q29, [sp, #720]
    stp q30, q31, [sp, #752]
    // Recover guest site+4 from the island B at context-16.
    // Sign-extend its imm26 before scaling; the planner bounded this branch.
    // Keep this PC distinct from the saved original x30 in the frame record.
    ldr w9, [x16, #-16]
    sbfx x9, x9, #0, #26
    lsl x9, x9, #2
    sub x10, x16, #16
    add x9, x10, x9
    str x9, [sp, #816]
.cfi_offset 32, -32
    add x9, sp, #800
    stp x29, x30, [x9]
.cfi_offset x29, -48
.cfi_offset x30, -40
    add x29, sp, #800
.cfi_def_cfa x29, 48
    ldr x16, [sp, #784]
    ldr w9, [x16, #12]
    cmp w9, #1
    b.ne Lshiraz_tpidr_generic_accessor
    // Only the planned default TLS accessor receives quarantined inputs.
    // x29 is this native frame, not the saved guest FP; x18 is Darwin state.
    // Saved guest values remain recoverable by CFI, so this is not a native
    // memory, asynchronous-signal or pre-write stack-safety guarantee.
    mov x0, xzr
    mov x1, xzr
    mov x2, xzr
    mov x3, xzr
    mov x4, xzr
    mov x5, xzr
    mov x6, xzr
    add x7, sp, #848
    mov x8, xzr
    mov x9, xzr
    mov x10, xzr
    mov x11, xzr
    mov x12, xzr
    mov x13, xzr
    mov x14, xzr
    mov x15, xzr
    mov x16, xzr
    mov x17, xzr
    mov x19, xzr
    mov x20, xzr
    mov x21, xzr
    mov x22, xzr
    mov x23, xzr
    mov x24, xzr
    mov x25, xzr
    mov x26, xzr
    mov x27, xzr
    mov x28, xzr
    eor v0.16b, v0.16b, v0.16b
    eor v1.16b, v1.16b, v1.16b
    eor v2.16b, v2.16b, v2.16b
    eor v3.16b, v3.16b, v3.16b
    eor v4.16b, v4.16b, v4.16b
    eor v5.16b, v5.16b, v5.16b
    eor v6.16b, v6.16b, v6.16b
    eor v7.16b, v7.16b, v7.16b
    eor v8.16b, v8.16b, v8.16b
    eor v9.16b, v9.16b, v9.16b
    eor v10.16b, v10.16b, v10.16b
    eor v11.16b, v11.16b, v11.16b
    eor v12.16b, v12.16b, v12.16b
    eor v13.16b, v13.16b, v13.16b
    eor v14.16b, v14.16b, v14.16b
    eor v15.16b, v15.16b, v15.16b
    eor v16.16b, v16.16b, v16.16b
    eor v17.16b, v17.16b, v17.16b
    eor v18.16b, v18.16b, v18.16b
    eor v19.16b, v19.16b, v19.16b
    eor v20.16b, v20.16b, v20.16b
    eor v21.16b, v21.16b, v21.16b
    eor v22.16b, v22.16b, v22.16b
    eor v23.16b, v23.16b, v23.16b
    eor v24.16b, v24.16b, v24.16b
    eor v25.16b, v25.16b, v25.16b
    eor v26.16b, v26.16b, v26.16b
    eor v27.16b, v27.16b, v27.16b
    eor v28.16b, v28.16b, v28.16b
    eor v29.16b, v29.16b, v29.16b
    eor v30.16b, v30.16b, v30.16b
    eor v31.16b, v31.16b, v31.16b
    bl _shiraz_guest_tls_access
    b Lshiraz_tpidr_accessor_return
Lshiraz_tpidr_generic_accessor:
    ldr x16, [x16, #16]
    // SVC dispatch takes Linux x8 as its seventh integer argument. Production
    // TLS access takes original guest SP as its eighth argument. Test getters
    // and seven-argument syscall dispatchers ignore that extra register.
    // Original x6/x7 are restored from the frame.
    mov x6, x8
    add x7, sp, #848
    blr x16
Lshiraz_tpidr_accessor_return:
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
.cfi_restore 72
.cfi_restore 73
    ldp q10, q11, [sp, #432]
.cfi_restore 74
.cfi_restore 75
    ldp q12, q13, [sp, #464]
.cfi_restore 76
.cfi_restore 77
    ldp q14, q15, [sp, #496]
.cfi_restore 78
.cfi_restore 79
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
.cfi_restore x19
    ldp x20, x21, [sp, #160]
.cfi_restore x20
.cfi_restore x21
    ldp x22, x23, [sp, #176]
.cfi_restore x22
.cfi_restore x23
    ldp x24, x25, [sp, #192]
.cfi_restore x24
.cfi_restore x25
    ldp x26, x27, [sp, #208]
.cfi_restore x26
.cfi_restore x27
    ldp x28, x29, [sp, #224]
.cfi_restore x28
.cfi_restore x29
    ldr x30, [sp, #240]
.cfi_restore x30
    // x17 is restored by the island after this entry returns. Preserve the
    // exact guest continuation there while clearing its old frame slot.
    ldr x17, [sp, #816]
.cfi_register 32, 17
    // Erase exactly the 832-byte shared frame after restoring
    // guest state. Each post-index store maintains 16-byte SP alignment.
    // No pre-write, asynchronous or accessor-frame purity claim follows.
.rept 52
    stp xzr, xzr, [sp], #16
.cfi_adjust_cfa_offset -16
.endr
    br x16
.cfi_endproc

