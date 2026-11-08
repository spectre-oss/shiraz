// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/memory.hpp"
#include <filesystem>
#include <iostream>
#include <pthread.h>
using namespace shiraz::guest;
// Detached threads can finish host key-destructor epilogues after notifying the
// guest. Match NativeBridge's process-resident image lifetime rather than racing
// an ELF unload against their final return instructions.
static Loader *resident = nullptr;
static Loader *other_resident = nullptr;
static void require(bool value, const char *message) {
  if (!value) throw std::runtime_error(message);
}
static Argument pointer(const void *value) {
  return Argument::integer(reinterpret_cast<uintptr_t>(value), ArgumentType::Int64);
}
int main(int argc, char **argv) {
  try {
    require(argc == 2, "usage: thread-probe fixture-directory");
    resident = new Loader({std::filesystem::canonical(argv[1])}, native_personality(
        [](const void *code) { return resident && resident->contains_code(code); }));
    auto &loader = *resident;
    auto *module = loader.open("libthread.so");
    require(loader.image_count() == 1, "thread APIs use explicit providers");
    auto call = [&](const char *name, std::vector<Argument> arguments) {
      return invoke(reinterpret_cast<void *>(loader.symbol(module, name).address), arguments)
          .result_integers[0];
    };
    auto self = loader.symbol(module, "pthread_self");
    require(self.abi == "native/pthread-self-noargs" &&
                self.address == reinterpret_cast<uintptr_t>(&::pthread_self),
            "pthread_self binds the identical native no-argument ABI directly");
    errno = ELOOP;
    require(call("self_identity", {}) == reinterpret_cast<uintptr_t>(pthread_self()) &&
                errno == ELOOP && *guest_errno_location() == 731,
            "pthread_self returns the native thread identity and preserves both errnos");
    other_resident = new Loader({std::filesystem::canonical(argv[1])}, native_personality(
        [](const void *code) { return other_resident && other_resident->contains_code(code); }));
    auto *other_module = other_resident->open("libthread.so");
    auto other_call = [&](const char *name, std::vector<Argument> arguments) {
      return invoke(reinterpret_cast<void *>(other_resident->symbol(other_module, name).address), arguments)
          .result_integers[0];
    };
    int namespace_values[2] = {71, 73};
    require(int(call("key_namespace_create", {})) == 0 &&
                int(other_call("key_namespace_create", {})) == 0 &&
                !call("key_namespace_get", {}) && !other_call("key_namespace_get", {}),
            "independent loader key registries begin empty on the same thread");
    require(int(call("key_namespace_set", {pointer(&namespace_values[0])})) == 0 &&
                int(other_call("key_namespace_set", {pointer(&namespace_values[1])})) == 0 &&
                call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[0]) &&
                other_call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[1]),
            "guest key values include registry identity rather than only a numeric key");
    require(int(call("key_namespace_delete", {})) == 0 &&
                other_call("key_namespace_get", {}) == reinterpret_cast<uintptr_t>(&namespace_values[1]) &&
                int(other_call("key_namespace_delete", {})) == 0,
            "deleting a key in one guest registry cannot clear another registry value");
    errno = ELOOP;
    int result = int(call("run_threads", {}));
    if (result) throw std::runtime_error("guest thread failed at line " + std::to_string(result));
    require(errno == ELOOP, "pthread providers preserve host errno");
    struct { uint32_t before = 0xabcdef01, key = 0, after = 0x12345678; } guarded;
    require(int(call("pthread_key_create", {pointer(&guarded.key), pointer(nullptr)})) == 0 &&
                guarded.before == 0xabcdef01 && guarded.after == 0x12345678,
            "key creation writes only four bytes");
    int value = 73;
    uint64_t dirty_key = 0xabababab00000000ULL | guarded.key;
    require(int(call("pthread_setspecific", {Argument::integer(dirty_key, ArgumentType::Int64), pointer(&value)})) == 0 &&
                call("pthread_getspecific", {Argument::integer(dirty_key, ArgumentType::Int64)}) ==
                    reinterpret_cast<uintptr_t>(&value),
            "key arguments ignore unspecified upper 32 bits");
    require(int(call("pthread_key_delete", {Argument::integer(guarded.key)})) == 0,
            "key cleanup");
    result = int(call("run_key_lifecycle", {}));
    if (result) throw std::runtime_error("guest key lifecycle failed at line " + std::to_string(result));
    require(errno == ELOOP, "key cleanup providers preserve host errno");
    std::cout << "M5_THREAD_PASS create=6 join=4 identity=1 errno=1 attr_guards=1 "
                 "key_guards=1 key_width=1 destructors=12 detached=2 callback_validation=1 "
                 "key_iteration_threads=4 key_iterations=4 key_clear_before_callback=1 "
                 "key_reset_final_pass=1 key_create_in_destructor=1 key_delete_suppresses=1 "
                 "key_delete_in_destructor=1 key_namespaces=2 self_noargs=1 self_errnos=1\n";
  } catch (const std::exception &error) {
    std::cerr << "FAIL " << error.what() << '\n';
    return 1;
  }
}
