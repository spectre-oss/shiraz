// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
int main(void){
 const char *path="/sys/fs/selinux/enforce";struct stat64 info;
 assert(stat64(path,&info)==0 && S_ISREG(info.st_mode));assert(lstat64(path,&info)==0);
 setenv("SHIRAZ_GUEST_METADATA_EPOCH","1234567890",1);
 assert(stat64(path,&info)==0 && info.st_ctim.tv_sec==1234567890 && info.st_ctim.tv_nsec==0);
 assert(lstat64(path,&info)==0 && info.st_mtim.tv_sec==1234567890);
 setenv("SHIRAZ_GUEST_METADATA_EPOCH","invalid",1);
 assert(stat64(path,&info)==0 && info.st_ctim.tv_sec!=1234567890);
 unsetenv("SHIRAZ_GUEST_METADATA_EPOCH");
 assert(access(path,R_OK)==0);assert(access(path,W_OK)==-1 && errno==EROFS);
 int fd=open64(path,O_RDONLY);assert(fd>=0);char c=0;assert(read(fd,&c,1)==1 && c=='1');close(fd);
 assert(open64(path,O_WRONLY)==-1 && errno==EROFS);
 assert(open64(path,O_RDONLY|O_TRUNC)==-1 && errno==EROFS);
 assert(stat64("/sys/fs/selinux/enforce-other",&info)==-1);
}
