// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/transfer.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
struct Descriptor { int value; ~Descriptor() { if (value >= 0) ::close(value); } };
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value),ArgumentType::Int64);
}
int main(int argc,char **argv) {
  try {
    require(argc==2,"usage: transfer-probe fixture-directory");
    auto base=native_personality(); Providers providers;
    for(const auto &pair:base.modules()) {
      Provider module=pair.second;
      if(module.soname=="libc.so") {
        auto additions=transfer_exports();
        module.exports.erase(std::remove_if(module.exports.begin(),module.exports.end(),[&](const auto &entry){
          return std::any_of(additions.begin(),additions.end(),[&](const auto &addition){return entry.name==addition.name;});
        }),module.exports.end());
        module.exports.insert(module.exports.end(),additions.begin(),additions.end());
      }
      providers.add(std::move(module));
    }
    Loader loader({argv[1]},std::move(providers)); auto *module=loader.open("libtransfer.so");
    auto send=[&](int out,int in,int64_t *offset,size_t count,int &error,bool wide=false) {
      errno=61;
      auto address=loader.symbol(module,wide?"transfer64":"transfer").address;
      auto result=invoke(reinterpret_cast<void *>(address),{Argument::integer(out),Argument::integer(in),pointer(offset),
        Argument::integer(count,ArgumentType::Int64),pointer(&error)}).result_integers[0];
      require(errno==61,"native errno preserved across transfer");
      return int64_t(result);
    };
    char in_name[]="/tmp/shiraz-transfer-in-XXXXXX",out_name[]="/tmp/shiraz-transfer-out-XXXXXX";
    Descriptor input{mkstemp(in_name)},output{mkstemp(out_name)};
    require(input.value>=0&&output.value>=0,"exclusive transfer fixtures"); unlink(in_name);unlink(out_name);
    std::vector<unsigned char> source(131072);
    for(size_t i=0;i<source.size();++i)source[i]=unsigned(i*13+7);
    require(::write(input.value,source.data(),source.size())==ssize_t(source.size()),"write real input file");
    require(lseek(input.value,19,SEEK_SET)==19,"set independent input cursor");
    int error=0;int64_t offset=3;
    auto moved=send(output.value,input.value,&offset,source.size(),error);
    require(moved==65536&&offset==65539&&error==73&&lseek(input.value,0,SEEK_CUR)==19&&
        lseek(output.value,0,SEEK_CUR)==moved,"bounded short transfer updates explicit offset and output cursor only");
    std::vector<unsigned char> copied(static_cast<size_t>(moved));
    require(pread(output.value,copied.data(),copied.size(),0)==moved&&
        std::equal(copied.begin(),copied.end(),source.begin()+3),"actual file bytes transferred");
    offset=source.size();require(!send(output.value,input.value,&offset,1,error,true)&&error==73&&offset==int64_t(source.size()),"64-bit alias returns EOF without offset change");
    offset=0;require(!send(output.value,input.value,&offset,0,error)&&error==73&&!offset,"zero count is side-effect free");
    require(send(output.value,input.value,nullptr,1,error)==-1&&error==38&&lseek(input.value,0,SEEK_CUR)==19,
        "uncoordinated shared input cursor is explicitly unsupported");
    offset=-1;require(send(output.value,input.value,&offset,1,error)==-1&&error==22&&offset==-1,"negative explicit offset fails unchanged");
    offset=0;require(send(-1,input.value,&offset,1,error)==-1&&error==9&&!offset,"bad output leaves source offset untouched");
    int flags=fcntl(output.value,F_GETFL);require(flags>=0&&!fcntl(output.value,F_SETFL,flags|O_APPEND),"enable output append");
    require(send(output.value,input.value,&offset,1,error)==-1&&error==22&&!offset,"Linux sendfile rejects O_APPEND");
    require(!fcntl(output.value,F_SETFL,flags),"restore output flags");
    int sockets[2];require(!socketpair(AF_UNIX,SOCK_STREAM,0,sockets),"real socket output");
    Descriptor socket_out{sockets[0]},socket_in{sockets[1]};
    require(send(socket_out.value,input.value,&offset,32,error)==32&&offset==32&&error==73,"explicit-offset socket transfer");
    std::array<unsigned char,32> socket_bytes{};
    require(::read(socket_in.value,socket_bytes.data(),socket_bytes.size())==32&&
        std::equal(socket_bytes.begin(),socket_bytes.end(),source.begin()),"socket receives actual source bytes");
    int pipes[2];require(!pipe(pipes),"real pipe output"); Descriptor pipe_in{pipes[0]},pipe_out{pipes[1]};
    require(!fcntl(pipe_out.value,F_SETFL,fcntl(pipe_out.value,F_GETFL)|O_NONBLOCK),"nonblocking pipe output");
    std::array<unsigned char,4096> filler{};size_t filled=0;
    for(;;){ssize_t n=::write(pipe_out.value,filler.data(),filler.size());if(n>0){filled+=size_t(n);continue;}require(n==-1&&errno==EAGAIN,"fill pipe to would-block");break;}
    offset=11;require(send(pipe_out.value,input.value,&offset,65536,error)==-1&&error==11&&offset==11,"EAGAIN consumes no input or explicit offset");
    require(::read(pipe_in.value,filler.data(),filler.size())==ssize_t(filler.size()),"free bounded pipe capacity");filled-=filler.size();
    auto partial=send(pipe_out.value,input.value,&offset,65536,error);
    require(partial>0&&partial<65536&&offset==11+partial&&error==73,"partial pipe write returns exactly committed bytes");
    std::vector<unsigned char> drained(filled+size_t(partial));size_t received=0;
    while(received<drained.size()){ssize_t n=::read(pipe_in.value,drained.data()+received,drained.size()-received);require(n>0,"read partial pipe payload");received+=size_t(n);}
    require(std::equal(drained.begin()+filled,drained.end(),source.begin()+11),"partial output contains exact committed prefix");
    offset=0;require(send(output.value,pipe_in.value,&offset,1,error)==-1&&error==29&&!offset,
        "nonseekable explicit input is ESPIPE");
    require(send(input.value,input.value,&offset,1,error)==-1&&error==22&&!offset,
        "unverified overlapping same-file transfer is rejected");
    int broken[2];require(!pipe(broken),"broken pipe fixture");close(broken[0]);Descriptor broken_out{broken[1]};
    struct sigaction ignored{},prior_pipe{};ignored.sa_handler=SIG_IGN;sigemptyset(&ignored.sa_mask);
    require(!sigaction(SIGPIPE,&ignored,&prior_pipe),"ignore SIGPIPE for errno assertion");
    auto broken_result=send(broken_out.value,input.value,&offset,1,error);
    require(!sigaction(SIGPIPE,&prior_pipe,nullptr),"restore SIGPIPE action");
    require(broken_result==-1&&error==32&&!offset,"EPIPE leaves explicit offset unchanged");
    // Refill then block output. Run the interrupted transfer in a bounded
    // child so a native signal/restart regression cannot hang the suite.
    for(;;){ssize_t n=::write(pipe_out.value,filler.data(),filler.size());if(n>0)continue;require(n==-1&&errno==EAGAIN,"refill nonblocking pipe");break;}
    require(!fcntl(pipe_out.value,F_SETFL,fcntl(pipe_out.value,F_GETFL)&~O_NONBLOCK),"switch pipe output to blocking");
    pid_t child=fork();require(child>=0,"EINTR transfer child");
    if(!child){
      struct sigaction handler{};handler.sa_handler=+[](int){};sigemptyset(&handler.sa_mask);
      sigset_t mask{};sigemptyset(&mask);sigaddset(&mask,SIGUSR2);
      if(sigaction(SIGUSR2,&handler,nullptr)||pthread_sigmask(SIG_UNBLOCK,&mask,nullptr))::_exit(99);
      pthread_t writer=pthread_self();std::atomic<bool> finished{false};
      std::thread interrupter([&]{
        auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(4);
        while(!finished&&std::chrono::steady_clock::now()<deadline){pthread_kill(writer,SIGUSR2);std::this_thread::yield();}
      });
      int interrupted_error=0;int64_t interrupted_offset=23;
      auto result=send(pipe_out.value,input.value,&interrupted_offset,65536,interrupted_error);
      finished=true;interrupter.join();
      ::_exit(result==-1&&interrupted_error==4&&interrupted_offset==23?0:98);
    }
    int status=0;bool exited=false;auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(5);
    while(std::chrono::steady_clock::now()<deadline){pid_t result=waitpid(child,&status,WNOHANG);if(result==child){exited=true;break;}require(result==0,"observe EINTR child");std::this_thread::yield();}
    if(!exited){kill(child,SIGKILL);waitpid(child,&status,0);}
    require(exited&&WIFEXITED(status)&&WEXITSTATUS(status)==0,"EINTR is returned without retry or source offset movement");
    int64_t sparse=(int64_t(1)<<32)+123;const char tail[]="above-four-gib";
    require(pwrite(input.value,tail,sizeof(tail),sparse)==ssize_t(sizeof(tail)),"sparse source above4GiB");
    off_t tail_output=lseek(output.value,0,SEEK_CUR);
    offset=sparse;require(send(output.value,input.value,&offset,sizeof(tail),error,true)==ssize_t(sizeof(tail))&&
        offset==sparse+int64_t(sizeof(tail))&&error==73&&lseek(input.value,0,SEEK_CUR)==19,"full-width explicit off64 semantics");
    char tail_copy[sizeof(tail)]{};require(pread(output.value,tail_copy,sizeof(tail_copy),tail_output)==ssize_t(sizeof(tail_copy))&&
        !std::memcmp(tail_copy,tail,sizeof(tail)),"actual payload above4GiB copied intact");
    std::cout<<"M5_TRANSFER_PASS explicit_off64=1 file=1 socket=1 pipe_partial=1 nonblock=1 eintr=1 bounded=65536 null_offset=unsupported errno=preserved\n";
  }catch(const std::exception &failure){std::cerr<<"FAIL "<<failure.what()<<'\n';return 1;}
}
