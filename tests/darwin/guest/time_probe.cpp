// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <cstdio>
#include <thread>
using namespace shiraz::guest;
int main(int argc,char **argv){try{
 if(argc!=2)throw std::runtime_error("usage: time-probe fixture-directory");
 Loader loader({argv[1]},native_personality());auto *module=loader.open("libtime-fixture.so");
 auto *entry=reinterpret_cast<void *>(loader.symbol(module,"run_time").address);errno=EBUSY;
 auto result=invoke(entry,{}).result_integers[0];if(result)throw std::runtime_error("time fixture line "+std::to_string(result));
 if(errno!=EBUSY)throw std::runtime_error("host errno changed");
 std::atomic<bool> passed{true};std::vector<std::thread> threads;
 for(int i=0;i<4;++i)threads.emplace_back([&]{for(int n=0;n<32;++n)if(invoke(entry,{}).result_integers[0])passed=false;});
 for(auto &thread:threads)thread.join();if(!passed)throw std::runtime_error("threaded date conversion");
 std::puts("M5_TIME_PASS tm_translated=1 leap_date=1 local_roundtrip=1 formatting=1 nanosleep=1 threads=4 host_errno_preserved=1");
 }catch(const std::exception&e){std::fprintf(stderr,"time-probe: %s\n",e.what());return 1;}}
