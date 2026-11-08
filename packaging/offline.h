// SPDX-License-Identifier: GPL-3.0-only
// Direct-network restriction, not a filesystem or local-IPC sandbox.
#include <dirent.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <stddef.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>

void restrictInternet() {
    // Do not let sockets passed by the launching process bypass socket filtering.
    DIR* descriptors=opendir("/proc/self/fd");
    if(!descriptors)fail(error("--no-internet: enumerate inherited descriptors"));
    while(auto* entry=readdir(descriptors)) {
        char* end=nullptr;long fd=strtol(entry->d_name,&end,10);
        if(!*entry->d_name||*end||fd<0||fd==dirfd(descriptors))continue;
        struct stat info{};
        if(fstat(static_cast<int>(fd),&info)==0&&S_ISSOCK(info.st_mode)) {
            if(close(static_cast<int>(fd))<0){closedir(descriptors);fail(error("--no-internet: close inherited socket"));}
        }
    }
    closedir(descriptors);
#if defined(__x86_64__)
    constexpr unsigned architecture=AUDIT_ARCH_X86_64;
#elif defined(__aarch64__)
    constexpr unsigned architecture=AUDIT_ARCH_AARCH64;
#endif
    const sock_filter filter[]={
        BPF_STMT(BPF_LD|BPF_W|BPF_ABS,offsetof(seccomp_data,arch)),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,architecture,1,0),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_KILL_PROCESS),
        BPF_STMT(BPF_LD|BPF_W|BPF_ABS,offsetof(seccomp_data,nr)),
#if defined(__x86_64__)
        // Reject the alternate x32 syscall ABI too.
        BPF_JUMP(BPF_JMP|BPF_JGE|BPF_K,0x40000000,0,1),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM),
#endif
        // io_uring can create sockets without a socket syscall.
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_io_uring_setup,0,1),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_io_uring_enter,0,1),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_io_uring_register,0,1),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|EPERM),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_socket,1,0),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,__NR_socketpair,0,3),
        BPF_STMT(BPF_LD|BPF_W|BPF_ABS,offsetof(seccomp_data,args[0])),
        BPF_JUMP(BPF_JMP|BPF_JEQ|BPF_K,AF_UNIX,1,0),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ERRNO|ENETUNREACH),
        BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ALLOW),
    };
    sock_fprog program{static_cast<unsigned short>(sizeof(filter)/sizeof(filter[0])),const_cast<sock_filter*>(filter)};
    if(prctl(PR_SET_NO_NEW_PRIVS,1,0,0,0)||prctl(PR_SET_SECCOMP,SECCOMP_MODE_FILTER,&program))
        fail(error("--no-internet: cannot install network restriction"));
}
