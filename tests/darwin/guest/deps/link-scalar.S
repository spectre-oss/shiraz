// SPDX-License-Identifier: GPL-3.0-only
// Link-only declarations; bodies trap.
.text
.global atoi
.type atoi, %function
atoi:
 brk #0
.global atol
.type atol, %function
atol:
 brk #0
.global atoll
.type atoll, %function
atoll:
 brk #0
.global atof
.type atof, %function
atof:
 brk #0
.global strtof
.type strtof, %function
strtof:
 brk #0
.global strtod
.type strtod, %function
strtod:
 brk #0
.global wcslen
.type wcslen, %function
wcslen:
 brk #0
.global wcschr
.type wcschr, %function
wcschr:
 brk #0
.global wcscmp
.type wcscmp, %function
wcscmp:
 brk #0
.global wmemchr
.type wmemchr, %function
wmemchr:
 brk #0
.global wmemcmp
.type wmemcmp, %function
wmemcmp:
 brk #0
.global wmemmove
.type wmemmove, %function
wmemmove:
 brk #0
.global wcstol
.type wcstol, %function
wcstol:
 brk #0
.global wcstoul
.type wcstoul, %function
wcstoul:
 brk #0
.global wcstoll
.type wcstoll, %function
wcstoll:
 brk #0
.global wcstoull
.type wcstoull, %function
wcstoull:
 brk #0
.global wcstof
.type wcstof, %function
wcstof:
 brk #0
.global wcstod
.type wcstod, %function
wcstod:
 brk #0
.global isatty
.type isatty, %function
isatty:
 brk #0
.global getcwd
.type getcwd, %function
getcwd:
 brk #0
.global realpath
.type realpath, %function
realpath:
 brk #0
.global remove
.type remove, %function
remove:
 brk #0
.global pathconf
.type pathconf, %function
pathconf:
 brk #0
.global getenv
.type getenv, %function
getenv:
 brk #0
.global posix_memalign
.type posix_memalign, %function
posix_memalign:
 brk #0

// Link-only extra scalar / binary128 declarations; all bodies trap.
.global strstr
.type strstr, %function
strstr:
 brk #0
.size strstr, .-strstr
.global strtold
.type strtold, %function
strtold:
 brk #0
.size strtold, .-strtold
.global strtold_l
.type strtold_l, %function
strtold_l:
 brk #0
.size strtold_l, .-strtold_l
.global wcstold
.type wcstold, %function
wcstold:
 brk #0
.size wcstold, .-wcstold
.global wcstold_l
.type wcstold_l, %function
wcstold_l:
 brk #0
.size wcstold_l, .-wcstold_l

.global sendfile
.type sendfile, %function
sendfile:
 brk #0
.global sendfile64
.type sendfile64, %function
sendfile64:
 brk #0

.global swprintf
.type swprintf, %function
swprintf:
 brk #0
.global vswprintf
.type vswprintf, %function
vswprintf:
 brk #0

.global strcasecmp
.type strcasecmp, %function
strcasecmp:
 brk #0
.global strncasecmp
.type strncasecmp, %function
strncasecmp:
 brk #0
.global __strchr_chk
.type __strchr_chk, %function
__strchr_chk:
 brk #0
.global __strrchr_chk
.type __strrchr_chk, %function
__strrchr_chk:
 brk #0
.global __strncat_chk
.type __strncat_chk, %function
__strncat_chk:
 brk #0
.global uname
.type uname, %function
uname:
 brk #0
.global gettimeofday
.type gettimeofday, %function
gettimeofday:
 brk #0
.global getrlimit
.type getrlimit, %function
getrlimit:
 brk #0
.global getrlimit64
.type getrlimit64, %function
getrlimit64:
 brk #0
.global usleep
.type usleep, %function
usleep:
 brk #0
.global sleep
.type sleep, %function
sleep:
 brk #0
.global arc4random_buf
.type arc4random_buf, %function
arc4random_buf:
 brk #0
.global fchown
.type fchown, %function
fchown:
 brk #0
.global ldexp
.type ldexp, %function
ldexp:
 brk #0
.global bsearch
.type bsearch, %function
bsearch:
 brk #0

.global timegm
.type timegm, %function
timegm:
 brk #0
.global ctime
.type ctime, %function
ctime:
 brk #0
.global ctime_r
.type ctime_r, %function
ctime_r:
 brk #0

.global setenv
.type setenv, %function
setenv:
 brk #0
.global unsetenv
.type unsetenv, %function
unsetenv:
 brk #0
.global tzset
.type tzset, %function
tzset:
 brk #0

.data
.global environ
.type environ, %object
.size environ, 8
environ: .quad 0
.global tzname
.type tzname, %object
.size tzname, 16
tzname: .quad 0,0
.global timezone
.type timezone, %object
.size timezone, 8
timezone: .quad 0

.text
.global prctl
.type prctl, %function
prctl:
 brk #0
.global __assert2
.type __assert2, %function
__assert2:
 brk #0

.text
.global eventfd
.type eventfd, %function
eventfd:
 brk #0
