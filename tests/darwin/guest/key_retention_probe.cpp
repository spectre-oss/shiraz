// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/lifecycle.hpp"
#include "shiraz/darwin/guest/memory.hpp"
#include "shiraz/darwin/guest/tls.hpp"
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
  bool ready=false, entered=false, release=false, block=false;
  unsigned calls=0;
};
static void observe(void *opaque) {
  auto &barrier=*static_cast<Barrier *>(opaque);
  std::unique_lock<std::mutex> lock(barrier.mutex);
  ++barrier.calls; barrier.entered=true; barrier.changed.notify_all();
  if (barrier.block)
    require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"callback timeout");
}
static void release(Barrier &barrier) {
  std::lock_guard<std::mutex> lock(barrier.mutex);
  barrier.release=true; barrier.changed.notify_all();
}
int main(int argc, char **argv) {
  require(argc==3,"usage: key-retention-probe fixture-directory mode");
  const std::string mode=argv[2];
  require(mode=="joined"||mode=="pending"||mode=="same-thread"||mode=="clear"||mode=="delete"||mode=="active-delete"||mode=="active-delete-release"||mode=="exhaustion"||mode=="multi","unknown mode");
  const bool active=mode=="active-delete"||mode=="active-delete-release";
  Loader *current=nullptr;
  auto validate=[&](const void *p){return current&&current->contains_code(p);};
  auto lifecycle=std::make_shared<Lifecycle>(validate);
  auto loader=std::make_unique<Loader>(std::vector<std::filesystem::path>{argv[1]},native_personality(validate,lifecycle));
  current=loader.get(); lifecycle->attach(*loader);
  auto *module=loader->open("libkey-retention.so");
  auto call=[&](const char *name,const std::vector<Argument> &args=std::vector<Argument>{}) {
    return invoke(reinterpret_cast<void *>(loader->symbol(module,name).address),args).result_integers[0];
  };
  Barrier barrier, replaced;
  barrier.block=active;
  auto callback=fixed_export("fixture_key_observe","LIBC",&observe);
  require(call("key_retention_create",{pointer(reinterpret_cast<void *>(callback.address)),pointer(const_cast<sig_atomic_t *>(&finalizations)),Argument::integer(mode=="exhaustion",ArgumentType::Int32)})==0,"key creation failed");
  require(!lifecycle->retained_thread_callbacks(),"empty key pins namespace");
  auto set=[&](void *value){require(call("key_retention_set",{pointer(value)})==0,"set key failed");};
  auto refuse=[&] {
    bool refused=false;
    try {lifecycle->shutdown();} catch(const ElfError &error) {
      refused=std::string(error.what())=="guest thread-local callbacks still retain namespace at shutdown";
    }
    require(refused&&finalizations==0&&loader->image_count()==1,"shutdown must refuse before FINI/unmap");
  };
  if (mode=="multi") {
    Barrier first, second;
    auto queued=[&](Barrier &value) {
      set(&value);
      {
        std::unique_lock<std::mutex> lock(value.mutex);
        value.ready=true; value.changed.notify_all();
        require(value.changed.wait_for(lock,std::chrono::seconds(10),[&]{return value.release;}),"multi worker timeout");
      }
      set(nullptr);
    };
    auto await_ready=[&](Barrier &value) {
      std::unique_lock<std::mutex> lock(value.mutex);
      require(value.changed.wait_for(lock,std::chrono::seconds(10),[&]{return value.ready;}),"multi arrival timeout");
    };
    // Publish serially, then inspect only after both acquisition candidates
    // have gone out of scope. Both foreign slots share one key token epoch.
    std::thread one([&]{queued(first);});
    await_ready(first);
    std::thread two([&]{queued(second);});
    await_ready(second);
    require(lifecycle->retained_thread_callbacks()==1,"two values did not share one retention epoch");
    release(first); one.join();
    require(lifecycle->retained_thread_callbacks()==1,"first clear unpinned second foreign value");
    release(second); two.join();
    require(!lifecycle->retained_thread_callbacks(),"last foreign clear retained namespace");
    require(!first.calls&&!second.calls,"cleared foreign values invoked destructors");
    lifecycle->shutdown(); loader.reset(); current=nullptr;
    require(finalizations==1,"all foreign clears did not permit finalization");
    std::cout<<"M5_KEY_RETENTION_RELEASE_PASS mode=multi callbacks=0 tokens=0 fini=1 shared_epoch=1\n";
    return 0;
  }
  if (mode=="same-thread") {
    set(&barrier);
    require(lifecycle->retained_thread_callbacks()==1,"same-thread retention missing");
    refuse();
    std::cout<<"M5_KEY_RETENTION_REFUSAL_PASS mode=same-thread before_fini=1 no_wait=1\n"<<std::flush;
    std::_Exit(0);
  }
  std::thread worker([&] {
    // Replacement must preserve one queued value and select the latest payload.
    set(&replaced); set(&barrier);
    require(lifecycle->retained_thread_callbacks()==1,"replacement changed key retention epoch");
    if (mode=="clear") {
      set(nullptr);
      require(!lifecycle->retained_thread_callbacks(),"clearing last value retained namespace");
    }
    std::unique_lock<std::mutex> lock(barrier.mutex);
    barrier.ready=true; barrier.changed.notify_all();
    if (!active)
      require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return barrier.release;}),"worker timeout");
  });
  {
    std::unique_lock<std::mutex> lock(barrier.mutex);
    require(barrier.changed.wait_for(lock,std::chrono::seconds(10),[&]{return active?barrier.entered:barrier.ready;}),"arrival timeout");
  }
  if (mode=="pending") {
    require(lifecycle->retained_thread_callbacks()==1,"foreign queued value not retained");
    refuse();
    std::cout<<"M5_KEY_RETENTION_REFUSAL_PASS mode=pending before_fini=1 before_unmap=1\n"<<std::flush;
    std::_Exit(0);
  }
  if (mode=="delete"||active) {
    require(call("key_retention_delete")==0,"key deletion failed");
    if (active)
      require(lifecycle->retained_thread_callbacks()==1,"deletion unpinned selected callback");
    if (mode=="active-delete") {
      refuse();
      std::cout<<"M5_KEY_RETENTION_REFUSAL_PASS mode=active-delete selected_token=1 before_fini=1\n"<<std::flush;
      std::_Exit(0);
    }
    if (!active)
      require(!lifecycle->retained_thread_callbacks(),"deletion retained stale foreign value");
  }
  if (mode=="clear"||mode=="delete") {
    // It is safe to finalize before a foreign thread drops a cleared/deleted
    // value: it must never re-enter the unmapped fixture during native teardown.
    lifecycle->shutdown(); loader.reset(); current=nullptr;
    require(finalizations==1,"empty/deleted key blocked finalization");
    release(barrier); worker.join();
    require(!barrier.calls&&!replaced.calls,"cleared/deleted value invoked destructor");
  } else {
    release(barrier); worker.join();
    require(!replaced.calls,"replacement selected stale value");
    require(barrier.calls==(mode=="exhaustion"?4u:1u),"destructor pass count differs");
    require(!lifecycle->retained_thread_callbacks(),"completed/discarded key value leaked retention");
    lifecycle->shutdown(); loader.reset(); current=nullptr;
    require(finalizations==1,"joined callback did not permit finalization");
  }
  std::cout<<"M5_KEY_RETENTION_RELEASE_PASS mode="<<mode<<" callbacks="<<barrier.calls<<" tokens=0 fini=1\n";
}
