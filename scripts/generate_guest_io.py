#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Derive bionic arm64 file ABI constants and layouts without executing ELF."""

import fcntl
import hashlib
import json
import re
import shlex
import subprocess
from pathlib import Path

import bootstrap_bionic_headers as headers
from generate_guest_errno import verify_headers

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/macos/m5"
CONSTANTS = [
    "SEM_VALUE_MAX",
    "PTHREAD_MUTEX_NORMAL",
    "PTHREAD_MUTEX_RECURSIVE",
    "PTHREAD_MUTEX_ERRORCHECK",
    "PTHREAD_PROCESS_PRIVATE",
    "PTHREAD_PROCESS_SHARED",
    "CLOCK_REALTIME",
    "CLOCK_MONOTONIC",
    "CLOCK_BOOTTIME",
    "PTHREAD_CREATE_JOINABLE",
    "PTHREAD_CREATE_DETACHED",
    "PTHREAD_STACK_MIN",
    "PTHREAD_DESTRUCTOR_ITERATIONS",
    "PROT_NONE",
    "PROT_READ",
    "PROT_WRITE",
    "PROT_EXEC",
    "MAP_SHARED",
    "MAP_PRIVATE",
    "MAP_FIXED",
    "MAP_ANONYMOUS",
    "MAP_NORESERVE",
    "MAP_FIXED_NOREPLACE",
    "MS_ASYNC",
    "MS_SYNC",
    "MS_INVALIDATE",
    "O_RDONLY",
    "O_WRONLY",
    "O_RDWR",
    "O_ACCMODE",
    "O_CREAT",
    "O_EXCL",
    "O_NOCTTY",
    "O_TRUNC",
    "O_APPEND",
    "O_NONBLOCK",
    "O_DSYNC",
    "O_SYNC",
    "O_CLOEXEC",
    "O_DIRECTORY",
    "O_NOFOLLOW",
    "O_LARGEFILE",
    "O_DIRECT",
    "O_NOATIME",
    "O_PATH",
    "O_TMPFILE",
    "O_ASYNC",
    "FD_CLOEXEC",
    "F_GETFD",
    "F_SETFD",
    "F_GETFL",
    "F_SETFL",
    "F_DUPFD",
    "F_DUPFD_CLOEXEC",
    "F_GETLK",
    "F_SETLK",
    "F_SETLKW",
    "F_RDLCK",
    "F_WRLCK",
    "F_UNLCK",
    "AT_FDCWD",
    "AT_SYMLINK_NOFOLLOW",
    "AT_SYMLINK_FOLLOW",
    "AT_REMOVEDIR",
    "AT_EACCESS",
    "AT_EMPTY_PATH",
    "SEEK_SET",
    "SEEK_CUR",
    "SEEK_END",
    "SEEK_DATA",
    "SEEK_HOLE",
    "FIONREAD",
    "FIONBIO",
    "FIOCLEX",
    "FIONCLEX",
    "S_IFMT",
    "S_IFREG",
    "S_IFDIR",
    "S_IFLNK",
    "S_IFCHR",
    "S_IFBLK",
    "S_IFIFO",
    "S_IFSOCK",
    "S_ISUID",
    "S_ISGID",
    "S_ISVTX",
    "S_IRWXU",
    "S_IRWXG",
    "S_IRWXO",
    "R_OK",
    "W_OK",
    "X_OK",
    "F_OK",
    "UTIME_NOW",
    "UTIME_OMIT",
    "ST_RDONLY",
    "ST_NOSUID",
    "ST_NODEV",
    "ST_NOEXEC",
    "ST_SYNCHRONOUS",
    "ST_NOATIME",
    "DT_REG",
    "DT_DIR",
    "DT_LNK",
    "DT_CHR",
    "DT_BLK",
    "DT_FIFO",
    "DT_SOCK",
    "DT_UNKNOWN",
]
STRUCTS = {
    "Sockaddr": ("struct sockaddr", "sa_family sa_data"),
    "SockaddrIn": ("struct sockaddr_in", "sin_family sin_port sin_addr"),
    "SockaddrIn6": ("struct sockaddr_in6", "sin6_family sin6_port sin6_flowinfo sin6_addr sin6_scope_id"),
    "SockaddrUn": ("struct sockaddr_un", "sun_family sun_path"),
    "Addrinfo": ("struct addrinfo", "ai_flags ai_family ai_socktype ai_protocol ai_addrlen ai_canonname ai_addr ai_next"),
    "Iovec": ("struct iovec", "iov_base iov_len"),
    "Msghdr": ("struct msghdr", "msg_name msg_namelen msg_iov msg_iovlen msg_control msg_controllen msg_flags"),
    "Cmsghdr": ("struct cmsghdr", "cmsg_len cmsg_level cmsg_type"),
    "Linger": ("struct linger", "l_onoff l_linger"),
    "InPktinfo": ("struct in_pktinfo", "ipi_ifindex ipi_spec_dst ipi_addr"),
    "In6Pktinfo": ("struct in6_pktinfo", "ipi6_addr ipi6_ifindex"),
    "IpMreq": ("struct ip_mreq", "imr_multiaddr imr_interface"),
    "Ipv6Mreq": ("struct ipv6_mreq", "ipv6mr_multiaddr ipv6mr_interface"),
    "PthreadRwlock": ("pthread_rwlock_t", ""),
    "Semaphore": ("sem_t", ""),
    "PthreadMutex": ("pthread_mutex_t", ""),
    "PthreadMutexAttr": ("pthread_mutexattr_t", ""),
    "PthreadCond": ("pthread_cond_t", ""),
    "PthreadCondAttr": ("pthread_condattr_t", ""),
    "PthreadOnce": ("pthread_once_t", ""),
    "PthreadAttr": (
        "pthread_attr_t",
        "flags stack_base stack_size guard_size sched_policy sched_priority",
    ),
    "Stat": (
        "struct stat",
        "st_dev st_ino st_mode st_nlink st_uid st_gid st_rdev st_size st_blksize st_blocks st_atim st_mtim st_ctim",
    ),
    "Flock": ("struct flock", "l_type l_whence l_start l_len l_pid"),
    "Dirent": ("struct dirent", "d_ino d_off d_reclen d_type d_name"),
    "Timespec": ("struct timespec", "tv_sec tv_nsec"),
    "Timeval": ("struct timeval", "tv_sec tv_usec"),
    "Tm": (
        "struct tm",
        "tm_sec tm_min tm_hour tm_mday tm_mon tm_year tm_wday tm_yday tm_isdst tm_gmtoff tm_zone",
    ),
    "Statfs": (
        "struct statfs",
        "f_type f_bsize f_blocks f_bfree f_bavail f_files f_ffree f_fsid f_namelen f_frsize f_flags",
    ),
    "Statvfs": (
        "struct statvfs",
        "f_bsize f_frsize f_blocks f_bfree f_bavail f_files f_ffree f_favail f_fsid f_flag f_namemax",
    ),
}
CONSTANTS += [
    "AF_UNSPEC", "AF_UNIX", "AF_INET", "AF_INET6", "SOCK_STREAM", "SOCK_DGRAM", "SOCK_RAW", "SOCK_SEQPACKET", "SOCK_NONBLOCK", "SOCK_CLOEXEC",
    "SOL_SOCKET", "SCM_RIGHTS", "SO_REUSEADDR", "SO_REUSEPORT", "SO_KEEPALIVE", "SO_BROADCAST", "SO_OOBINLINE", "SO_SNDBUF", "SO_RCVBUF", "SO_ERROR", "SO_TYPE", "SO_LINGER", "SO_RCVTIMEO", "SO_SNDTIMEO", "SO_RCVLOWAT", "SO_SNDLOWAT", "SO_ACCEPTCONN",
    "IPPROTO_IP", "IPPROTO_IPV6", "IPPROTO_TCP", "IPPROTO_UDP", "TCP_NODELAY", "TCP_KEEPIDLE", "TCP_KEEPINTVL", "TCP_KEEPCNT",
    "IP_TTL", "IP_TOS", "IP_MULTICAST_IF", "IP_MULTICAST_TTL", "IP_MULTICAST_LOOP", "IP_ADD_MEMBERSHIP", "IP_DROP_MEMBERSHIP", "IP_PKTINFO", "IP_RECVTTL", "IP_RECVTOS",
    "IPV6_V6ONLY", "IPV6_UNICAST_HOPS", "IPV6_MULTICAST_IF", "IPV6_MULTICAST_HOPS", "IPV6_MULTICAST_LOOP", "IPV6_JOIN_GROUP", "IPV6_LEAVE_GROUP", "IPV6_RECVPKTINFO", "IPV6_PKTINFO", "IPV6_RECVHOPLIMIT", "IPV6_HOPLIMIT", "IPV6_RECVTCLASS", "IPV6_TCLASS",
    "MSG_OOB", "MSG_PEEK", "MSG_DONTROUTE", "MSG_CTRUNC", "MSG_TRUNC", "MSG_DONTWAIT", "MSG_EOR", "MSG_WAITALL", "MSG_NOSIGNAL", "MSG_CMSG_CLOEXEC",
    "AI_PASSIVE", "AI_CANONNAME", "AI_NUMERICHOST", "AI_NUMERICSERV", "AI_ADDRCONFIG", "AI_V4MAPPED", "AI_ALL", "AI_V4MAPPED_CFG",
    "NI_NUMERICHOST", "NI_NUMERICSERV", "NI_NOFQDN", "NI_NAMEREQD", "NI_DGRAM",
    "EAI_ADDRFAMILY", "EAI_AGAIN", "EAI_BADFLAGS", "EAI_FAIL", "EAI_FAMILY", "EAI_MEMORY", "EAI_NODATA", "EAI_NONAME", "EAI_SERVICE", "EAI_SOCKTYPE", "EAI_SYSTEM", "EAI_BADHINTS", "EAI_PROTOCOL", "EAI_OVERFLOW",
    "SHUT_RD", "SHUT_WR", "SHUT_RDWR",
]


