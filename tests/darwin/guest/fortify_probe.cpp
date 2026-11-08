// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include <cstdio>
#include <csignal>
#include <sys/wait.h>
#include <unistd.h>
using namespace shiraz::guest;
static Argument ptr(const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); }
static Argument number(uint64_t v) { return Argument::integer(v, ArgumentType::Int64); }
static void require(bool v,const char *message) { if (!v) throw std::runtime_error(message); }
int main(int argc,char **argv) {
  try {
    require(argc==2,"usage: fortify-probe fixture-directory");
    Loader loader({argv[1]},native_personality()); auto *module=loader.open("libfortify.so");
    require(!invoke(reinterpret_cast<void *>(loader.symbol(module,"run_fortify").address),{}).result_integers[0],"real guest fortified memory and formatting");
    auto *libc=loader.link("libc.so"); char out[16]{};
    auto rejects=[&](const char *name,std::vector<Argument> args) {
      auto entry=reinterpret_cast<void *>(loader.symbol(libc,name).address);
      pid_t child=fork();require(child>=0,"fork abort test");
      if (!child) { std::signal(SIGABRT,+[](int){_exit(97);}); invoke(entry,args);_exit(1); }
      int status=0;require(waitpid(child,&status,0)==child && WIFEXITED(status) && WEXITSTATUS(status)==97,"bounds violation must abort before returning success");
    };
    rejects("__memcpy_chk",{ptr(out),ptr("abcdef"),number(6),number(5)});
    rejects("__memmove_chk",{ptr(out),ptr("abcdef"),number(6),number(5)});
    rejects("__memset_chk",{ptr(out),number(0),number(6),number(5)});
    rejects("__strlen_chk",{ptr("abcd"),number(4)});
    rejects("__strncpy_chk",{ptr(out),ptr("x"),number(6),number(5)});
    rejects("__strncpy_chk2",{ptr(out),ptr("abcdef"),number(6),number(16),number(5)});
    rejects("__strcpy_chk",{ptr(out),ptr("abcdef"),number(6)});
    rejects("__snprintf_chk",{ptr(out),number(17),number(0),number(16),ptr("x")});
    rejects("__sprintf_chk",{ptr(out),number(0),number(3),ptr("%s"),ptr("abc")});
    std::puts("M5_FORTIFY_PASS guest_memory=1 guest_varargs=1 guest_va_list=1 truncate=1 abort_cases=9");
  } catch (const std::exception &e) {std::fprintf(stderr,"fortify-probe: %s\n",e.what());return 1;}
}
