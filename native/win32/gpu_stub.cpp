// SPDX-License-Identifier: GPL-3.0-only
// Software-canvas GPU stub used by Windows SDL2 presentation tests built
// without Skia Ganesh archives.
#include "shiraz/gpu.hpp"

namespace shiraz {

bool gpu_initialize(void* (*)(const char*)) {
    return false;
}

void gpu_shutdown() {}

void gpu_prepare() {}

uint64_t gpu_revision() {
    return 0;
}

bool gpu_present(int, int, const uint32_t*, int, int) {
    return false;
}

void gpu_software_frame() {}

} // namespace shiraz
