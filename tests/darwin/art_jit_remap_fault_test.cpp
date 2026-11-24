// SPDX-License-Identifier: GPL-3.0-only
// Live Apple Silicon verification for Darwin ART MemMap::ReplaceWith (mach_vm_remap),
// MemMap::MapAnonymousJit (MAP_JIT + pthread_jit_write_protect_np + sys_icache_invalidate),
// and Darwin ARM64 FaultManager ucontext_t register/signal decoding.
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <string>
#include <sys/mman.h>
#include <sys/ucontext.h>
#include <unistd.h>

#include "base/globals.h"
#include "base/mem_map.h"
#include "base/utils.h"
#include "fault_handler.h"

namespace {

void test_mach_vm_remap() {
    static_assert(art::MemMap::kCanReplaceMapping, "Darwin ARM64 must enable kCanReplaceMapping");
    const size_t size = static_cast<size_t>(art::kPageSize) * 2;
    std::string err;

    // 1. General address-space atomic replacement via mach_vm_remap.
    art::MemMap src =
        art::MemMap::MapAnonymous("remap-src", size, PROT_READ | PROT_WRITE, false, &err);
    assert(src.IsValid() && err.empty());
    art::MemMap dst =
        art::MemMap::MapAnonymous("remap-dst", size, PROT_READ | PROT_WRITE, false, &err);
    assert(dst.IsValid() && err.empty());
    std::strcpy(reinterpret_cast<char*>(src.Begin()), "SHIRAZ_ART_MACH_VM_REMAP");
    std::strcpy(reinterpret_cast<char*>(dst.Begin()), "STALE_DST");
    assert(dst.ReplaceWith(&src, &err));
    assert(!src.IsValid());
    assert(dst.IsValid());
    assert(std::strcmp(reinterpret_cast<const char*>(dst.Begin()), "SHIRAZ_ART_MACH_VM_REMAP") ==
           0);

    // 2. Inside the 4 GiB heap-reference window (0x10000000000).
    art::MemMap wsrc =
        art::MemMap::MapAnonymous("win-src", size, PROT_READ | PROT_WRITE, true, &err);
    assert(wsrc.IsValid() && err.empty());
    art::MemMap wdst =
        art::MemMap::MapAnonymous("win-dst", size, PROT_READ | PROT_WRITE, true, &err);
    assert(wdst.IsValid() && err.empty());
    std::strcpy(reinterpret_cast<char*>(wsrc.Begin()), "SHIRAZ_WIN_REMAP");
    assert(wdst.ReplaceWith(&wsrc, &err));
    assert(!wsrc.IsValid());
    assert(std::strcmp(reinterpret_cast<const char*>(wdst.Begin()), "SHIRAZ_WIN_REMAP") == 0);
}

void test_map_jit_and_icache() {
    const size_t data_cap = static_cast<size_t>(art::kPageSize) * 4;
    const size_t exec_cap = static_cast<size_t>(art::kPageSize) * 4;
    std::string err;

    art::MemMap data = art::MemMap::MapAnonymous("jit-data", data_cap + exec_cap,
                                                 PROT_READ | PROT_WRITE, false, &err);
    assert(data.IsValid() && err.empty());
    uint8_t* divider = data.Begin() + data_cap;
    data.SetSize(data_cap);
    art::MemMap exec = art::MemMap::MapAnonymousJit("jit-exec", divider, exec_cap, &err);
    assert(exec.IsValid() && err.empty());
    const intptr_t span =
        reinterpret_cast<intptr_t>(exec.End()) - reinterpret_cast<intptr_t>(data.Begin());
    assert(exec.Begin() >= data.End());
    assert(span > 0 && static_cast<uint64_t>(span) <= (UINT64_C(1) << 31));

    pthread_jit_write_protect_np(0);
    auto* insns = reinterpret_cast<uint32_t*>(exec.Begin());
    insns[0] = 0x0b010000u; // add w0, w0, w1
    insns[1] = 0xd65f03c0u; // ret
    assert(art::FlushCpuCaches(exec.Begin(), exec.Begin() + 8));
    pthread_jit_write_protect_np(1);

    using AddFn = int (*)(int, int);
    AddFn fn = reinterpret_cast<AddFn>(exec.Begin());
    assert(fn(19, 23) == 42);
}

void test_darwin_fault_ucontext() {
    alignas(16) uintptr_t fake_stack[16] = {};
    auto* fake_method_sp = reinterpret_cast<art::ArtMethod*>(0x12345678u);
    auto* fake_method_x0 = reinterpret_cast<art::ArtMethod*>(0x87654321u);
    fake_stack[8] = reinterpret_cast<uintptr_t>(fake_method_sp);

    _STRUCT_MCONTEXT64 mc{};
    ucontext_t uc{};
    uc.uc_mcontext = &mc;
    mc.__ss.__sp = reinterpret_cast<uintptr_t>(&fake_stack[8]);
    mc.__ss.__pc = 0x100004000ull;
    mc.__ss.__x[0] = reinterpret_cast<uintptr_t>(fake_method_x0);
    mc.__es.__far = 0x10ull;

    siginfo_t si{};
    si.si_signo = SIGSEGV;
    si.si_addr = reinterpret_cast<void*>(0x10ull);

    art::ArtMethod* out_method = nullptr;
    uintptr_t out_return_pc = 0;
    uintptr_t out_sp = 0;
    art::fault_manager.GetMethodAndReturnPcAndSp(&si, &uc, &out_method, &out_return_pc, &out_sp);
    assert(out_sp == reinterpret_cast<uintptr_t>(&fake_stack[8]));
    assert(out_return_pc == 0x100004004ull);
    assert(out_method == fake_method_sp);

    // NullPointerHandler::Action pushes return_pc onto sp, stores fault addr in lr, sets pc.
    art::NullPointerHandler npe(&art::fault_manager);
    assert(npe.Action(SIGSEGV, &si, &uc));
    assert(mc.__ss.__sp == reinterpret_cast<uintptr_t>(&fake_stack[7]));
    assert(fake_stack[7] == 0x100004004ull);
    assert(mc.__ss.__lr == 0x10ull);
    assert(mc.__ss.__pc != 0x100004000ull);
    Dl_info npe_info{};
    assert(dladdr(reinterpret_cast<void*>(mc.__ss.__pc), &npe_info) != 0);
    assert(npe_info.dli_fname != nullptr &&
           std::strstr(npe_info.dli_fname, "libart.so") != nullptr);
    const uintptr_t npe_trampoline = mc.__ss.__pc;

    // StackOverflowHandler::Action matches sp - 16 KiB guard offset and redirects pc.
    mc.__ss.__sp = reinterpret_cast<uintptr_t>(&fake_stack[8]);
    mc.__ss.__pc = 0x100005000ull;
    mc.__es.__far = mc.__ss.__sp - 16384u;
    si.si_addr = reinterpret_cast<void*>(mc.__es.__far);
    art::fault_manager.GetMethodAndReturnPcAndSp(&si, &uc, &out_method, &out_return_pc, &out_sp);
    assert(out_method == fake_method_x0);
    art::StackOverflowHandler sof(&art::fault_manager);
    assert(sof.Action(SIGBUS, &si, &uc));
    assert(mc.__ss.__pc != 0x100005000ull && mc.__ss.__pc != npe_trampoline);
    Dl_info sof_info{};
    assert(dladdr(reinterpret_cast<void*>(mc.__ss.__pc), &sof_info) != 0);
    assert(sof_info.dli_fname != nullptr &&
           std::strstr(sof_info.dli_fname, "libart.so") != nullptr);
}

} // namespace

int main() {
    art::MemMap::Init();
    test_mach_vm_remap();
    test_map_jit_and_icache();
    test_darwin_fault_ucontext();
    std::printf("DARWIN_ART_JIT_REMAP_FAULT_PASS\n");
    return 0;
}
