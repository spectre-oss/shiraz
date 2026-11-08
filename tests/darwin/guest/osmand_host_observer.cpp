// SPDX-License-Identifier: GPL-3.0-only
// Read-only observation of descriptors for this one explicit test asset.
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>
extern "C" JNIEXPORT jint JNICALL Java_OsmAndNativeLoad_nativeOpenCount(JNIEnv *env,jclass,jstring path){
  const char *name=env->GetStringUTFChars(path,nullptr);if(!name)return -1;
  struct stat wanted{};int result=::stat(name,&wanted);env->ReleaseStringUTFChars(path,name);if(result)return -1;
  int count=0;for(int fd=0;fd<4096;++fd){struct stat current{};if(!::fstat(fd,&current) && wanted.st_dev==current.st_dev && wanted.st_ino==current.st_ino)++count;}return count;
}
