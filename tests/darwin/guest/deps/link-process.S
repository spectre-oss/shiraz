// SPDX-License-Identifier: GPL-3.0-only
// Link-only symbols; every body traps. The guest personality supplies real
// implementations at execution, and these artifacts are never run.
.text
.macro declaration name
.global \name
.type \name,%function
\name:
    brk #0
.size \name,.-\name
.endm
declaration syscall
declaration openlog
declaration syslog
declaration closelog
declaration android_set_abort_message
declaration exit
declaration _exit
