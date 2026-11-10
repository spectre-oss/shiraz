// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <mutex>
#include <thread>
using namespace shiraz::guest;
static volatile sig_atomic_t finalizations = 0;
static void require(bool value, const char *message) {
  if (!value) { std::cerr << message << '\n'; std::_Exit(1); }
}
static Argument pointer(const void *p) {
  return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64);
}
struct Barrier {
  std::mutex mutex;
  std::condition_variable changed;
  bool entered=false, release=false;
  unsigned calls=0;
};
static void observe(void *opaque) {
  auto &barrier=*static_cast<Barrier *>(opaque);
  std::unique_lock<std::mutex> lock(barrier.mutex);
  ++barrier.calls; barrier.entered=true; barrier.changed.notify_all();
  require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"entry callback timeout");
}
int main(int argc, char **argv) {
  require(argc==3,"usage: thread-retention-probe fixture-directory mode");
  const std::string mode=argv[2];
  require(mode=="joined"||mode=="active"||mode=="validation","unknown mode");
  Loader *current=nullptr;
  Barrier barrier;
  barrier.release=mode=="joined";
  std::atomic<bool> block_validation{false};
  auto validate=[&](const void *p) {
    if (block_validation.load()) {
      std::unique_lock<std::mutex> lock(barrier.mutex);
      barrier.entered=true; barrier.changed.notify_all();
      require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"entry validation timeout");
    }
    return current&&current->contains_code(p);
  };
  auto lifecycle=std::make_shared<Lifecycle>(validate);
  auto loader=std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]},native_personality(validate,lifecycle));
  current=loader.get(); lifecycle->attach(*loader);
  auto *module=loader->open("libthread-retention.so");
  auto call=[&](const char *name,const std::vector<Argument> &args=std::vector<Argument>{}) {
    return invoke(reinterpret_cast<void *>(loader->symbol(module,name).address),args).result_integers[0];
  };
  call("thread_retention_bind_finalizer",{pointer(const_cast<sig_atomic_t *>(&finalizations))});
  require(call("thread_retention_invalid")!=0&&!lifecycle->retained_thread_callbacks(),"invalid entry leaked retention");
  auto callback=fixed_export("fixture_thread_observe","LIBC",&observe);
  auto start=[&] {
    require(call("thread_retention_start",{pointer(reinterpret_cast<void *>(callback.address)),pointer(&barrier)})==0,"start guest thread");
  };
  block_validation=mode=="validation";
  if (mode=="validation") {
    // This foreign caller is stopped inside pthread_create validation, before
    // publishing any child. The admission token must already pin the image.
    std::thread caller(start);
    {
      std::unique_lock<std::mutex> lock(barrier.mutex);
      require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.entered;}),"validation arrival timeout");
    }
    require(lifecycle->retained_thread_callbacks()==1,"validator missing namespace retention");
    bool refused=false;
    try {lifecycle->shutdown();} catch(const ElfError &error) {
      refused=std::string(error.what())=="guest thread-local callbacks still retain namespace at shutdown";
    }
    require(refused&&finalizations==0&&loader->image_count()==1,"validation shutdown did not refuse before FINI/unmap");
    std::cout<<"M5_THREAD_RETENTION_REFUSAL_PASS mode=validation token=1 before_fini=1 before_publication=1\n"<<std::flush;
    std::_Exit(0);
  }
  start();
  if (mode=="active") {
    {
      std::unique_lock<std::mutex> lock(barrier.mutex);
      require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.entered;}),"entry arrival timeout");
    }
    require(lifecycle->retained_thread_callbacks()==1,"active entry missing namespace retention");
    bool refused=false;
    try {lifecycle->shutdown();} catch(const ElfError &error) {
      refused=std::string(error.what())=="guest thread-local callbacks still retain namespace at shutdown";
    }
    require(refused&&finalizations==0&&loader->image_count()==1,"active shutdown did not refuse before FINI/unmap");
    std::cout<<"M5_THREAD_RETENTION_REFUSAL_PASS mode=active token=1 before_fini=1 before_unmap=1\n"<<std::flush;
    std::_Exit(0);
  }
  require(call("thread_retention_join",{pointer(&barrier)})==0,"join guest thread/result");
  require(barrier.calls==1&&!lifecycle->retained_thread_callbacks(),"joined entry leaked retention");
  lifecycle->shutdown(); loader.reset(); current=nullptr;
  require(finalizations==1,"joined entry did not permit FINI");
  std::cout<<"M5_THREAD_RETENTION_RELEASE_PASS mode=joined calls=1 tokens=0 invalid_entry=1 fini=1\n";
}
