// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "shiraz/audio.hpp"
namespace shiraz::audio {
std::shared_ptr<Clip> darwinDecode(const std::vector<std::uint8_t>& bytes);
}
