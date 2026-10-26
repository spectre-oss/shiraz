// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>
namespace shiraz {
bool gpu_initialize(void *(*getProc)(const char *));
void gpu_shutdown();
void gpu_prepare();
uint64_t gpu_revision();
bool gpu_present(int width, int height, const uint32_t *pixels, int sourceWidth,
                 int sourceHeight);
void gpu_software_frame();
} // namespace shiraz
