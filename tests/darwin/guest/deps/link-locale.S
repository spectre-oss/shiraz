// SPDX-License-Identifier: GPL-3.0-only
// Link-only declarations; each body traps.
.text
.global __ctype_get_mb_cur_max
.type __ctype_get_mb_cur_max, %function
__ctype_get_mb_cur_max:
 brk #0
.global btowc
.type btowc, %function
btowc:
 brk #0
.global difftime
.type difftime, %function
difftime:
 brk #0
.global duplocale
.type duplocale, %function
duplocale:
 brk #0
.global freelocale
.type freelocale, %function
freelocale:
 brk #0
.global gmtime
.type gmtime, %function
gmtime:
 brk #0
.global gmtime_r
.type gmtime_r, %function
gmtime_r:
 brk #0
.global iswalnum_l
.type iswalnum_l, %function
iswalnum_l:
 brk #0
.global iswalpha_l
.type iswalpha_l, %function
iswalpha_l:
 brk #0
.global iswblank_l
.type iswblank_l, %function
iswblank_l:
 brk #0
.global iswcntrl_l
.type iswcntrl_l, %function
iswcntrl_l:
 brk #0
.global iswdigit_l
.type iswdigit_l, %function
iswdigit_l:
 brk #0
.global iswgraph_l
.type iswgraph_l, %function
iswgraph_l:
 brk #0
.global iswlower_l
.type iswlower_l, %function
iswlower_l:
 brk #0
.global iswprint_l
.type iswprint_l, %function
iswprint_l:
 brk #0
.global iswpunct_l
.type iswpunct_l, %function
iswpunct_l:
 brk #0
.global iswspace_l
.type iswspace_l, %function
iswspace_l:
 brk #0
.global iswupper_l
.type iswupper_l, %function
iswupper_l:
 brk #0
.global iswxdigit_l
.type iswxdigit_l, %function
iswxdigit_l:
 brk #0
.global localeconv
.type localeconv, %function
localeconv:
 brk #0
.global localtime
.type localtime, %function
localtime:
 brk #0
.global localtime_r
.type localtime_r, %function
localtime_r:
 brk #0
.global mbrlen
.type mbrlen, %function
mbrlen:
 brk #0
.global mbrtowc
.type mbrtowc, %function
mbrtowc:
 brk #0
.global mbsinit
.type mbsinit, %function
mbsinit:
 brk #0
.global mbsnrtowcs
.type mbsnrtowcs, %function
mbsnrtowcs:
 brk #0
.global mbsrtowcs
.type mbsrtowcs, %function
mbsrtowcs:
 brk #0
.global mbtowc
.type mbtowc, %function
mbtowc:
 brk #0
.global mktime
.type mktime, %function
mktime:
 brk #0
.global nanosleep
.type nanosleep, %function
nanosleep:
 brk #0
.global newlocale
.type newlocale, %function
newlocale:
 brk #0
.global setlocale
.type setlocale, %function
setlocale:
 brk #0
.global strcoll
.type strcoll, %function
strcoll:
 brk #0
.global strcoll_l
.type strcoll_l, %function
strcoll_l:
 brk #0
.global strftime
.type strftime, %function
strftime:
 brk #0
.global strftime_l
.type strftime_l, %function
strftime_l:
 brk #0
.global strtod_l
.type strtod_l, %function
strtod_l:
 brk #0
.global strtof_l
.type strtof_l, %function
strtof_l:
 brk #0
.global strtol_l
.type strtol_l, %function
strtol_l:
 brk #0
.global strtoll_l
.type strtoll_l, %function
strtoll_l:
 brk #0
.global strtoul_l
.type strtoul_l, %function
strtoul_l:
 brk #0
.global strtoull_l
.type strtoull_l, %function
strtoull_l:
 brk #0
.global strxfrm
.type strxfrm, %function
strxfrm:
 brk #0
.global strxfrm_l
.type strxfrm_l, %function
strxfrm_l:
 brk #0
.global time
.type time, %function
time:
 brk #0
.global towlower_l
.type towlower_l, %function
towlower_l:
 brk #0
.global towupper_l
.type towupper_l, %function
towupper_l:
 brk #0
.global uselocale
.type uselocale, %function
uselocale:
 brk #0
.global wcrtomb
.type wcrtomb, %function
wcrtomb:
 brk #0
.global wcscoll
.type wcscoll, %function
wcscoll:
 brk #0
.global wcscoll_l
.type wcscoll_l, %function
wcscoll_l:
 brk #0
.global wcsnrtombs
.type wcsnrtombs, %function
wcsnrtombs:
 brk #0
.global wcsrtombs
.type wcsrtombs, %function
wcsrtombs:
 brk #0
.global wcsxfrm
.type wcsxfrm, %function
wcsxfrm:
 brk #0
.global wcsxfrm_l
.type wcsxfrm_l, %function
wcsxfrm_l:
 brk #0
.global wctob
.type wctob, %function
wctob:
 brk #0
.global sscanf
.type sscanf, %function
sscanf:
 brk #0
.global vsscanf
.type vsscanf, %function
vsscanf:
 brk #0
.global sysconf
.type sysconf, %function
sysconf:
 brk #0
.global getauxval
.type getauxval, %function
getauxval:
 brk #0
.global getpid
.type getpid, %function
getpid:
 brk #0
.global getuid
.type getuid, %function
getuid:
 brk #0
.global geteuid
.type geteuid, %function
geteuid:
 brk #0
.global sched_yield
.type sched_yield, %function
sched_yield:
 brk #0
.global setjmp
.type setjmp, %function
setjmp:
 brk #0
.global _setjmp
.type _setjmp, %function
_setjmp:
 brk #0
.global sigsetjmp
.type sigsetjmp, %function
sigsetjmp:
 brk #0
.global longjmp
.type longjmp, %function
longjmp:
 brk #0
.global _longjmp
.type _longjmp, %function
_longjmp:
 brk #0
.global siglongjmp
.type siglongjmp, %function
siglongjmp:
 brk #0
.global strspn
.type strspn, %function
strspn:
 brk #0
.global strcspn
.type strcspn, %function
strcspn:
 brk #0
.global strdup
.type strdup, %function
strdup:
 brk #0
.global strerror
.type strerror, %function
strerror:
 brk #0
.global strerror_r
.type strerror_r, %function
strerror_r:
 brk #0
.global perror
.type perror, %function
perror:
 brk #0
.global rand
.type rand, %function
rand:
 brk #0
.global srand
.type srand, %function
srand:
 brk #0
.global qsort
.type qsort, %function
qsort:
 brk #0
