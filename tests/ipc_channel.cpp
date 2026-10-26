// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ipc_channel.hpp"
#include <cassert>
#include <cerrno>
#include <cstdio>
#include <array>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>
int main(){
    int sockets[2];assert(shiraz::ipc_pair(sockets)==0);
    int fd=memfd_create("shiraz-ipc-test",MFD_CLOEXEC);assert(fd>=0);
    assert(write(fd,"abc",3)==3);
    pid_t child=fork();assert(child>=0);
    if(child==0){
        close(sockets[0]);close(fd);
        {
            shiraz::IpcPacket packet;assert(shiraz::ipc_receive(sockets[1],packet)==0);
            assert(packet.bytes.size()==1024*1024&&packet.descriptors.size()==1);
            for(size_t i=0;i<packet.bytes.size();++i)assert(packet.bytes[i]==uint8_t(i));
            int received=packet.descriptors[0];assert(fcntl(received,F_GETFD)&FD_CLOEXEC);
            char data[3];assert(pread(received,data,3,0)==3&&data[0]=='a');
            assert(pwrite(received,"xyz",3,0)==3);
            assert(shiraz::ipc_send(sockets[1],reinterpret_cast<const uint8_t*>("ok"),2,&received,1)==0);
        }
        close(sockets[1]);_exit(0);
    }
    close(sockets[1]);
    std::vector<uint8_t> bytes(1024*1024);for(size_t i=0;i<bytes.size();++i)bytes[i]=uint8_t(i);
    assert(shiraz::ipc_send(sockets[0],bytes.data(),bytes.size(),&fd,1)==0);
    close(fd); // Receiver owns a separate kernel reference.
    int received=-1;
    {
        shiraz::IpcPacket packet;assert(shiraz::ipc_receive(sockets[0],packet)==0);
        assert(packet.bytes.size()==2&&packet.bytes[0]=='o'&&packet.descriptors.size()==1);
        received=packet.descriptors[0];char data[3];assert(pread(received,data,3,0)==3&&data[0]=='x');
    }
    assert(fcntl(received,F_GETFD)==-1&&errno==EBADF);
    shiraz::IpcPacket eof;assert(shiraz::ipc_receive(sockets[0],eof)==-EPIPE);close(sockets[0]);
    int status;assert(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);
    assert(shiraz::ipc_pair(sockets)==0);
    assert(shiraz::ipc_send(sockets[0],nullptr,1,nullptr,0)==-EINVAL);
    assert(shiraz::ipc_send(sockets[0],bytes.data(),17*1024*1024,nullptr,0)==-EINVAL);
    assert(shiraz::ipc_send(sockets[0],nullptr,0,nullptr,0)==0);
    shiraz::IpcPacket empty;assert(shiraz::ipc_receive(sockets[1],empty)==0&&empty.bytes.empty());
    uint8_t corrupt[12]={};assert(send(sockets[0],corrupt,sizeof(corrupt),MSG_NOSIGNAL)==12);
    shiraz::IpcPacket bad;assert(shiraz::ipc_receive(sockets[1],bad)==-EPROTO);
    close(sockets[0]);close(sockets[1]);
    assert(shiraz::ipc_pair(sockets)==0);
    assert(send(sockets[0],corrupt,3,MSG_NOSIGNAL)==3);close(sockets[0]);
    shiraz::IpcPacket truncated;assert(shiraz::ipc_receive(sockets[1],truncated)==-EPIPE);close(sockets[1]);
    assert(shiraz::ipc_pair(sockets)==0);
    fd=memfd_create("shiraz-ipc-malformed",MFD_CLOEXEC);assert(fd>=0);
    // A valid rights control message with a deliberately mismatched header.
    uint8_t malformed[12]={0x53,0x48,0x5a,0x31};
    alignas(cmsghdr) std::array<uint8_t,CMSG_SPACE(sizeof(int))> control{};
    iovec vector{malformed,sizeof(malformed)};msghdr message{};
    message.msg_iov=&vector;message.msg_iovlen=1;message.msg_control=control.data();message.msg_controllen=control.size();
    cmsghdr* c=CMSG_FIRSTHDR(&message);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(c),&fd,sizeof(fd));
    assert(sendmsg(sockets[0],&message,MSG_NOSIGNAL)==12);
    int rejected=-1;
    {shiraz::IpcPacket packet;assert(shiraz::ipc_receive(sockets[1],packet)==-EPROTO);
     assert(packet.descriptors.size()==1);rejected=packet.descriptors[0];}
    assert(fcntl(rejected,F_GETFD)==-1&&errno==EBADF);
    assert(fcntl(fd,F_GETFD)>=0);close(fd);close(sockets[0]);close(sockets[1]);
    puts("IPC_CHANNEL_OK");
}
