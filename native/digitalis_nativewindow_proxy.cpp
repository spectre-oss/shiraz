// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/hardware_buffer.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/proxy_loader/proxy_library_builder.h"
namespace berberis {
#define ENTRY(name) {#name, GetTrampolineFunc<decltype(name)>(), reinterpret_cast<void*>(name)}
static const KnownTrampoline functions[] = {
 ENTRY(AHardwareBuffer_acquire), ENTRY(AHardwareBuffer_allocate),
 ENTRY(AHardwareBuffer_describe), ENTRY(AHardwareBuffer_isSupported),
 ENTRY(AHardwareBuffer_lock), ENTRY(AHardwareBuffer_recvHandleFromUnixSocket),
 ENTRY(AHardwareBuffer_release), ENTRY(AHardwareBuffer_sendHandleToUnixSocket),
 ENTRY(AHardwareBuffer_unlock), ENTRY(ANativeWindow_acquire),
 ENTRY(ANativeWindow_getFormat), ENTRY(ANativeWindow_getHeight),
 ENTRY(ANativeWindow_getWidth), ENTRY(ANativeWindow_lock),
 ENTRY(ANativeWindow_release), ENTRY(ANativeWindow_setBuffersGeometry),
 ENTRY(ANativeWindow_unlockAndPost)
};
#undef ENTRY
extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {
 builder->Build("libandroid.so", sizeof(functions)/sizeof(functions[0]), functions, 0, nullptr);
}
}