.global eventfd_read
.type eventfd_read, %function
eventfd_read:
 brk #0
.global eventfd_write
.type eventfd_write, %function
eventfd_write:
 brk #0
.global poll
.type poll, %function
poll:
 brk #0
.global ppoll
.type ppoll, %function
ppoll:
 brk #0
.global inotify_init
.type inotify_init, %function
inotify_init:
 brk #0
.global inotify_init1
.type inotify_init1, %function
inotify_init1:
 brk #0
.global inotify_add_watch
.type inotify_add_watch, %function
inotify_add_watch:
 brk #0
.global inotify_rm_watch
.type inotify_rm_watch, %function
inotify_rm_watch:
 brk #0
.global signal
.type signal, %function
signal:
 brk #0
.global sigaction
.type sigaction, %function
sigaction:
 brk #0
.global sigemptyset
.type sigemptyset, %function
sigemptyset:
 brk #0
.global sigfillset
.type sigfillset, %function
sigfillset:
 brk #0
.global sigaddset
.type sigaddset, %function
sigaddset:
 brk #0
.global sigdelset
.type sigdelset, %function
sigdelset:
 brk #0
.global sigismember
.type sigismember, %function
sigismember:
 brk #0
.global sigprocmask
.type sigprocmask, %function
sigprocmask:
 brk #0
.global pthread_sigmask
.type pthread_sigmask, %function
pthread_sigmask:
 brk #0
.global raise
.type raise, %function
raise:
 brk #0
.global kill
.type kill, %function
kill:
 brk #0
.global waitpid
.type waitpid, %function
waitpid:
 brk #0
.global wait4
.type wait4, %function
wait4:
 brk #0
.global waitid
.type waitid, %function
waitid:
 brk #0
.global setsid
.type setsid, %function
setsid:
 brk #0
.global fork
.type fork, %function
fork:
 brk #0
.global vfork
.type vfork, %function
vfork:
 brk #0
.global execv
.type execv, %function
execv:
 brk #0
.global execve
.type execve, %function
execve:
 brk #0
.global execvp
.type execvp, %function
execvp:
 brk #0

.text
.global getaddrinfo
.type getaddrinfo, %function
getaddrinfo:
 brk #0
.global freeaddrinfo
.type freeaddrinfo, %function
freeaddrinfo:
 brk #0
.global getnameinfo
.type getnameinfo, %function
getnameinfo:
 brk #0
.global gai_strerror
.type gai_strerror, %function
gai_strerror:
 brk #0
.global socket
.type socket, %function
socket:
 brk #0
.global socketpair
.type socketpair, %function
socketpair:
 brk #0
.global connect
.type connect, %function
connect:
 brk #0
.global bind
.type bind, %function
bind:
 brk #0
.global listen
.type listen, %function
listen:
 brk #0
.global shutdown
.type shutdown, %function
shutdown:
 brk #0
.global accept4
.type accept4, %function
accept4:
 brk #0
.global accept
.type accept, %function
accept:
 brk #0
.global getsockname
.type getsockname, %function
getsockname:
 brk #0
.global getpeername
.type getpeername, %function
getpeername:
 brk #0
.global getsockopt
.type getsockopt, %function
getsockopt:
 brk #0
.global setsockopt
.type setsockopt, %function
setsockopt:
 brk #0
.global recv
.type recv, %function
recv:
 brk #0
.global sendmsg
.type sendmsg, %function
sendmsg:
 brk #0
.global recvmsg
.type recvmsg, %function
recvmsg:
 brk #0
.global __cmsg_nxthdr
.type __cmsg_nxthdr, %function
__cmsg_nxthdr:
 brk #0

.text
.global dup
.type dup, %function
dup:
 brk #0
.global dup2
.type dup2, %function
dup2:
 brk #0

.text
.global pipe2
.type pipe2, %function
pipe2:
 brk #0
.global dup3
.type dup3, %function
dup3:
 brk #0

.text
.global getpwuid_r
.type getpwuid_r, %function
getpwuid_r:
 brk #0
.global getgrgid
.type getgrgid, %function
getgrgid:
 brk #0
.global flock
.type flock, %function
flock:
 brk #0

.text
.global mremap
.type mremap, %function
mremap:
 brk #0
.global statfs
.type statfs, %function
statfs:
 brk #0
.global statfs64
.type statfs64, %function
statfs64:
 brk #0
.global fstatfs
.type fstatfs, %function
fstatfs:
 brk #0
.global fstatfs64
.type fstatfs64, %function
fstatfs64:
 brk #0

.text
.global __pthread_cleanup_push
.type __pthread_cleanup_push, %function
__pthread_cleanup_push:
 brk #0
.global __pthread_cleanup_pop
.type __pthread_cleanup_pop, %function
__pthread_cleanup_pop:
 brk #0

.global sigpending
.type sigpending, %function
sigpending:
 brk #0

// Link-time declaration only; never an executed implementation.
.global strnlen
.type strnlen, %function
strnlen:
 brk #0
