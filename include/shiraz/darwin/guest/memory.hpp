// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/darwin/guest/providers.hpp"
#include "shiraz/darwin/guest/maps_stream.hpp"
#include <functional>
#include <string>
#include "shiraz/darwin/guest/thread.hpp"
namespace shiraz::guest {
class Lifecycle;
class SignalRuntime;
class CleanupRuntime;
// Describes only mappings owned by this provider, not the whole process.
// captured_path is historical: rename/unlink resolution belongs to the maps provider.
struct MemoryMapRecord {
    uintptr_t start, end;
    int protection, flags; // Native Darwin values.
    uint64_t owner, file_offset, device, inode;
    std::string captured_path;
};
using MemorySnapshot = std::function<std::vector<MemoryMapRecord>()>;
std::vector<ProviderExport> memory_exports(MemorySnapshot* snapshot = nullptr);
Providers native_personality(CodeValidator validate = {}, std::shared_ptr<Lifecycle> lifecycle = {},
                             std::shared_ptr<SignalRuntime> signals = {},
                             std::shared_ptr<CleanupRuntime> cleanup = {},
                             MemorySnapshot* memory_snapshot = nullptr,
                             MapsStream::Factory maps = {});
} // namespace shiraz::guest
