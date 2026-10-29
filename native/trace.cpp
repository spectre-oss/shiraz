// SPDX-License-Identifier: GPL-3.0-only
#include <cstdint>
// No Android system tracing session exists in Shiraz. Match the disabled NDK
// tracing contract; calls neither record events nor dereference caller labels.
extern "C" {
bool ATrace_isEnabled() { return false; }
void ATrace_beginSection(const char*) {}
void ATrace_endSection() {}
void ATrace_beginAsyncSection(const char*, std::int32_t) {}
void ATrace_endAsyncSection(const char*, std::int32_t) {}
void ATrace_setCounter(const char*, std::int64_t) {}
}
