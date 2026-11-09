// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <mutex>
#include <thread>
#include <unistd.h>
using namespace shiraz::guest;
static volatile sig_atomic_t finalizations = 0;
static void require(bool value, const char *message) { if (!value) { std::cerr << message << '\n'; std::_Exit(1); } }
static Argument pointer(const void *p) { return Argument::integer(reinterpret_cast<uintptr_t>(p), ArgumentType::Int64); }
struct Barrier { std::mutex mutex; std::condition_variable changed; bool ready=false, entered=false, release=false; int calls=0; };
static void observe(void *opaque) {
  auto &barrier = *static_cast<Barrier *>(opaque);
  std::unique_lock<std::mutex> lock(barrier.mutex);
  ++barrier.calls; barrier.entered=true; barrier.changed.notify_all();
  require(barrier.changed.wait_for(lock, std::chrono::seconds(10), [&]{return barrier.release;}), "callback timeout");
}
static void rejected_unmap(int) {
  static constexpr char message[] = "M5_TLS_RETENTION_UNMAP_PASS before_fini=1\n";
  if (finalizations) std::_Exit(1);
  (void)::write(STDOUT_FILENO, message, sizeof(message)-1);
  std::_Exit(0);
}
int main(int argc, char **argv) {
  require(argc==3, "usage: tls-retention-probe fixture-directory mode");
  const std::string mode=argv[2];
  require(mode=="joined"||mode=="pending"||mode=="active"||mode=="validation"||mode=="unmap"||mode=="same-thread", "unknown mode");
  Loader *current=nullptr;
  Barrier barrier;
  std::atomic<bool> arm_validation{false};
  std::atomic<unsigned> validations{0};
  auto validate=[&](const void *p) {
    ++validations;
    if (arm_validation) {
      std::unique_lock<std::mutex> lock(barrier.mutex);
      barrier.entered=true; barrier.changed.notify_all();
      require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"validation timeout");
    }
    return current && current->contains_code(p);
  };
  auto lifecycle=std::make_shared<Lifecycle>(validate);
  auto providers=native_personality(validate,lifecycle);
  uintptr_t registration=0;
  for (const auto &entry:providers.modules().at("libc.so").exports)
    if(entry.name=="__cxa_thread_atexit_impl") registration=entry.address;
  require(registration!=0,"registration export missing");
  auto loader=std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]},std::move(providers));
  current=loader.get(); lifecycle->attach(*loader);
  auto *module=loader->open("libtls-retention.so");
  auto call=[&](const char *name,const std::vector<Argument> &args) {
    return invoke(reinterpret_cast<void *>(loader->symbol(module,name).address),args).result_integers[0];
  };
  call("retention_bind_finalizer",{pointer(const_cast<sig_atomic_t *>(&finalizations))});
  auto callback=fixed_export("fixture_observe","LIBC",&observe);
  require(int(invoke(reinterpret_cast<void *>(registration),{pointer(reinterpret_cast<void *>(uintptr_t(0x1000))),pointer(nullptr),pointer(nullptr)}).result_integers[0])==-1
      && !lifecycle->retained_thread_callbacks(),"failed validation leaked retention");
  arm_validation=mode=="validation";
  if(mode=="same-thread") {
    require(call("retention_register",{pointer(reinterpret_cast<void *>(callback.address)),pointer(&barrier)})==0,"same-thread registration failed");
    require(lifecycle->retained_thread_callbacks()==1,"same-thread retention missing");
    bool refused=false;
    try { lifecycle->shutdown(); } catch(const ElfError &error) {
      refused=std::string(error.what())=="guest thread-local callbacks still retain namespace at shutdown";
    }
    require(refused&&finalizations==0,"same-thread shutdown must refuse without waiting");
    std::cout<<"M5_TLS_RETENTION_REFUSAL_PASS mode=same-thread tokens=1 before_fini=1 no_wait=1\n"<<std::flush;
    std::_Exit(0);
  }
  std::thread worker([&] {
    require(call("retention_register",{pointer(reinterpret_cast<void *>(callback.address)),pointer(&barrier)})==0,"registration failed");
    std::unique_lock<std::mutex> lock(barrier.mutex);
    barrier.ready=true;barrier.changed.notify_all();
    if(mode!="active") require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"worker timeout");
  });
  {
    std::unique_lock<std::mutex> lock(barrier.mutex);
    require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return mode=="active"||mode=="validation"?barrier.entered:barrier.ready;}),"arrival timeout");
  }
  require(lifecycle->retained_thread_callbacks()==1,"queued/executing/validating callback lost retention");
  if(mode=="joined") {
    {std::lock_guard<std::mutex> lock(barrier.mutex);barrier.release=true;barrier.changed.notify_all();}
    worker.join();
    require(barrier.calls==1&&!lifecycle->retained_thread_callbacks(),"callback return did not release retention");
    lifecycle->shutdown();
    require(finalizations==1&&lifecycle->closed()&&!lifecycle->retain_thread_callback(),"clean finalization/admission failure");
    auto before=validations.load();
    require(int(invoke(reinterpret_cast<void *>(registration),{pointer(reinterpret_cast<void *>(uintptr_t(0x1000))),pointer(nullptr),pointer(nullptr)}).result_integers[0])==-1
        && before==validations,"closed registration touched validator");
    loader.reset();
    std::cout<<"M5_TLS_RETENTION_JOINED_PASS callback=1 tokens=0 fini=1 closed_validator_calls=0\n";
    return 0;
  }
  if(mode=="unmap") { std::signal(SIGABRT,rejected_unmap);loader.reset();require(false,"unmap accepted outstanding callback"); }
  bool refused=false;
  try { lifecycle->shutdown(); } catch(const ElfError &error) {
    refused=std::string(error.what())=="guest thread-local callbacks still retain namespace at shutdown";
  }
  require(refused&&finalizations==0&&loader->image_count()==1,"shutdown did not refuse before finalizers/unmap");
  std::cout<<"M5_TLS_RETENTION_REFUSAL_PASS mode="<<mode<<" tokens=1 before_fini=1 before_unmap=1\n"<<std::flush;
  std::_Exit(0); // Fresh fixture process: intentionally live worker owns callback.
}
