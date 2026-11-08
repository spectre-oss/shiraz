// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/signals.hpp"
#include <array>
#include <cmath>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <pthread.h>
#include <signal.h>
#include <unistd.h>
#include <zlib.h>
using namespace shiraz::guest;
static void check(bool value,const char *message){if(!value)throw std::runtime_error(message);}
static Argument n(uint64_t value){return Argument::integer(value,ArgumentType::Int64);}
static Argument p(const void *value){return n(uintptr_t(value));}
struct Event{uint32_t events=0,padding=0;uint64_t data=0;};
int main(int argc,char **argv){try{
 check(argc==2,"usage: telegram-api-probe work-directory");std::filesystem::create_directories(argv[1]);
 Loader *current=nullptr;auto validate=[&](const void *code){return current&&current->contains_code(code);};
 auto lifecycle=std::make_shared<Lifecycle>(validate);auto signals=std::make_shared<SignalRuntime>(validate);
 auto providers=native_personality(validate,lifecycle,signals);std::vector<ProviderExport> exports;
 for(const auto &module:providers.modules())exports.insert(exports.end(),module.second.exports.begin(),module.second.exports.end());
 Loader loader({argv[1]},std::move(providers));current=&loader;lifecycle->attach(loader);signals->attach(loader);
 auto find=[&](const char *name){for(auto &entry:exports)if(entry.name==name)return reinterpret_cast<void *>(entry.address);throw std::runtime_error(std::string("missing export ")+name);};
 auto call=[&](const char *name,std::vector<Argument> args=std::vector<Argument>{}){return invoke(find(name),args);};
 auto integer=[&](const char *name,std::vector<Argument> args=std::vector<Argument>{}){return int64_t(call(name,args).result_integers[0]);};
 int ep=int(integer("epoll_create1",{n(0x80000)})),counter=int(integer("eventfd",{n(0),n(0x800)}));check(ep>=0&&counter>=0,"epoll and eventfd creation");
 Event interest{1,0,0x12345678abcdefULL},output[4]{};
 check(!integer("epoll_ctl",{n(ep),n(1),n(counter),p(&interest)}),"epoll add counter");
 check(integer("epoll_wait",{n(ep),p(output),n(4),n(0)})==0,"empty counter readiness");
 uint64_t one=1,value=0;check(integer("write",{n(counter),p(&one),n(8)})==8,"real counter write");
 check(integer("epoll_wait",{n(ep),p(output),n(4),n(100)})==1&&output[0].data==interest.data&&(output[0].events&1),"counter readiness and exact 64-bit event data");
 check(integer("read",{n(counter),p(&value),n(8)})==8&&value==1,"counter read");
 check(!integer("epoll_wait",{n(ep),p(output),n(4),n(0)}),"drained level readiness");
 interest.events=0x40000001u;check(!integer("epoll_ctl",{n(ep),n(3),n(counter),p(&interest)}),"one-shot registration");
 check(integer("write",{n(counter),p(&one),n(8)})==8,"counter write for one-shot");
 check(integer("epoll_wait",{n(ep),p(output),n(4),n(100)})==1,"one-shot delivery");
 check(!integer("epoll_wait",{n(ep),p(output),n(4),n(0)}),"one-shot disables all filters");
 check(!integer("epoll_ctl",{n(ep),n(3),n(counter),p(&interest)})&&integer("epoll_wait",{n(ep),p(output),n(4),n(100)})==1,"one-shot rearm observes pending readiness");
 check(!integer("close",{n(counter)}),"close watched eventfd");check(!integer("epoll_wait",{n(ep),p(output),n(4),n(0)}),"close removes watch");
 int sockets[2];check(!integer("socketpair",{n(1),n(1),n(0),p(sockets)}),"real Unix socket pair");
 interest.events=0x80000001u;check(!integer("epoll_ctl",{n(ep),n(1),n(sockets[0]),p(&interest)}),"edge registration");
 const char payload[]="guest network";char bytes[sizeof(payload)]{};
 check(integer("send",{n(sockets[1]),p(payload),n(sizeof(payload)),n(0)})==sizeof(payload),"actual guest send");
 check(integer("epoll_wait",{n(ep),p(output),n(4),n(100)})==1,"edge delivered");
 check(!integer("epoll_wait",{n(ep),p(output),n(4),n(0)}),"edge not repeated without drain");
 check(integer("recvfrom",{n(sockets[0]),p(bytes),n(sizeof(bytes)),n(0),n(0),n(0)})==sizeof(bytes)&&!std::memcmp(payload,bytes,sizeof(bytes)),"actual recvfrom payload");
 std::array<uint8_t,128> set{};integer("__FD_SET_chk",{n(sockets[0]),p(set.data()),n(set.size())});int64_t timeout[2]{};
 check(!integer("select",{n(sockets[0]+1),p(set.data()),n(0),n(0),p(timeout)}),"select empty socket");
 check(integer("sendto",{n(sockets[1]),p(payload),n(sizeof(payload)),n(0),n(0),n(0)})==sizeof(payload),"actual sendto on connected socket");
 integer("__FD_SET_chk",{n(sockets[0]),p(set.data()),n(set.size())});
 check(integer("select",{n(sockets[0]+1),p(set.data()),n(0),n(0),p(timeout)})==1&&integer("__FD_ISSET_chk",{n(sockets[0]),p(set.data()),n(set.size())}),"select publishes Linux fd bits");
 check(integer("epoll_wait",{n(ep),p(output),n(4),n(100)})==1,"new edge after drain");
 integer("close",{n(sockets[0])});integer("close",{n(sockets[1])});integer("close",{n(ep)});
 std::array<uint8_t,16> address{};char text[64]{};check(integer("inet_pton",{n(10),p("::1"),p(address.data())})==1,"IPv6 parse");
 check(integer("inet_ntop",{n(10),p(address.data()),p(text),n(sizeof(text))})==int64_t(uintptr_t(text))&&!std::strcmp(text,"::1"),"IPv6 formatting");
 int pipe[2];check(!::pipe(pipe),"real I/O pipe");struct Vector{void *data;uint64_t size;};char a[]="abc",b[]="def",first[2]{},second[4]{};
 Vector writes[]={{a,3},{b,3}},reads[]={{first,2},{second,4}};
 check(integer("writev",{n(pipe[1]),p(writes),n(2)})==6&&integer("readv",{n(pipe[0]),p(reads),n(2)})==6&&!std::memcmp(first,"ab",2)&&!std::memcmp(second,"cdef",4),"real vectored I/O");
 ::close(pipe[0]);::close(pipe[1]);
 std::string path=(std::filesystem::path(argv[1])/"payload.gz").string();gzFile host=::gzopen(path.c_str(),"wb");check(host&&::gzwrite(host,payload,sizeof(payload))==sizeof(payload)&&!::gzclose(host),"independent gzip fixture");
 auto file=integer("gzopen",{p(path.c_str()),p("rb")});check(file,"owned gzip open");
 std::memset(bytes,0,sizeof(bytes));check(integer("gzread",{n(file),p(bytes),n(sizeof(bytes))})==sizeof(bytes)&&!std::memcmp(bytes,payload,sizeof(bytes)),"owned gzip decompression");check(!integer("gzclose",{n(file)}),"owned gzip close");
 check(integer("gzread",{n(file),p(bytes),n(1)})==-1,"closed gzip handle rejected");
 check(call("floor",{Argument::real(-1.25)}).double_result()==-2,"double math ABI");
 check(call("scalbn",{Argument::real(1.5),n(3)}).double_result()==12,"mixed scalar math ABI");
 auto floating=call("sqrtf",{Argument::real(9.0f)});float square;std::memcpy(&square,floating.result_vectors[0],4);check(square==3,"float math ABI");
 alignas(8) std::array<uint8_t,128> attributes{};check(!integer("pthread_getattr_np",{p(pthread_self()),p(attributes.data())}),"real thread attributes");
 void *base=nullptr;size_t size=0;check(!integer("pthread_attr_getstack",{p(attributes.data()),p(&base),p(&size)})&&uintptr_t(&size)>=uintptr_t(base)&&uintptr_t(&size)-uintptr_t(base)<size,"actual thread stack contains local variable");check(!integer("pthread_attr_destroy",{p(attributes.data())}),"thread attribute lifetime");
 int64_t resolution[2];check(!integer("clock_getres",{n(1),p(resolution)})&&(resolution[0]||resolution[1]),"actual clock resolution");int64_t zero[2]{};check(!integer("clock_nanosleep",{n(1),n(0),p(zero),n(0)}),"clock sleep ABI");
 check(integer("__register_atfork",{n(0),n(0),n(0),n(0)})==0,"owned empty atfork registration");
 std::cout<<"M5_TELEGRAM_API_PASS epoll=counter_edge_oneshot_close select=real sockets=real ipv6=real vector_io=real gzip=owned math=double_float stack=real clocks=real atfork=registration_only\n";
}catch(const std::exception &error){std::cerr<<"FAIL "<<error.what()<<'\n';return 1;}}
