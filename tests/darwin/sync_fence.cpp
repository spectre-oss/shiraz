// SPDX-License-Identifier: GPL-3.0-only
// macOS pipe fences in native/sync_fence.cpp: sync_merge and sync_wait accept only
// the read end of a pipe; a write or a closed write end signals the fence.
#include <cassert>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <fcntl.h>
#include <thread>
#include <unistd.h>
extern "C" int sync_merge(const char*, int, int);
extern "C" int sync_wait(int, int);
static int count(){int n=0;for(int fd=0;fd<1024;++fd)if(fcntl(fd,F_GETFD)>=0)++n;return n;}
static void fence(int ends[2]){assert(pipe(ends)==0);}
int main(){
    const int baseline=count();
    errno=0;assert(sync_merge("invalid",-1,-1)==-1&&errno==EBADF);
    assert(sync_wait(-1,0)==-1&&errno==EINVAL);
    int a[2],b[2];fence(a);fence(b);
    errno=0;assert(sync_merge("bad2",a[0],-1)==-1&&errno==ENOENT);
    int file=open("/dev/null",O_RDONLY);errno=0;assert(sync_merge("file",file,a[0])==-1&&errno==ENOTTY);
    errno=0;assert(sync_merge("file2",a[0],file)==-1&&errno==ENOENT);close(file);
    errno=0;assert(sync_merge("writeend",a[1],b[0])==-1&&errno==ENOTTY);
    int merged=sync_merge("pending",a[0],b[0]);assert(merged>=0);
    assert((fcntl(merged,F_GETFD)&FD_CLOEXEC)!=0);
    close(a[0]);close(b[0]); // the merged fence must hold its own input references
    errno=0;assert(sync_wait(merged,0)==-1&&errno==ETIME);
    assert(write(a[1],"x",1)==1);
    errno=0;assert(sync_wait(merged,50)==-1&&errno==ETIME);
    std::thread late([&]{std::this_thread::sleep_for(std::chrono::milliseconds(30));assert(write(b[1],"x",1)==1);});
    auto start=std::chrono::steady_clock::now();assert(sync_wait(merged,2000)==0);
    assert(std::chrono::steady_clock::now()-start<std::chrono::milliseconds(1000));late.join();
    assert(sync_wait(merged,0)==0&&sync_wait(merged,0)==0); // sticky
    close(merged);close(a[1]);close(b[1]);
    // Both already signalled: fast path, immediately readable.
    fence(a);fence(b);assert(write(a[1],"x",1)==1);assert(write(b[1],"x",1)==1);
    merged=sync_merge(nullptr,a[0],b[0]);assert(merged>=0&&sync_wait(merged,0)==0);close(merged);
    // Self-merge of a pending fence; writer exit counts as signalled.
    int c[2];fence(c);merged=sync_merge("self",c[0],c[0]);assert(merged>=0);
    errno=0;assert(sync_wait(merged,0)==-1&&errno==ETIME);close(c[1]);assert(sync_wait(merged,1000)==0);close(merged);close(c[0]);
    // Merge of merges, and dropping the merged fence before signal must not SIGPIPE.
    int d[2],e[2];fence(d);fence(e);int m1=sync_merge("m1",d[0],e[0]),m2=sync_merge("m2",m1,d[0]);assert(m1>=0&&m2>=0);
    int dropped=sync_merge("dropped",d[0],e[0]);assert(dropped>=0);close(dropped);
    assert(write(d[1],"x",1)==1);errno=0;assert(sync_wait(m2,30)==-1&&errno==ETIME);
    assert(write(e[1],"x",1)==1);assert(sync_wait(m2,1000)==0&&sync_wait(m1,0)==0);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    close(m1);close(m2);for(int fd:{d[0],d[1],e[0],e[1],a[0],a[1],b[0],b[1]})close(fd);
    // Many concurrent pending merges through the single worker.
    int p[64][2],m[64];for(int i=0;i<64;++i){fence(p[i]);m[i]=sync_merge("many",p[i][0],p[i][0]);assert(m[i]>=0);}
    for(int i=63;i>=0;--i)assert(write(p[i][1],"x",1)==1);
    for(int i=0;i<64;++i){assert(sync_wait(m[i],1000)==0);close(m[i]);close(p[i][0]);close(p[i][1]);}
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    std::printf("fds baseline=%d end=%d (+2 = worker wake pipe)\n",baseline,count());assert(count()==baseline+2);
    std::puts("SYNC_FENCE_DARWIN_PIPE_FENCES_OK");
}
