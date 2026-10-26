// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/native_window.h"
#include <functional>
// Internal producer hook. Runs under the window lock; must only enqueue/copy
// the frame, never invoke app callbacks or call back into the window.
using ShirazFrameSink = std::function<void(const uint32_t*, int, int, bool)>;
void shiraz_window_set_frame_sink(ANativeWindow*, ShirazFrameSink);
