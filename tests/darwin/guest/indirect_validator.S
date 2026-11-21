// SPDX-License-Identifier: GPL-3.0-only
// Observe incoming registers before any compiler-generated prologue. Only
// target/site/SP (x0-x2), host x18/x29 and callback x16 may be nonzero.
.text
.p2align 2
.globl _shiraz_indirect_test_validator
_shiraz_indirect_test_validator:
    orr x16, x3, x4
    orr x16, x16, x5
    orr x16, x16, x6
    orr x16, x16, x7
    orr x16, x16, x8
    orr x16, x16, x9
    orr x16, x16, x10
    orr x16, x16, x11
    orr x16, x16, x12
    orr x16, x16, x13
    orr x16, x16, x14
    orr x16, x16, x15
    orr x16, x16, x17
    orr x16, x16, x19
    orr x16, x16, x20
    orr x16, x16, x21
    orr x16, x16, x22
    orr x16, x16, x23
    orr x16, x16, x24
    orr x16, x16, x25
    orr x16, x16, x26
    orr x16, x16, x27
    orr x16, x16, x28
    cbnz x16, 1f
    orr v0.16b, v0.16b, v1.16b
    orr v0.16b, v0.16b, v2.16b
    orr v0.16b, v0.16b, v3.16b
    orr v0.16b, v0.16b, v4.16b
    orr v0.16b, v0.16b, v5.16b
    orr v0.16b, v0.16b, v6.16b
    orr v0.16b, v0.16b, v7.16b
    orr v0.16b, v0.16b, v8.16b
    orr v0.16b, v0.16b, v9.16b
    orr v0.16b, v0.16b, v10.16b
    orr v0.16b, v0.16b, v11.16b
    orr v0.16b, v0.16b, v12.16b
    orr v0.16b, v0.16b, v13.16b
    orr v0.16b, v0.16b, v14.16b
    orr v0.16b, v0.16b, v15.16b
    orr v0.16b, v0.16b, v16.16b
    orr v0.16b, v0.16b, v17.16b
    orr v0.16b, v0.16b, v18.16b
    orr v0.16b, v0.16b, v19.16b
    orr v0.16b, v0.16b, v20.16b
    orr v0.16b, v0.16b, v21.16b
    orr v0.16b, v0.16b, v22.16b
    orr v0.16b, v0.16b, v23.16b
    orr v0.16b, v0.16b, v24.16b
    orr v0.16b, v0.16b, v25.16b
    orr v0.16b, v0.16b, v26.16b
    orr v0.16b, v0.16b, v27.16b
    orr v0.16b, v0.16b, v28.16b
    orr v0.16b, v0.16b, v29.16b
    orr v0.16b, v0.16b, v30.16b
    orr v0.16b, v0.16b, v31.16b
    umaxv b0, v0.16b
    umov w16, v0.b[0]
    cbnz w16, 1f
    b _validate_transfer
1:  brk #1
