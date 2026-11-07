// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ipc_channel.hpp"
#include <array>
#include <cerrno>
#include <cstring>
#include <new>
#include <sys/socket.h>
#include <unistd.h>
#if defined(__APPLE__)
#include <atomic>
#include <cstdio>
#include <fcntl.h>
#include <sys/un.h>
#include <sys/ucred.h>
#endif
namespace shiraz {
namespace {
#if defined(__APPLE__)
// Darwin has no per-message SCM_CREDENTIALS. LOCAL_PEERPID reports the last
// process that operated on the connected peer socket and fails once it closed;
// LOCAL_PEERCRED is cached by the kernel at pair time and survives the close.
int peerPid(int socket){pid_t pid=-1;socklen_t length=sizeof(pid);return !getsockopt(socket,SOL_LOCAL,LOCAL_PEERPID,&pid,&length)&&length==sizeof(pid)&&pid>0?int(pid):-1;}
int peerUid(int socket){xucred credentials{};socklen_t length=sizeof(credentials);
    return !getsockopt(socket,SOL_LOCAL,LOCAL_PEERCRED,&credentials,&length)&&length==sizeof(credentials)&&credentials.cr_version==XUCRED_VERSION&&credentials.cr_uid<=uid_t(INT32_MAX)?int(credentials.cr_uid):-1;}
#endif
constexpr size_t maximumBytes=16*1024*1024,maximumFds=128;
constexpr uint32_t magic=0x315a4853;
void put(uint8_t* p,uint32_t value){for(unsigned i=0;i<4;i++)p[i]=uint8_t(value>>(8*i));}
uint32_t get(const uint8_t* p){uint32_t value=0;for(unsigned i=0;i<4;i++)value|=uint32_t(p[i])<<(8*i);return value;}
int fail(int socket,int error){shutdown(socket,SHUT_RDWR);return -error;}
int writeAll(int socket,const uint8_t* bytes,size_t size){
    while(size){ssize_t n=send(socket,bytes,size,MSG_NOSIGNAL);if(n<0&&errno==EINTR)continue;
        if(n<=0)return fail(socket,n<0?errno:EPIPE);
        bytes+=n;size-=n;}
    return 0;
}
}
IpcPacket::~IpcPacket(){for(int fd:descriptors)close(fd);}
int ipc_pair(int sockets[2]){
#if defined(__APPLE__)
    // No SOCK_CLOEXEC or SO_PASSCRED: mark both ends close-on-exec and disable SIGPIPE on the socket itself.
    if(socketpair(AF_UNIX,SOCK_STREAM,0,sockets))return -errno;
    int enabled=1;
    for(int i=0;i<2;i++)if(fcntl(sockets[i],F_SETFD,FD_CLOEXEC)||setsockopt(sockets[i],SOL_SOCKET,SO_NOSIGPIPE,&enabled,sizeof(enabled))){int code=errno;close(sockets[0]);close(sockets[1]);return -code;}
#else
    if(socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,sockets))return -errno;
    int enabled=1;
    if(setsockopt(sockets[0],SOL_SOCKET,SO_PASSCRED,&enabled,sizeof(enabled))||setsockopt(sockets[1],SOL_SOCKET,SO_PASSCRED,&enabled,sizeof(enabled))){int code=errno;close(sockets[0]);close(sockets[1]);return -code;}
#endif
    return 0;
}
int ipc_send(int socket,const uint8_t* bytes,size_t size,const int* descriptors,size_t count){
    if(size>maximumBytes||count>maximumFds||(size&&!bytes)||(count&&!descriptors))return -EINVAL;
    std::array<uint8_t,12> header{};put(header.data(),magic);put(header.data()+4,size);put(header.data()+8,count);
    alignas(cmsghdr) std::array<uint8_t,CMSG_SPACE(maximumFds*sizeof(int))> control{};
    iovec vector{header.data(),header.size()};msghdr message{};message.msg_iov=&vector;message.msg_iovlen=1;
    if(count){message.msg_control=control.data();message.msg_controllen=CMSG_SPACE(count*sizeof(int));
        cmsghdr* c=CMSG_FIRSTHDR(&message);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(count*sizeof(int));
        memcpy(CMSG_DATA(c),descriptors,count*sizeof(int));}
    ssize_t sent;do{sent=sendmsg(socket,&message,MSG_NOSIGNAL);}while(sent<0&&errno==EINTR);
    if(sent<=0)return fail(socket,sent<0?errno:EPIPE);
    int status=writeAll(socket,header.data()+sent,header.size()-size_t(sent));
    return status?status:writeAll(socket,bytes,size);
}
int ipc_receive(int socket,IpcPacket& packet){
    if(!packet.bytes.empty()||!packet.descriptors.empty())return -EINVAL;
    // Allocate before recvmsg: every installed descriptor must have an owner,
    // including malformed/truncated packets and allocation failures.
    try{packet.descriptors.reserve(maximumFds);}catch(const std::bad_alloc&){return -ENOMEM;}
    std::array<uint8_t,12> header{};
#if defined(__APPLE__)
    alignas(cmsghdr) std::array<uint8_t,CMSG_SPACE(maximumFds*sizeof(int))> control{};
#else
    alignas(cmsghdr) std::array<uint8_t,CMSG_SPACE(maximumFds*sizeof(int))+CMSG_SPACE(sizeof(ucred))> control{};
#endif
    iovec vector{header.data(),header.size()};msghdr message{};message.msg_iov=&vector;message.msg_iovlen=1;
    message.msg_control=control.data();message.msg_controllen=control.size();
#if defined(__APPLE__)
    // Snapshot before blocking: a sender that exits right after its final packet disconnects the peer pid query.
    int snapshot=peerPid(socket);
    ssize_t received;do{received=recvmsg(socket,&message,MSG_WAITALL);}while(received<0&&errno==EINTR);
    if(received<=0)return received==0?-EPIPE:fail(socket,errno);
    // A fresh pair reports its creator until the peer acts, so a snapshot equal to
    // our own pid cannot name another sender; leave pid unset and fail EPROTO.
    int current=peerPid(socket);packet.pid=current>0?current:snapshot!=int(getpid())?snapshot:-1;packet.uid=peerUid(socket);
#else
    ssize_t received;do{received=recvmsg(socket,&message,MSG_WAITALL|MSG_CMSG_CLOEXEC);}while(received<0&&errno==EINTR);
    if(received<=0)return received==0?-EPIPE:fail(socket,errno);
#endif
    bool bad=(message.msg_flags&(MSG_CTRUNC|MSG_TRUNC))!=0;
    for(cmsghdr* c=CMSG_FIRSTHDR(&message);c;c=CMSG_NXTHDR(&message,c)){
#if defined(__APPLE__)
        // Sender credentials were read from the connected peer after recvmsg.
#else
        if(c->cmsg_level==SOL_SOCKET&&c->cmsg_type==SCM_CREDENTIALS&&c->cmsg_len==CMSG_LEN(sizeof(ucred))){
            ucred credentials;memcpy(&credentials,CMSG_DATA(c),sizeof(credentials));
            if(packet.pid!=-1)bad=true;
            packet.pid=credentials.pid;packet.uid=credentials.uid;continue;
        }
#endif
        if(c->cmsg_level!=SOL_SOCKET||c->cmsg_type!=SCM_RIGHTS||c->cmsg_len<CMSG_LEN(0)){bad=true;continue;}
        size_t length=c->cmsg_len-CMSG_LEN(0);
        if(length%sizeof(int))bad=true;
        for(size_t offset=0;offset+sizeof(int)<=length;offset+=sizeof(int)){
            int fd;memcpy(&fd,CMSG_DATA(c)+offset,sizeof(fd));
#if defined(__APPLE__)
            // No MSG_CMSG_CLOEXEC: set it before the packet takes ownership (spawns use POSIX_SPAWN_CLOEXEC_DEFAULT).
            if(packet.descriptors.size()<maximumFds){if(fcntl(fd,F_SETFD,FD_CLOEXEC))bad=true;packet.descriptors.push_back(fd);}else{close(fd);bad=true;}
#else
            if(packet.descriptors.size()<maximumFds)packet.descriptors.push_back(fd);else{close(fd);bad=true;}
#endif
        }
    }
    // recvmsg can return a short header after a signal or at an ancillary boundary.
    size_t filled=received;
    while(filled<header.size()&&!bad){ssize_t n=recv(socket,header.data()+filled,header.size()-filled,0);
        if(n<0&&errno==EINTR)continue;
        if(n<=0)return fail(socket,n<0?errno:EPIPE);
        filled+=n;}
#if defined(__APPLE__)
    static std::atomic<bool> peerReported{false};
    if(!bad&&packet.pid<1&&!peerReported.exchange(true))fprintf(stderr,"SHIRAZ_UNSUPPORTED ipc-peer-pid: sender exited before Darwin LOCAL_PEERPID could identify it\n");
#endif
    if(bad||packet.pid<1||packet.uid<0||get(header.data())!=magic||get(header.data()+4)>maximumBytes||get(header.data()+8)!=packet.descriptors.size())return fail(socket,EPROTO);
    try{packet.bytes.resize(get(header.data()+4));}catch(const std::bad_alloc&){return fail(socket,ENOMEM);}
    filled=0;
    while(filled<packet.bytes.size()){
        ssize_t n=recv(socket,packet.bytes.data()+filled,packet.bytes.size()-filled,0);
        if(n<0&&errno==EINTR)continue;
        if(n<=0)return fail(socket,n<0?errno:EPIPE);
        filled+=n;
    }
    return 0;
}
}
