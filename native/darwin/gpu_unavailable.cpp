// SPDX-License-Identifier: GPL-3.0-only
// macOS placeholder for the Skia GPU canvas until Skia Ganesh-Metal lands.
// Presentation falls back to the software canvas on the Metal renderer.
#include "shiraz/gpu.hpp"
#include <iostream>
namespace shiraz {
bool gpu_initialize(void *(*)(const char *)) {
    std::cerr << "SHIRAZ_CANVAS gpu=unavailable reason=skia-metal-pending\n";
    return false;
}
void gpu_shutdown() {}
void gpu_prepare() {}
uint64_t gpu_revision() { return 0; }
bool gpu_present(int, int, const uint32_t *, int, int) { return false; }
void gpu_software_frame() {}
} // namespace shiraz
