// SPDX-License-Identifier: GPL-3.0-only
// Exact PC labels let the host require the interrupted MRS frame in a trace.
.text
.p2align 2
.global guest_island_framed
.type guest_island_framed, %function
guest_island_framed:
.cfi_startproc
    stp x29, x30, [sp, #-16]!
.cfi_def_cfa_offset 16
.cfi_offset x29, -16
.cfi_offset x30, -8
    mov x29, sp
.cfi_def_cfa x29, 16
.global guest_island_framed_site
guest_island_framed_site:
    mrs x9, tpidr_el0
.global guest_island_framed_resume
guest_island_framed_resume:
    ldp x29, x30, [sp], #16
.cfi_def_cfa sp, 0
    ret
.cfi_endproc
.size guest_island_framed, .-guest_island_framed

.p2align 2
.global guest_island_leaf
.type guest_island_leaf, %function
guest_island_leaf:
.cfi_startproc
.cfi_def_cfa sp, 0
.global guest_island_leaf_site
guest_island_leaf_site:
    mrs x9, tpidr_el0
.global guest_island_leaf_resume
guest_island_leaf_resume:
    ret
.cfi_endproc
.size guest_island_leaf, .-guest_island_leaf

.macro outer name, child
.p2align 2
.global \name
.type \name, %function
\name:
.cfi_startproc
    stp x29, x30, [sp, #-16]!
.cfi_def_cfa_offset 16
.cfi_offset x29, -16
.cfi_offset x30, -8
    mov x29, sp
.cfi_def_cfa x29, 16
    bl \child
.global \name\()_resume
\name\()_resume:
    ldp x29, x30, [sp], #16
.cfi_def_cfa sp, 0
    ret
.cfi_endproc
.size \name, .-\name
.endm
outer guest_island_outer_framed, guest_island_framed
outer guest_island_outer_leaf, guest_island_leaf
