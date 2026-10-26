// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include "shiraz/window_snapshot.hpp"
#include <cassert>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <fcntl.h>
#include <sys/wait.h>
#include <unistd.h>
#include <cstdio>
int main() {
    assert(!shiraz_window_create(-1, 4));
    auto* w = shiraz_window_create(2, 3);
    assert(w && ANativeWindow_getWidth(w) == 2 && ANativeWindow_getHeight(w) == 3);
    assert(shiraz_window_revision(nullptr)==0);uint64_t revision=0;shiraz::Surface image(1,1);
    assert(shiraz::refresh_window_snapshot(w,image,revision)&&image.width==2&&image.height==3&&image.pixels[0]==0);
    uint64_t initial=revision;image.pixels[0]=0x12345678;
    assert(!shiraz::refresh_window_snapshot(w,image,revision)&&image.pixels[0]==0x12345678);
    ANativeWindow_acquire(w); ANativeWindow_release(w);
    ANativeWindow_Buffer b{}; ARect dirty{1, 1, 2, 2};
    assert(ANativeWindow_lock(w, &b, &dirty) == 0);
    assert(b.stride == 2 && b.format == 1 && dirty.left == 0 && dirty.bottom == 3);
    auto* bytes = static_cast<uint8_t*>(b.bits);
    bytes[0] = 255; bytes[1] = 64; bytes[2] = 32; bytes[3] = 128;
    uint32_t pixels[6]{};
    assert(shiraz_window_copy_argb(w, pixels, 6) == 0 && pixels[0] == 0);
    assert(ANativeWindow_lock(w, &b, nullptr) == -EBUSY);
    assert(ANativeWindow_setBuffersGeometry(w, 4, 4, 1) == -EBUSY);
    assert(shiraz_window_revision(w)==initial);assert(!shiraz::refresh_window_snapshot(w,image,revision));
    assert(ANativeWindow_unlockAndPost(w) == 0);
    assert(shiraz_window_revision(w)!=initial&&shiraz::refresh_window_snapshot(w,image,revision)&&image.pixels[0]==0x80ff4020);
    assert(!shiraz::refresh_window_snapshot(w,image,revision));
    assert(ANativeWindow_unlockAndPost(w) == -EINVAL);
    assert(shiraz_window_copy_argb(w, pixels, 5) == -EINVAL);
    assert(shiraz_window_copy_argb(w, pixels, 6) == 0 && pixels[0] == 0x80ff4020);
    assert(ANativeWindow_setBuffersGeometry(w, 4, 4, 2) == -EINVAL);
    assert(ANativeWindow_setBuffersGeometry(w, 4, 4, 1) == 0);
    assert(ANativeWindow_getWidth(w) == 4);
    assert(shiraz::refresh_window_snapshot(w,image,revision)&&image.width==4&&image.height==4&&image.pixels[0]==0);
    uint32_t host[16];for(int i=0;i<16;i++)host[i]=0x10010203u+i*0x01030507u;
    auto* storage=image.pixels.data();auto posted=revision;
    assert(shiraz_window_post_argb(w,host,15)==-EINVAL&&shiraz_window_revision(w)==posted);
    assert(shiraz_window_post_argb(w,host,16)==0&&shiraz::refresh_window_snapshot(w,image,revision));
    assert(image.pixels.data()==storage);for(int i=0;i<16;i++)assert(image.pixels[i]==host[i]);
    assert(ANativeWindow_lock(w,&b,nullptr)==0);assert(shiraz_window_post_argb(w,host,16)==-EBUSY);
    auto* rgba=static_cast<uint8_t*>(b.bits);rgba[0]=9;rgba[1]=8;rgba[2]=7;rgba[3]=6;
    assert(ANativeWindow_unlockAndPost(w)==0&&shiraz::refresh_window_snapshot(w,image,revision)&&image.pixels[0]==0x06090807);
    assert(shiraz_window_post_argb(w,host,16)==0&&shiraz::refresh_window_snapshot(w,image,revision)&&image.pixels[0]==host[0]);
    auto before=revision;assert(ANativeWindow_setBuffersGeometry(w,-1,4,1)==-EINVAL&&shiraz_window_revision(w)==before);
    assert(ANativeWindow_setBuffersGeometry(w,4,4,1)==0&&shiraz::refresh_window_snapshot(w,image,revision));
    assert(ANativeWindow_setBuffersGeometry(w, 0, 0, 0) == 0);
    assert(ANativeWindow_getWidth(w) == 2 && ANativeWindow_getHeight(w) == 3);
    assert(shiraz::refresh_window_snapshot(w,image,revision)&&image.width==2&&image.height==3);
    int fd=shiraz_window_export(w);assert(fd>=0&&(fcntl(fd,F_GETFD)&FD_CLOEXEC));
    auto beforeFork=shiraz_window_revision(w);
    pid_t child=fork();assert(child>=0);
    if(!child){
        auto* imported=shiraz_window_import(fd);assert(imported);close(fd);
        ANativeWindow_release(w);
        assert(ANativeWindow_getWidth(imported)==2&&ANativeWindow_getHeight(imported)==3);
        assert(ANativeWindow_setBuffersGeometry(imported,3,1,1)==0);
        ANativeWindow_Buffer buffer{};assert(ANativeWindow_lock(imported,&buffer,nullptr)==0);
        auto* data=static_cast<uint8_t*>(buffer.bits);
        for(int i=0;i<3;i++){data[i*4]=1+i;data[i*4+1]=7;data[i*4+2]=9;data[i*4+3]=255;}
        assert(ANativeWindow_unlockAndPost(imported)==0);
        ANativeWindow_release(imported);_exit(0);
    }
    int status=0;assert(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);
    assert(shiraz_window_revision(w)!=beforeFork);
    assert(ANativeWindow_getWidth(w)==3&&ANativeWindow_getHeight(w)==1);
    assert(shiraz_window_copy_argb(w,pixels,6)==0&&pixels[0]==0xff010709&&pixels[2]==0xff030709);
    auto stable=shiraz_window_revision(w);assert(shiraz_window_revision(w)==stable);
    auto* stale=shiraz_window_import(fd);assert(stale);
    ANativeWindow_Buffer pending{};assert(ANativeWindow_lock(stale,&pending,nullptr)==0);
    assert(ANativeWindow_setBuffersGeometry(w,7,2,1)==0);
    assert(ANativeWindow_unlockAndPost(stale)==-EAGAIN);
    assert(ANativeWindow_getWidth(w)==7&&ANativeWindow_getHeight(w)==2);
    ANativeWindow_release(stale);
    assert(ANativeWindow_setBuffersGeometry(w,3,1,1)==0);
    auto* survivor=shiraz_window_import(fd);assert(survivor);close(fd);ANativeWindow_release(w);
    uint32_t finalPixels[3]={0x12345678,0x87654321,0xffabcdef};
    assert(shiraz_window_post_argb(survivor,finalPixels,3)==0);
    assert(shiraz_window_copy_argb(survivor,pixels,6)==0&&pixels[0]==finalPixels[0]);
    ANativeWindow_release(survivor);
    assert(!shiraz_window_import(-1));
    puts("NATIVE_WINDOW_PROCESS_PIXELS_RESIZE_LIFETIME_OK");
}
