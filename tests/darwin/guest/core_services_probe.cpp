// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/core_services.hpp"
#include "shiraz/darwin/guest/io.hpp"
#include "shiraz/darwin/guest/fortify.hpp"
#include <filesystem>
#include <iostream>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
using namespace shiraz::guest;
static void require(bool value,const char *message){if(!value)throw std::runtime_error(message);}
int main(int argc,char **argv){try{
 require(argc==2,"usage: core-services-probe fixture-directory");Loader *active=nullptr;
 auto validate=[&](const void *p){return active && active->contains_code(p);};
 auto core=file_personality();Providers providers;
 for(auto &pair:core.modules()){auto module=pair.second;if(pair.first=="libc.so"){module.exports.push_back(fixed_export("getenv","LIBC",&std::getenv));auto extra=core_service_exports(validate);module.exports.insert(module.exports.end(),extra.begin(),extra.end());}providers.add(std::move(module));}
 Loader loader({std::filesystem::canonical(argv[1])},std::move(providers));active=&loader;
 auto *module=loader.open("libcore-services.so");errno=ELOOP;
 auto result=invoke(reinterpret_cast<void *>(loader.symbol(module,"run_core_services").address),{}).result_integers[0];
 if(result)throw std::runtime_error("guest core service failure line "+std::to_string(result));require(errno==ELOOP,"host errno preserved");
 for(int i=0;i<4;++i){auto child=fork();require(child>=0,"fork fatal test");if(!child){invoke(reinterpret_cast<void *>(loader.symbol(module,"fail_core_service").address),{Argument::integer(i)});_exit(0);}int status=0;require(waitpid(child,&status,0)==child && WIFSIGNALED(status) && WTERMSIG(status)==SIGABRT,"checked string rejects bounds");}
 std::cout<<"M5_CORE_SERVICES_PASS bsearch_guest_callback=1 checked_strings=3 case_ascii=1 uname=1 walltime=1 rlimit=1 random=real sleep=1 fatal_rejections=4\n";return 0;
}catch(const std::exception &e){std::cerr<<"core-services-probe: "<<e.what()<<'\n';return 1;}}
