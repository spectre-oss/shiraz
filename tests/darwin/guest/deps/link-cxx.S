// SPDX-License-Identifier: GPL-3.0-only
// Link-only declarations. Every body traps.
.text
.global __cxa_guard_acquire
.type __cxa_guard_acquire, %function
__cxa_guard_acquire:
 brk #0
.global __cxa_guard_release
.type __cxa_guard_release, %function
__cxa_guard_release:
 brk #0
.global __cxa_guard_abort
.type __cxa_guard_abort, %function
__cxa_guard_abort:
 brk #0
.global __cxa_pure_virtual
.type __cxa_pure_virtual, %function
__cxa_pure_virtual:
 brk #0
.global _Znwm
.type _Znwm, %function
_Znwm:
 brk #0
.global _Znam
.type _Znam, %function
_Znam:
 brk #0
.global _ZnwmRKSt9nothrow_t
.type _ZnwmRKSt9nothrow_t, %function
_ZnwmRKSt9nothrow_t:
 brk #0
.global _ZnamRKSt9nothrow_t
.type _ZnamRKSt9nothrow_t, %function
_ZnamRKSt9nothrow_t:
 brk #0
.global _ZdlPv
.type _ZdlPv, %function
_ZdlPv:
 brk #0
.global _ZdaPv
.type _ZdaPv, %function
_ZdaPv:
 brk #0
.global _ZdlPvRKSt9nothrow_t
.type _ZdlPvRKSt9nothrow_t, %function
_ZdlPvRKSt9nothrow_t:
 brk #0
.global _ZdaPvRKSt9nothrow_t
.type _ZdaPvRKSt9nothrow_t, %function
_ZdaPvRKSt9nothrow_t:
 brk #0
.section .rodata
.global _ZSt7nothrow
.type _ZSt7nothrow, %object
_ZSt7nothrow:
 .byte 0
.size _ZSt7nothrow, 1