def main():
    headers.main()
    expected, groups = verify_headers()
    expressions = {name: name for name in CONSTANTS}
    for name, (kind, fields) in STRUCTS.items():
        expressions[name + "Size"] = f"sizeof({kind})"
        expressions[name + "Align"] = f"_Alignof({kind})"
        for field in fields.split():
            expressions[name + "_" + field] = f"__builtin_offsetof({kind}, {field})"
            expressions[name + "_" + field + "Width"] = f"sizeof((({kind}*)0)->{field})"
    expressions.update(
        {
            "MutexNormalInitializer": "((pthread_mutex_t)PTHREAD_MUTEX_INITIALIZER).__private[0]",
            "MutexRecursiveInitializer": "((pthread_mutex_t)PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP).__private[0]",
            "MutexErrorcheckInitializer": "((pthread_mutex_t)PTHREAD_ERRORCHECK_MUTEX_INITIALIZER_NP).__private[0]",
            "CondInitializer": "((pthread_cond_t)PTHREAD_COND_INITIALIZER).__private[0]",
            "CondMonotonicInitializer": "((pthread_cond_t)PTHREAD_COND_INITIALIZER_MONOTONIC_NP).__private[0]",
            "OnceInitializer": "PTHREAD_ONCE_INIT",
        }
    )
    expressions["DevEncodingSample"] = "makedev(0xabc, 0x123456)"
    expressions["CmsgDataOffset"] = "CMSG_LEN(0)"
    expressions["CmsgAlignment"] = "CMSG_ALIGN(1)"
    OUT.mkdir(parents=True, exist_ok=True)
    source = OUT / "io-abi.c"
    source.write_text(
        "#define _GNU_SOURCE 1\n#include <stddef.h>\n#include <pthread.h>\n#include <semaphore.h>\n#include <fcntl.h>\n#include <unistd.h>\n#include <sys/stat.h>\n#include <sys/mman.h>\n#include <sys/time.h>\n#include <time.h>\n#include <sys/vfs.h>\n#include <sys/statvfs.h>\n#include <sys/sysmacros.h>\n#include <sys/ioctl.h>\n#include <dirent.h>\n#include <sys/socket.h>\n#include <sys/un.h>\n#include <sys/uio.h>\n#include <netdb.h>\n#include <netinet/in.h>\n#include <netinet/tcp.h>\n"
        + "\n".join(
            f"const unsigned long long abi_{name} = (unsigned long long)({value});"
            for name, value in expressions.items()
        )
        + "\n"
    )
    compiler = [
        "xcrun",
        "clang",
        "--target=aarch64-linux-android24",
        "-ffreestanding",
        "-O2",
        "-isystem",
        str(headers.SYSROOT),
        "-x",
        "c",
    ]
    dependencies = shlex.split(
        subprocess.check_output([*compiler, "-M", str(source)], text=True).replace(
            "\\\n", ""
        )
    )[1:]
    resource = Path(
        subprocess.check_output(
            ["xcrun", "clang", "-print-resource-dir"], text=True
        ).strip()
    ).resolve()
    inputs = {}
    for value in dependencies:
        path = Path(value)
        if path == source:
            continue
        if path.is_relative_to(headers.SYSROOT):
            name = path.relative_to(headers.SYSROOT).as_posix()
            if len(groups[name.casefold()]) != 1 or path.read_bytes() != expected[name]:
                raise ValueError(f"ambiguous or modified bionic dependency: {name}")
        elif not path.resolve().is_relative_to(resource):
            raise ValueError(f"unexpected compiler header dependency: {path}")
        inputs[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest()
    ir = subprocess.check_output(
        [*compiler, "-S", "-emit-llvm", str(source), "-o", "-"], text=True
    )
    values = {
        name: int(value)
        for name, value in re.findall(
            r"^@abi_(\w+) = .*?constant i64 (-?\d+)", ir, re.MULTILINE
        )
    }
    if set(values) != set(expressions):
        raise ValueError("compiler did not emit every constant/layout as an integer")
    lines = [
        "// Compiler-derived bionic arm64 file ABI; generated local output.",
        "#pragma once",
        "#include <cstdint>",
        "namespace shiraz::guest::bionic {",
    ]
    lines += [
        f"inline constexpr int64_t k{name} = {value};" for name, value in values.items()
    ]
    lines += ["}", ""]
    output = OUT / "guest_io.hpp"
    text = "\n".join(lines)
    if not output.exists() or output.read_text() != text:
        output.write_text(text)
    output.with_suffix(".json").write_text(
        json.dumps(
            {
                "bionic_commit": headers.COMMIT,
                "compiler_command": compiler,
                "header_dependencies_sha256": inputs,
                "values": values,
                "ir_sha256": hashlib.sha256(ir.encode()).hexdigest(),
            },
            indent=2,
            sort_keys=True,
        )
        + "\n"
    )
    print(f"Guest file ABI: {len(values)} compiler-derived constants/layout fields")


if __name__ == "__main__":
    (ROOT / ".deps").mkdir(exist_ok=True)
    with (ROOT / ".deps/guest-abi.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        main()
