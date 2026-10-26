// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <cerrno>
#include <dlfcn.h>
#include <fcntl.h>
#include <spawn.h>
#include <string>
#include <vector>
#include <sys/wait.h>
#include <unistd.h>
extern char** environ;
namespace {
void error(JNIEnv* env,const char* message){if(env->ExceptionCheck())return;jclass type=env->FindClass("java/io/IOException");if(type){env->ThrowNew(type,message);env->DeleteLocalRef(type);}}
struct Actions {posix_spawn_file_actions_t actions;bool initialized=false;~Actions(){if(initialized)posix_spawn_file_actions_destroy(&actions);}};
struct Descriptor {int fd=-1;~Descriptor(){if(fd>=0)close(fd);}};
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeIpc_spawnNative(JNIEnv* env,jclass,jobjectArray command,jint socket) try {
    if(!command){error(env,"Missing child command");return -1;}
    jsize count=env->GetArrayLength(command);if(count<1||count>256){error(env,"Child command limit");return -1;}
    std::vector<std::string> arguments;arguments.reserve(count);size_t total=0;
    for(jsize i=0;i<count;i++){
        auto value=static_cast<jbyteArray>(env->GetObjectArrayElement(command,i));
        if(!value){error(env,"Missing child argument");return -1;}
        jsize length=env->GetArrayLength(value);total+=length;
        if(total>1024*1024){env->DeleteLocalRef(value);error(env,"Child command byte limit");return -1;}
        arguments.emplace_back(length,'\0');if(length)env->GetByteArrayRegion(value,0,length,reinterpret_cast<jbyte*>(&arguments.back()[0]));env->DeleteLocalRef(value);
        if(env->ExceptionCheck())return -1;
        if(arguments.back().find('\0')!=std::string::npos){error(env,"NUL in child argument");return -1;}
    }
    if(arguments[0].empty()||arguments[0][0]!='/'){error(env,"Child executable must be absolute");return -1;}
    // Resolve optionally: older hosts fail explicitly rather than inheriting
    // arbitrary application descriptors into a service process.
    using CloseFrom=int(*)(posix_spawn_file_actions_t*,int);
    auto closeFrom=reinterpret_cast<CloseFrom>(dlsym(RTLD_DEFAULT,"posix_spawn_file_actions_addclosefrom_np"));
    if(!closeFrom){error(env,"Safe child descriptor isolation unavailable on this host");return -1;}
    Descriptor source;source.fd=fcntl(socket,F_DUPFD_CLOEXEC,4);
    if(source.fd<0){error(env,"Cannot duplicate child IPC endpoint");return -1;}
    Actions actions;
    if(posix_spawn_file_actions_init(&actions.actions)){error(env,"Cannot allocate child file actions");return -1;}actions.initialized=true;
    if(posix_spawn_file_actions_adddup2(&actions.actions,source.fd,3)||closeFrom(&actions.actions,4)){error(env,"Cannot isolate child descriptors");return -1;}
    std::vector<char*> argv;argv.reserve(arguments.size()+1);for(auto& arg:arguments)argv.push_back(&arg[0]);argv.push_back(nullptr);
    pid_t pid;int status=posix_spawn(&pid,arguments[0].c_str(),&actions.actions,nullptr,argv.data(),environ);
    if(status){error(env,"Cannot execute child process");return -1;}
    return pid;
} catch(const std::bad_alloc&){error(env,"Cannot allocate child process command");return -1;}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeIpc_waitFor(JNIEnv* env,jclass,jint pid){
    if(pid<=0){error(env,"Invalid child PID");return -1;}
    int status;pid_t result;do{result=waitpid(pid,&status,0);}while(result<0&&errno==EINTR);
    if(result!=pid){error(env,"Cannot reap child process");return -1;}
    return WIFEXITED(status)?WEXITSTATUS(status):WIFSIGNALED(status)?128+WTERMSIG(status):-1;
}
