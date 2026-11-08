// SPDX-License-Identifier: GPL-3.0-only
// Link-time declarations only; every body traps.
.text
.macro declaration name
.global \name
.type \name, %function
\name:
 brk #0
.endm
declaration dlopen
declaration dlclose
declaration dlsym
declaration dlvsym
declaration dlerror
declaration dladdr
declaration dl_iterate_phdr
