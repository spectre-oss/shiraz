// SPDX-License-Identifier: GPL-3.0-only
.text
.p2align 2
.global guestegl_poison_mask
.type guestegl_poison_mask, %function
guestegl_poison_mask:
 .cfi_startproc
 stp x29, x30, [sp, #-16]!
 .cfi_def_cfa_offset 16
 .cfi_offset x29, -16
 .cfi_offset x30, -8
 mov x29, sp
 mov x16, x0
 // AAPCS64 does not promise upper bits for unsigned-byte arguments. Only
 // the low bytes are false,true,false,true; the Apple callee needs a thunk.
 mov w0, #0x5a5a0000
 mov w1, #0x5a5a0000
 orr w1, w1, #1
 mov w2, #0x5a5a0000
 mov w3, w1
 blr x16
 ldp x29, x30, [sp], #16
 .cfi_def_cfa_offset 0
 ret
 .cfi_endproc
.size guestegl_poison_mask, .-guestegl_poison_mask
