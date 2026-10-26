// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/shared_frame.hpp"
#include <cassert>
#include <cstdio>
#include <stdexcept>
#include <fcntl.h>
#include <sys/wait.h>
#include <unistd.h>
int main(){
 auto frame=shiraz::SharedFrame::create();int fd=frame->duplicate();assert(fcntl(fd,F_GETFD)&FD_CLOEXEC);
 int w=0,h=0;uint64_t revision=0;std::vector<uint32_t> pixels;
 assert(!frame->read(w,h,revision,pixels));
 pid_t child=fork();assert(child>=0);
 if(!child){auto producer=shiraz::SharedFrame::import(fd);close(fd);frame.reset();uint32_t colors[]={0xff123456,0x80123456,0x00123456,0xffabcdef};producer->post(2,2,colors,4);_exit(0);}
 int status=0;assert(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);
 assert(frame->read(w,h,revision,pixels)&&w==2&&h==2&&revision==1&&pixels[0]==0xff123456&&pixels[1]==0x80123456);
 auto other=shiraz::SharedFrame::import(fd);frame.reset();close(fd);uint32_t color=0xff998877;other->post(1,1,&color,1);
 assert(other->read(w,h,revision,pixels)&&w==1&&h==1&&revision==2&&pixels[0]==color);
 bool rejected=false;try{other->post(8192,8192,&color,1);}catch(const std::invalid_argument&){rejected=true;}assert(rejected);
 int invalid=open("/dev/null",O_RDONLY);rejected=false;try{auto bad=shiraz::SharedFrame::import(invalid);}catch(const std::invalid_argument&){rejected=true;}close(invalid);assert(rejected);
 puts("SHARED_FRAME_PROCESS_PIXELS_LIFETIME_OK");
}
