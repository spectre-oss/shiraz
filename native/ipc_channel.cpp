// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ipc_channel.hpp"
#include <array>
#include <cerrno>
#include <cstring>
#include <new>
#include <sys/socket.h>
#include <unistd.h>
namespace shiraz {
namespace {
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
    if(socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,sockets))return -errno;
    int enabled=1;
    if(setsockopt(sockets[0],SOL_SOCKET,SO_PASSCRED,&enabled,sizeof(enabled))||setsockopt(sockets[1],SOL_SOCKET,SO_PASSCRED,&enabled,sizeof(enabled))){int code=errno;close(sockets[0]);close(sockets[1]);return -code;}
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
    alignas(cmsghdr) std::array<uint8_t,CMSG_SPACE(maximumFds*sizeof(int))+CMSG_SPACE(sizeof(ucred))> control{};
    iovec vector{header.data(),header.size()};msghdr message{};message.msg_iov=&vector;message.msg_iovlen=1;
    message.msg_control=control.data();message.msg_controllen=control.size();
    ssize_t received;do{received=recvmsg(socket,&message,MSG_WAITALL|MSG_CMSG_CLOEXEC);}while(received<0&&errno==EINTR);
    if(received<=0)return received==0?-EPIPE:fail(socket,errno);
    bool bad=(message.msg_flags&(MSG_CTRUNC|MSG_TRUNC))!=0;
    for(cmsghdr* c=CMSG_FIRSTHDR(&message);c;c=CMSG_NXTHDR(&message,c)){
        if(c->cmsg_level==SOL_SOCKET&&c->cmsg_type==SCM_CREDENTIALS&&c->cmsg_len==CMSG_LEN(sizeof(ucred))){
            ucred credentials;memcpy(&credentials,CMSG_DATA(c),sizeof(credentials));
            if(packet.pid!=-1)bad=true;
            packet.pid=credentials.pid;packet.uid=credentials.uid;continue;
        }
        if(c->cmsg_level!=SOL_SOCKET||c->cmsg_type!=SCM_RIGHTS||c->cmsg_len<CMSG_LEN(0)){bad=true;continue;}
        size_t length=c->cmsg_len-CMSG_LEN(0);
        if(length%sizeof(int))bad=true;
        for(size_t offset=0;offset+sizeof(int)<=length;offset+=sizeof(int)){
            int fd;memcpy(&fd,CMSG_DATA(c)+offset,sizeof(fd));
            if(packet.descriptors.size()<maximumFds)packet.descriptors.push_back(fd);else{close(fd);bad=true;}
        }
    }
    // recvmsg can return a short header after a signal or at an ancillary boundary.
    size_t filled=received;
    while(filled<header.size()&&!bad){ssize_t n=recv(socket,header.data()+filled,header.size()-filled,0);
        if(n<0&&errno==EINTR)continue;
        if(n<=0)return fail(socket,n<0?errno:EPIPE);
        filled+=n;}
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
